package com.myplan.app.remote;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.BuildConfig;
import com.myplan.app.api.ApiResult;
import com.myplan.app.supabase.RemoteControlCache;
import com.myplan.app.supabase.SupabaseConfig;
import com.myplan.app.supabase.SupabaseHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * My Plan Remote Control Layer.
 *
 * This class is intentionally isolated from Planner and UI construction.
 * It only reads/writes remote-control state and stores a small local snapshot.
 *
 * Remote changes affect code already shipped in the APK:
 * maintenance, ban/block, premium entitlement, flags, config, announcements
 * and version policy. New code still requires an APK update.
 */
public final class RemoteControlService {
    private RemoteControlService() {}

    public static final String PREFS = "myplan_remote_v2";
    private static final String KEY_LAST_SYNC = "last_sync_ms";

    public static final class Result {
        public boolean configured;
        public boolean networkOk;
        public boolean accountBanned;
        public boolean deviceBlocked;
        public boolean maintenance;
        public boolean forceUpdate;
        public String maintenanceMessage = "";
        public String updateMessage = "";
        public String updateUrl = "";
        public int minimumVersionCode;
        public boolean premiumActive;
        public JSONObject snapshot;
    }

    public static void syncAsync(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try { sync(app); } catch (Throwable ignored) {}
        }, "myplan-remote-v2").start();
    }

    public static Result sync(Context context) {
        Context app = context.getApplicationContext();
        Result out = new Result();
        out.configured = SupabaseConfig.isConfigured(app);
        if (!out.configured) return out;

        SupabaseHttp http = new SupabaseHttp(app);
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("fetched_at", System.currentTimeMillis());

            AccountAuth.Account account = AccountAuth.getCurrentAccount(app);
            String localUserId = AccountAuth.getSessionUserId(app);
            if (localUserId == null || localUserId.isEmpty()) localUserId = AppInfrastructure.getUserId(app);
            String remoteUserId = remoteUserId(localUserId);

            // Registration is best-effort. The control plane must never touch planner data.
            if (account != null) upsertProfile(http, account, remoteUserId);
            upsertDevice(http, app, remoteUserId);

            JSONObject profile = first(http.get("/rest/v1/app_users?id=eq." + enc(remoteUserId)
                    + "&select=id,status,email,display_name&limit=1"));
            if (profile != null) {
                String status = profile.optBoolean("banned", false) || "banned".equalsIgnoreCase(profile.optString("status", ""))
                        || "disabled".equalsIgnoreCase(profile.optString("status", ""));
                
            }

            JSONObject device = first(http.get("/rest/v1/device_controls?installation_id=eq."
                    + enc(AppInfrastructure.getInstallationId(app))
                    + "&select=blocked&limit=1"));
            if (device != null) {
                out.deviceBlocked = device.optBoolean("blocked", false);
            }

            readMaintenance(http, snapshot);
            readFeatureFlags(http, snapshot);
            readRemoteConfig(http, snapshot);
            readVersions(http, snapshot);
            readPremium(http, snapshot, remoteUserId);
            readAnnouncements(http, snapshot);

            out.maintenance = snapshot.optBoolean("maintenance_enabled", false);
            out.maintenanceMessage = snapshot.optString("maintenance_message",
                    "التطبيق في وضع الصيانة. يمكنك المحاولة لاحقًا.");
            out.forceUpdate = snapshot.optBoolean("force_update", false);
            out.minimumVersionCode = snapshot.optInt("minimum_version_code", 0);
            out.updateMessage = snapshot.optString("update_message", "يتوفر إصدار أحدث.");
            out.updateUrl = snapshot.optString("update_url", "");
            out.premiumActive = snapshot.optBoolean("premium_active", false);

            RemoteControlCache.saveSnapshot(app, snapshot);
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();

            out.snapshot = snapshot;
            out.networkOk = true;
            return out;
        } catch (Throwable ignored) {
            out.networkOk = false;
            return out;
        }
    }

    private static void upsertProfile(SupabaseHttp http, AccountAuth.Account a, String id) {
        try {
            JSONObject b = new JSONObject();
            b.put("id", id);
            b.put("email", a.email == null ? "" : a.email);
            b.put("display_name", a.displayName == null ? "" : a.displayName);
            b.put("last_seen_at", now());
            b.put("metadata", new JSONObject()
                    .put("local_user_id", a.userId == null ? "" : a.userId)
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("version_code", BuildConfig.VERSION_CODE)
                    .put("android_version", Build.VERSION.RELEASE));
            http.postPrefer("/rest/v1/app_users?on_conflict=id",
                    "[" + b + "]", "resolution=merge-duplicates,return=minimal");
        } catch (Throwable ignored) {}
    }

    private static void upsertDevice(SupabaseHttp http, Context app, String remoteUserId) {
        try {
            JSONObject b = new JSONObject();
            String installation = AppInfrastructure.getInstallationId(app);
            b.put("installation_id", installation);
            if (remoteUserId != null && !remoteUserId.isEmpty()) b.put("user_id", remoteUserId);
            b.put("platform", "android");
            b.put("manufacturer", Build.MANUFACTURER);
            b.put("model", Build.MODEL);
            b.put("android_version", Build.VERSION.RELEASE);
            b.put("app_version", BuildConfig.VERSION_NAME);
            b.put("version_code", BuildConfig.VERSION_CODE);
            b.put("last_seen_at", now());
            http.postPrefer("/rest/v1/devices?on_conflict=installation_id",
                    "[" + b + "]", "resolution=merge-duplicates,return=minimal");
        } catch (Throwable ignored) {}
    }

    private static void readMaintenance(SupabaseHttp http, JSONObject s) {
        try {
            ApiResult<String> r = http.get("/rest/v1/maintenance_controls?id=eq.true&select=*&limit=1");
            if (!r.isSuccess() || r.data == null) return;
            JSONArray a = new JSONArray(r.data);
            if (a.length() == 0) return;
            JSONObject row = a.getJSONObject(0);
            s.put("maintenance_enabled", row.optBoolean("enabled", false));
            s.put("maintenance_message", first(row, "message", "title", "maintenance_message",
                    "التطبيق في وضع الصيانة. يمكنك المحاولة لاحقًا."));
            if (row.has("starts_at")) s.put("maintenance_starts_at", row.opt("starts_at"));
            if (row.has("ends_at")) s.put("maintenance_ends_at", row.opt("ends_at"));
        } catch (Throwable ignored) {}
    }

    private static void readFeatureFlags(SupabaseHttp http, JSONObject s) {
        try {
            ApiResult<String> r = http.get("/rest/v1/feature_flags?select=*&limit=500");
            if (!r.isSuccess() || r.data == null) return;
            JSONArray a = new JSONArray(r.data);
            JSONObject flags = new JSONObject();
            for (int i = 0; i < a.length(); i++) {
                JSONObject row = a.getJSONObject(i);
                String key = first(row, "feature_key", "key", "flag_key", "name", "");
                if (key.isEmpty()) continue;
                flags.put(key, row.optBoolean("enabled", row.optBoolean("is_enabled", false)));
            }
            s.put("feature_flags", flags);
        } catch (Throwable ignored) {}
    }

    private static void readRemoteConfig(SupabaseHttp http, JSONObject s) {
        try {
            ApiResult<String> r = http.get("/rest/v1/remote_config?select=*&limit=500");
            if (!r.isSuccess() || r.data == null) return;
            JSONArray a = new JSONArray(r.data);
            JSONObject cfg = new JSONObject();
            for (int i = 0; i < a.length(); i++) {
                JSONObject row = a.getJSONObject(i);
                String key = first(row, "config_key", "key", "name", "");
                if (key.isEmpty()) continue;
                Object value = row.has("config_value") ? row.opt("config_value")
                        : row.opt("value");
                cfg.put(key, value == null ? JSONObject.NULL : value);
                if ("maintenance_enabled".equals(key)) s.put("maintenance_enabled", asBool(value));
                if ("maintenance_message".equals(key)) s.put("maintenance_message", String.valueOf(value));
                if ("update_url".equals(key)) s.put("update_url", String.valueOf(value));
            }
            s.put("remote_config", cfg);
        } catch (Throwable ignored) {}
    }

    private static void readVersions(SupabaseHttp http, JSONObject s) {
        try {
            ApiResult<String> r = http.get("/rest/v1/app_versions?select=*&order=version_code.desc&limit=1");
            if (!r.isSuccess() || r.data == null) return;
            JSONArray a = new JSONArray(r.data);
            if (a.length() == 0) return;
            JSONObject row = a.getJSONObject(0);
            int minimum = row.optInt("minimum_version_code",
                    row.optInt("min_version_code", 0));
            int latest = row.optInt("version_code", 0);
            s.put("latest_version_code", latest);
            s.put("minimum_version_code", minimum);
            s.put("force_update", row.optBoolean("force_update", false)
                    || (minimum > 0 && BuildConfig.VERSION_CODE < minimum));
            s.put("update_url", first(row, "update_url", "apk_url", "download_url", "url", ""));
            s.put("update_message", first(row, "release_notes", "update_message", "message",
                    "يجب تحديث التطبيق للمتابعة."));
        } catch (Throwable ignored) {}
    }

    private static void readPremium(SupabaseHttp http, JSONObject s, String userId) {
        try {
            if (userId == null || userId.isEmpty()) return;
            ApiResult<String> r = http.get("/rest/v1/premium_grants?select=*&user_id=eq."
                    + enc(userId) + "&limit=100");
            if (!r.isSuccess() || r.data == null) return;
            JSONArray a = new JSONArray(r.data);
            boolean active = false;
            JSONArray features = new JSONArray();
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject row = a.getJSONObject(i);
                if (!row.optBoolean("active", true)) continue;
                String status = row.optString("status", "active");
                if ("revoked".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)) continue;
                String exp = row.optString("expires_at", "");
                if (!exp.isEmpty()) {
                    try {
                        if (java.time.Instant.parse(exp).toEpochMilli() < now) continue;
                    } catch (Throwable ignored) {}
                }
                active = true;
                Object fk = row.opt("feature_keys");
                if (fk instanceof JSONArray) {
                    JSONArray fa = (JSONArray) fk;
                    for (int j = 0; j < fa.length(); j++) features.put(fa.optString(j, ""));
                }
            }
            s.put("premium_active", active || s.optBoolean("premium_active", false));
            s.put("premium_feature_keys", features);
        } catch (Throwable ignored) {}
    }

    private static void readAnnouncements(SupabaseHttp http, JSONObject s) {
        try {
            ApiResult<String> r = http.get("/rest/v1/remote_messages?select=*&is_active=eq.true"
                    + "&message_type=in.(announcement,maintenance,update,new_feature,warning,in_app)"
                    + "&order=priority.desc,created_at.desc&limit=50");
            if (!r.isSuccess() || r.data == null) return;
            s.put("announcements", new JSONArray(r.data));
        } catch (Throwable ignored) {}
    }

    private static JSONObject first(ApiResult<String> r) {
        if (r == null || !r.isSuccess() || r.data == null) return null;
        try {
            JSONArray a = new JSONArray(r.data);
            return a.length() == 0 ? null : a.getJSONObject(0);
        } catch (Throwable ignored) { return null; }
    }

    private static String first(JSONObject o, String a, String b, String c, String def) {
        String[] keys = new String[]{a,b,c};
        for (String k : keys) {
            if (k == null || !o.has(k) || o.isNull(k)) continue;
            String v = o.optString(k, "").trim();
            if (!v.isEmpty()) return v;
        }
        return def;
    }

    private static String first(JSONObject o, String a, String b, String c, String d, String def) {
        String[] keys = new String[]{a,b,c,d};
        for (String k : keys) {
            if (k == null || !o.has(k) || o.isNull(k)) continue;
            String v = o.optString(k, "").trim();
            if (!v.isEmpty()) return v;
        }
        return def;
    }

    private static boolean asBool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static String remoteUserId(String local) {
        if (local == null || local.isEmpty()) return "";
        return UUID.nameUUIDFromBytes(("myplan-user:" + local)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Throwable ignored) { return s == null ? "" : s; }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                .format(new Date());
    }
}
