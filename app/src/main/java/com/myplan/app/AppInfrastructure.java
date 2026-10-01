package com.myplan.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * بنية تحتية محلية: Installation ID، Feature Flags، Entitlements، Logs.
 * كل الميزات المتقدمة OFF افتراضيًا. لا يعتمد على Backend.
 * Central Developer Control = خارج التطبيق مستقبلًا.
 */
public final class AppInfrastructure {
    private static final String PREFS = "myplan_infra_v1";
    private static final String KEY_INSTALL = "installation_id";
    private static final String KEY_USER = "user_id";
    private static final String KEY_DEV_UNLOCKED = "dev_unlocked"; // legacy; unlock is session-only now
    private static final String KEY_PREMIUM_TEST = "premium_test_mode";
    /** Master switch: when false, all user-facing Premium UI is hidden (contract stays). */
    private static final String KEY_PREMIUM_USER_VISIBLE = "premium_user_visible";
    private static final String KEY_ENTITLEMENTS = "entitlements_json";
    /** Session-only Developer Mode unlock — never persisted across process death. */
    private static volatile boolean sessionDevUnlocked = false;
    private static final String KEY_LOGS = "app_logs_json";
    private static final String KEY_LAST_LAUNCH = "last_launch";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final int MAX_LOGS = 200;

    private AppInfrastructure() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void onLaunch(Context c) {
        getInstallationId(c);
        long now = System.currentTimeMillis();
        SharedPreferences p = sp(c);
        if (!p.contains(KEY_FIRST_LAUNCH)) {
            p.edit().putLong(KEY_FIRST_LAUNCH, now).apply();
        }
        p.edit().putLong(KEY_LAST_LAUNCH, now).apply();
        log(c, "App", "Launch");
    }

    public static String getInstallationId(Context c) {
        SharedPreferences p = sp(c);
        String id = p.getString(KEY_INSTALL, null);
        if (id == null || id.isEmpty()) {
            String raw = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.US);
            id = "MP-" + raw.substring(0, 8);
            p.edit().putString(KEY_INSTALL, id).apply();
        }
        return id;
    }

    /** User ID مستقبلي — فارغ حتى تفعيل الحسابات */
    public static String getUserId(Context c) {
        return sp(c).getString(KEY_USER, "");
    }

    public static void setUserId(Context c, String userId) {
        sp(c).edit().putString(KEY_USER, userId == null ? "" : userId).apply();
    }

    /** Session-only; always locked after process restart or leaving Developer Center. */
    public static boolean isDeveloperUnlocked(Context c) {
        return sessionDevUnlocked;
    }

    public static void setDeveloperUnlocked(Context c, boolean v) {
        sessionDevUnlocked = v;
        // Clear any legacy persisted unlock so old installs do not stay unlocked
        try { sp(c).edit().putBoolean(KEY_DEV_UNLOCKED, false).apply(); } catch (Exception ignored) {}
    }

    public static final String SOURCE_DEVELOPER_TEST = "developer_test";
    public static final String SOURCE_PURCHASE = "purchase";
    public static final String SOURCE_SERVER = "server";
    public static final String SOURCE_LOCAL = "local";
    private static final String TEST_ENTITLEMENT_ID = "local-developer-test-premium";

    /**
     * Local Premium Test Mode — device-only, persists across restarts,
     * independent of Developer Center lock. Backed by a real local Entitlement.
     */
    public static boolean isPremiumTestMode(Context c) {
        if (sp(c).getBoolean(KEY_PREMIUM_TEST, false)) return true;
        Entitlement e = getActiveFullPremium(c);
        return e != null && SOURCE_DEVELOPER_TEST.equals(e.source);
    }

    public static void setPremiumTestMode(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_PREMIUM_TEST, on).apply();
        List<Entitlement> list = listEntitlements(c);
        // أزل أي entitlement تجريبي سابق
        List<Entitlement> kept = new ArrayList<>();
        for (Entitlement e : list) {
            if (e == null) continue;
            if (SOURCE_DEVELOPER_TEST.equals(e.source)) continue;
            if (TEST_ENTITLEMENT_ID.equals(e.id)) continue;
            kept.add(e);
        }
        if (on) {
            Entitlement te = new Entitlement();
            te.id = TEST_ENTITLEMENT_ID;
            te.type = "full";
            te.featureKey = "";
            te.startMs = System.currentTimeMillis();
            te.expiryMs = 0;
            te.permanent = true;
            te.source = SOURCE_DEVELOPER_TEST;
            te.userId = getUserId(c);
            te.installationId = getInstallationId(c);
            te.note = "Local Premium Test Mode — device only";
            kept.add(te);
        }
        saveEntitlements(c, kept);
        // فعّل أعلام الميزات المنفذة محليًا مع Test Premium؛ أطفئها عند OFF
        Flags f = getFlags(c);
        if (on) {
            f.premium = true;
            f.ai = true;
            f.advancedAnalytics = true;
            f.goals = true;
            f.studyIntelligence = true;
            f.reports = true;
            f.voice = true;
            f.experimental = true;
        } else {
            f.premium = false;
            f.ai = false;
            f.advancedAnalytics = false;
            f.goals = false;
            f.studyIntelligence = false;
            f.reports = false;
            f.voice = false;
            f.experimental = false;
        }
        setFlags(c, f);
        log(c, "Premium", on ? "Test entitlement ON" : "Test entitlement OFF");
    }

    public static long getLastLaunch(Context c) {
        return sp(c).getLong(KEY_LAST_LAUNCH, 0);
    }

    public static long getFirstLaunch(Context c) {
        return sp(c).getLong(KEY_FIRST_LAUNCH, 0);
    }

    // ── Feature Flags (local defaults all OFF for premium/future) ──
    public static final class Flags {
        public boolean premium;
        public boolean ai;
        public boolean cloud;
        public boolean accounts;
        public boolean ads;
        public boolean advancedAnalytics;
        public boolean advancedPlanning;
        public boolean goals;
        public boolean studyIntelligence;
        public boolean reports;
        public boolean connectedAccounts;
        public boolean voice;
        public boolean widgets;
        public boolean integrations;
        public boolean experimental;
        public boolean gamificationAdvanced;
        public boolean history;
        public boolean archive;
        public boolean smartSearch;
        public boolean syllabus;
        public boolean smartNotifications;
        public boolean multiProfile;

        public static Flags defaults() {
            return new Flags(); // all false
        }

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("premium", premium);
            o.put("ai", ai);
            o.put("cloud", cloud);
            o.put("accounts", accounts);
            o.put("ads", ads);
            o.put("advancedAnalytics", advancedAnalytics);
            o.put("advancedPlanning", advancedPlanning);
            o.put("goals", goals);
            o.put("studyIntelligence", studyIntelligence);
            o.put("reports", reports);
            o.put("connectedAccounts", connectedAccounts);
            o.put("voice", voice);
            o.put("widgets", widgets);
            o.put("integrations", integrations);
            o.put("experimental", experimental);
            o.put("gamificationAdvanced", gamificationAdvanced);
            o.put("history", history);
            o.put("archive", archive);
            o.put("smartSearch", smartSearch);
            o.put("syllabus", syllabus);
            o.put("smartNotifications", smartNotifications);
            o.put("multiProfile", multiProfile);
            return o;
        }

        public static Flags fromJson(JSONObject o) {
            Flags f = defaults();
            if (o == null) return f;
            f.premium = o.optBoolean("premium", false);
            f.ai = o.optBoolean("ai", false);
            f.cloud = o.optBoolean("cloud", false);
            f.accounts = o.optBoolean("accounts", false);
            f.ads = o.optBoolean("ads", false);
            f.advancedAnalytics = o.optBoolean("advancedAnalytics", false);
            f.advancedPlanning = o.optBoolean("advancedPlanning", false);
            f.goals = o.optBoolean("goals", false);
            f.studyIntelligence = o.optBoolean("studyIntelligence", false);
            f.reports = o.optBoolean("reports", false);
            f.connectedAccounts = o.optBoolean("connectedAccounts", false);
            f.voice = o.optBoolean("voice", false);
            f.widgets = o.optBoolean("widgets", false);
            f.integrations = o.optBoolean("integrations", false);
            f.experimental = o.optBoolean("experimental", false);
            f.gamificationAdvanced = o.optBoolean("gamificationAdvanced", false);
            f.history = o.optBoolean("history", false);
            f.archive = o.optBoolean("archive", false);
            f.smartSearch = o.optBoolean("smartSearch", false);
            f.syllabus = o.optBoolean("syllabus", false);
            f.smartNotifications = o.optBoolean("smartNotifications", false);
            f.multiProfile = o.optBoolean("multiProfile", false);
            return f;
        }
    }

    public static Flags getFlags(Context c) {
        try {
            String j = sp(c).getString("flags_json", null);
            if (j == null) return Flags.defaults();
            return Flags.fromJson(new JSONObject(j));
        } catch (Exception e) {
            return Flags.defaults();
        }
    }

    public static void setFlags(Context c, Flags f) {
        try {
            sp(c).edit().putString("flags_json", f.toJson().toString()).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Current full Premium entitlement if any (test / purchase / server / local grant).
     * Does not require Feature Flags — entitlement is independent of flag state.
     */
    public static Entitlement getCurrentPremiumEntitlement(Context c) {
        Entitlement e = getActiveFullPremium(c);
        if (e != null && e.isActiveNow()) return e;
        // ترحيل: لو العلم القديم ON ومافيش entitlement بعد، أنشئ entitlement تجريبي مرة واحدة
        if (sp(c).getBoolean(KEY_PREMIUM_TEST, false)) {
            setPremiumTestMode(c, true);
            e = getActiveFullPremium(c);
            if (e != null && e.isActiveNow()) return e;
        }
        return null;
    }

    /** Premium active iff local entitlement OR remote premium_grants cache. */
    public static boolean isPremiumActive(Context c) {
        if (getCurrentPremiumEntitlement(c) != null) return true;
        try {
            return c.getSharedPreferences("myplan_remote_control_v1", Context.MODE_PRIVATE)
                    .getBoolean("premium_active", false);
        } catch (Exception e) {
            return false;
        }
    }

    /** User-facing Premium visibility. Independent of entitlement / test mode. Default false (HIDDEN). */
    public static boolean isPremiumUserVisible(Context c) {
        return sp(c).getBoolean(KEY_PREMIUM_USER_VISIBLE, false);
    }

    public static void setPremiumUserVisible(Context c, boolean visible) {
        sp(c).edit().putBoolean(KEY_PREMIUM_USER_VISIBLE, visible).apply();
    }

    /**
     * Feature access = Feature Flag enabled AND (implemented gate).
     * Premium entitlement alone does NOT force every flag ON.
     * Premium-gated features also require isPremiumActive() or a feature entitlement.
     */
    public static boolean isFeatureEnabled(Context c, String featureKey) {
        if (featureKey == null || featureKey.isEmpty()) return false;
        String key = featureKey.trim();
        // remove_ads: entitlement أو grant مستقل — لا يرتبط بـ Premium كامل تلقائيًا
        if ("remove_ads".equalsIgnoreCase(key)) {
            return hasFeatureEntitlement(c, "remove_ads")
                    || remoteFeatureGranted(c, "remove_ads");
        }
        Flags f = getFlags(c);
        boolean prem = isPremiumActive(c);
        boolean remoteFeat = remoteFeatureGranted(c, key);
        switch (key) {
            case "ai": return f.ai && (prem || hasFeatureEntitlement(c, "ai") || remoteFeat);
            case "advancedAnalytics": return f.advancedAnalytics && (prem || remoteFeat);
            case "goals": return f.goals && (prem || remoteFeat);
            case "studyIntelligence": return f.studyIntelligence && (prem || remoteFeat);
            case "reports": return f.reports && (prem || remoteFeat);
            case "cloud": return f.cloud || remoteFeat;
            case "ads":
                // إظهار الإعلانات فقط إن كانت مفعّلة ولم يُمنح remove_ads
                if (shouldRemoveAds(c)) return false;
                return f.ads;
            case "accounts": return f.accounts || remoteFeat;
            case "advancedPlanning": return f.advancedPlanning && (prem || remoteFeat);
            case "connectedAccounts": return f.connectedAccounts || remoteFeat;
            case "voice": return f.voice && (prem || remoteFeat);
            case "widgets": return f.widgets || remoteFeat;
            case "integrations": return f.integrations || remoteFeat;
            case "experimental": return f.experimental && (prem || remoteFeat);
            case "banner_ads":
            case "interstitial_ads":
            case "rewarded_ads":
                if (shouldRemoveAds(c)) return false;
                return remoteDynamicFlag(c, key, true);
            default:
                // مفاتيح غير معروفة: من remote flags أو grants فقط — آمن
                return remoteDynamicFlag(c, key, false) || remoteFeat || hasFeatureEntitlement(c, key);
        }
    }

    /** هل يجب إخفاء الإعلانات؟ */
    public static boolean shouldRemoveAds(Context c) {
        return hasFeatureEntitlement(c, "remove_ads") || remoteFeatureGranted(c, "remove_ads");
    }

    /** feature_keys من آخر sync لـ premium_grants */
    public static boolean remoteFeatureGranted(Context c, String featureKey) {
        try {
            String raw = c.getSharedPreferences("myplan_remote_control_v1", Context.MODE_PRIVATE)
                    .getString("premium_feature_keys", "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                if (featureKey.equalsIgnoreCase(a.optString(i, ""))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean remoteDynamicFlag(Context c, String key, boolean def) {
        try {
            String snap = c.getSharedPreferences("myplan_remote_control_v1", Context.MODE_PRIVATE)
                    .getString("snapshot_json", "{}");
            JSONObject o = new JSONObject(snap);
            if (o.has(key)) return o.optBoolean(key, def);
            JSONObject flags = o.optJSONObject("feature_flags");
            if (flags != null && flags.has(key)) return flags.optBoolean(key, def);
        } catch (Exception ignored) {}
        return def;
    }

    // ── Entitlements ──
    public static final class Entitlement {
        public String id;
        public String type; // full | feature
        public String featureKey; // for feature type
        public long startMs;
        public long expiryMs; // 0 = permanent
        public boolean permanent;
        public String source; // local | grant | purchase | remote
        public String userId;
        public String installationId;
        public String note;

        public boolean isActiveNow() {
            long now = System.currentTimeMillis();
            if (startMs > 0 && now < startMs) return false;
            if (permanent || expiryMs == 0) return true;
            return now <= expiryMs;
        }

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("type", type);
            o.put("featureKey", featureKey == null ? "" : featureKey);
            o.put("startMs", startMs);
            o.put("expiryMs", expiryMs);
            o.put("permanent", permanent);
            o.put("source", source == null ? "" : source);
            o.put("userId", userId == null ? "" : userId);
            o.put("installationId", installationId == null ? "" : installationId);
            o.put("note", note == null ? "" : note);
            return o;
        }

        public static Entitlement fromJson(JSONObject o) {
            Entitlement e = new Entitlement();
            e.id = o.optString("id", UUID.randomUUID().toString());
            e.type = o.optString("type", "full");
            e.featureKey = o.optString("featureKey", "");
            e.startMs = o.optLong("startMs", 0);
            e.expiryMs = o.optLong("expiryMs", 0);
            e.permanent = o.optBoolean("permanent", false);
            e.source = o.optString("source", "local");
            e.userId = o.optString("userId", "");
            e.installationId = o.optString("installationId", "");
            e.note = o.optString("note", "");
            return e;
        }
    }

    public static List<Entitlement> listEntitlements(Context c) {
        List<Entitlement> out = new ArrayList<>();
        try {
            String j = sp(c).getString(KEY_ENTITLEMENTS, "[]");
            JSONArray a = new JSONArray(j);
            for (int i = 0; i < a.length(); i++) out.add(Entitlement.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveEntitlements(Context c, List<Entitlement> list) {
        try {
            JSONArray a = new JSONArray();
            for (Entitlement e : list) a.put(e.toJson());
            sp(c).edit().putString(KEY_ENTITLEMENTS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static Entitlement getActiveFullPremium(Context c) {
        for (Entitlement e : listEntitlements(c)) {
            if ("full".equals(e.type) && e.isActiveNow()) return e;
        }
        return null;
    }

    public static boolean hasFeatureEntitlement(Context c, String featureKey) {
        for (Entitlement e : listEntitlements(c)) {
            if ("feature".equals(e.type) && featureKey.equals(e.featureKey) && e.isActiveNow()) return true;
        }
        return false;
    }

    // ── Logs ──
    public static void log(Context c, String category, String message) {
        try {
            String j = sp(c).getString(KEY_LOGS, "[]");
            JSONArray a = new JSONArray(j);
            JSONObject row = new JSONObject();
            row.put("ts", System.currentTimeMillis());
            row.put("cat", category == null ? "App" : category);
            row.put("msg", message == null ? "" : message);
            a.put(row);
            while (a.length() > MAX_LOGS) a.remove(0);
            sp(c).edit().putString(KEY_LOGS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

        public static void clearLogs(Context c) {
        try { sp(c).edit().putString(KEY_LOGS, "[]").apply(); } catch (Exception ignored) {}
    }

    public static List<String> getLogLines(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(KEY_LOGS, "[]"));
            SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject o = a.getJSONObject(i);
                out.add(fmt.format(new Date(o.optLong("ts"))) + " [" + o.optString("cat") + "] " + o.optString("msg"));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** كلمة مرور Developer Mode المحلية فقط */
    public static boolean checkLocalDevPassword(String input) {
        // Release builds never contain or accept a Developer password.
        if (!BuildConfig.DEBUG || input == null || input.trim().isEmpty()) return false;
        String expected = BuildConfig.LOCAL_DEV_PASS_SHA256;
        return expected != null && !expected.isEmpty()
                && expected.equalsIgnoreCase(sha256(input.trim()));
    }

    public static String buildDiagnosticReport(Context c, Planner planner, int versionCode, String versionName) {
        StringBuilder sb = new StringBuilder();
        sb.append("My Plan Diagnostic Report\n");
        sb.append("========================\n");
        sb.append("version=").append(versionName).append(" (").append(versionCode).append(")\n");
        sb.append("installId=").append(getInstallationId(c)).append("\n");
        sb.append("userId=").append(getUserId(c).isEmpty() ? "(none)" : getUserId(c)).append("\n");
        sb.append("android=").append(Build.VERSION.RELEASE).append(" sdk=").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("device=").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("devUnlocked=").append(isDeveloperUnlocked(c)).append("\n");
        Flags f = getFlags(c);
        try { sb.append("flags=").append(f.toJson().toString()).append("\n"); } catch (Exception ignored) {}
        sb.append("premiumActive=").append(isPremiumActive(c)).append("\n");
        sb.append("premiumTestMode=").append(isPremiumTestMode(c)).append("\n");
        Entitlement cur = getCurrentPremiumEntitlement(c);
        sb.append("premiumSource=").append(cur != null ? cur.source : "none").append("\n");
        if (planner != null) {
            sb.append("tasks=").append(planner.tasks.size()).append("\n");
            sb.append("sessions=").append(planner.sessions.size()).append("\n");
            sb.append("exams=").append(planner.exams.size()).append("\n");
            sb.append("subjects=").append(planner.subjects.size()).append("\n");
        }
        sb.append("dbSchema=myplan_v3 (SharedPreferences JSON)\n");
        sb.append("migration=compatible_local\n");
        sb.append("lastLaunch=").append(getLastLaunch(c)).append("\n");
        sb.append("--- recent logs ---\n");
        int n = 0;
        for (String line : getLogLines(c)) {
            sb.append(line).append("\n");
            if (++n >= 40) break;
        }
        return sb.toString();
    }

    // ── Future backend abstractions (local stubs) ──
    public interface RemoteConfigRepository {
        Flags loadFlags();
        void saveFlagsLocal(Flags f);
    }

    public static final class LocalRemoteConfigRepository implements RemoteConfigRepository {
        private final Context c;
        public LocalRemoteConfigRepository(Context c) { this.c = c.getApplicationContext(); }
        @Override public Flags loadFlags() { return getFlags(c); }
        @Override public void saveFlagsLocal(Flags f) { setFlags(c, f); }
    }

    public interface EntitlementRepository {
        List<Entitlement> list();
        void saveAll(List<Entitlement> list);
    }

    public static final class LocalEntitlementRepository implements EntitlementRepository {
        private final Context c;
        public LocalEntitlementRepository(Context c) { this.c = c.getApplicationContext(); }
        @Override public List<Entitlement> list() { return listEntitlements(c); }
        @Override public void saveAll(List<Entitlement> list) { saveEntitlements(c, list); }
    }

    public interface AdProvider {
        boolean isEnabled();
        void loadSlot(String slotId);
    }

    public static final class NoOpAdProvider implements AdProvider {
        @Override public boolean isEnabled() { return false; }
        @Override public void loadSlot(String slotId) { /* OFF */ }
    }

    public interface AuthRepository {
        boolean isLoggedIn();
        String currentUserId();
    }

    public static final class OfflineAuthRepository implements AuthRepository {
        private final Context c;
        public OfflineAuthRepository(Context c) { this.c = c.getApplicationContext(); }
        @Override public boolean isLoggedIn() { return AccountAuth.isLoggedIn(c); }
        @Override public String currentUserId() {
            if (AccountAuth.isLoggedIn(c)) {
                String sid = AccountAuth.getSessionUserId(c);
                if (sid != null && !sid.isEmpty()) return sid;
            }
            return getUserId(c);
        }
    }

    public interface SyncRepository {
        boolean isCloudEnabled();
        long lastSyncMs();
    }

    public static final class OfflineSyncRepository implements SyncRepository {
        @Override public boolean isCloudEnabled() { return false; }
        @Override public long lastSyncMs() { return 0; }
    }
}
