package com.myplan.app.supabase;

import android.content.Context;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.api.ApiResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * طبقة واحدة معزولة لـ Supabase.
 * لا تُستدعى من Planner.
 *
 * الجداول المتوقعة (من Control Center) — تُقرأ إن وُجدت فقط:
 * remote_config, feature_flags, premium_grants, devices, messages, error_logs, ad_settings, app_versions
 */
public final class SupabaseRepository {
    private final Context app;
    private final SupabaseHttp http;

    public SupabaseRepository(Context c) {
        this.app = c.getApplicationContext();
        this.http = new SupabaseHttp(app);
    }

    public boolean isReady() {
        return SupabaseConfig.isConfigured(app);
    }

    /**
     * يجلب Remote Config + Flags + Update + Ads إن وُجدت الجداول.
     * يدمجها في snapshot محلي واحد.
     */
    public ApiResult<JSONObject> fetchAndCacheControlState() {
        if (!isReady()) return ApiResult.notConfigured();
        JSONObject snap = new JSONObject();
        try {
            snap.put("fetched_at", System.currentTimeMillis());

            // remote_config — أشكال شائعة: key/value أو config_key/config_value أو json
            mergeRemoteConfigTable(snap);
            mergeFeatureFlags(snap);
            mergeAppVersions(snap);
            mergeMaintenanceControls(snap);
            mergeAdSettings(snap);
            mergePremiumForCurrentUser(snap);
            mergeRemoteMessages(snap);

            RemoteControlCache.saveSnapshot(app, snap);
            return ApiResult.success(snap);
        } catch (Exception e) {
            return ApiResult.unknown("فشل دمج Remote Config");
        }
    }

    /**
     * schema الفعلي: config_key + config_value (jsonb).
     * أمثلة: maintenance/kill_switch/force_update → {enabled:bool}
     * app_update → {latest_version, minimum_version, force_update, apk_url, ...}
     * ad_settings → {ads_enabled, banner_enabled, ...}
     */
    private void mergeRemoteConfigTable(JSONObject snap) {
        ApiResult<String> r = http.get("/rest/v1/remote_config?select=*&limit=200");
        if (!r.isSuccess() || r.data == null) return;
        try {
            JSONArray arr = new JSONArray(r.data);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                String key = firstString(row, "config_key", "key", "name");
                if (key == null) continue;
                key = key.trim().toLowerCase();
                JSONObject cv = asObject(row.opt("config_value"));
                if (cv == null) cv = asObject(row.opt("value"));
                if (cv != null) {
                    if (cv.has("enabled")) {
                        snap.put(key, cv.optBoolean("enabled", false));
                    }
                    // app_update / ad_settings: انشر الحقول على الجذر
                    if ("app_update".equals(key) || key.contains("update")) {
                        if (cv.has("force_update")) snap.put("force_update", cv.optBoolean("force_update", false));
                        putIf(snap, "latest_version", cv.optString("latest_version", null));
                        putIf(snap, "minimum_version", cv.optString("minimum_version", null));
                        putIf(snap, "apk_url", cv.optString("apk_url", null));
                        putIf(snap, "update_message", cv.optString("update_message", null));
                        putIf(snap, "release_notes", cv.optString("release_notes", null));
                    }
                    if ("ad_settings".equals(key) || key.contains("ad")) {
                        if (cv.has("ads_enabled")) snap.put("ads", cv.optBoolean("ads_enabled", false));
                        if (cv.has("banner_enabled")) snap.put("banner_ads", cv.optBoolean("banner_enabled", false));
                        if (cv.has("interstitial_enabled")) snap.put("interstitial_ads", cv.optBoolean("interstitial_enabled", false));
                        if (cv.has("rewarded_enabled")) snap.put("rewarded_ads", cv.optBoolean("rewarded_enabled", false));
                    }
                    // انسخ كل مفاتيح الكائن تحت namespace المفتاح
                    snap.put("cfg_" + key, cv);
                } else if (row.has("config_value") && !row.isNull("config_value")) {
                    Object raw = row.get("config_value");
                    if (raw instanceof Boolean) snap.put(key, (Boolean) raw);
                    else {
                        String s = String.valueOf(raw);
                        if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s))
                            snap.put(key, Boolean.parseBoolean(s));
                    }
                } else if (row.has("enabled")) {
                    snap.put(key, row.optBoolean("enabled", false));
                }
            }
        } catch (Exception ignored) {}
        // emergency_controls جدول منفصل
        mergeEmergencyControls(snap);
    }

    private void mergeEmergencyControls(JSONObject snap) {
        ApiResult<String> r = http.get("/rest/v1/emergency_controls?select=*&limit=50");
        if (!r.isSuccess() || r.data == null) return;
        try {
            JSONArray arr = new JSONArray(r.data);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                String key = firstString(row, "control_key", "key", "name");
                if (key == null) continue;
                boolean en = row.optBoolean("enabled", false);
                String k = key.trim().toLowerCase();
                if (k.contains("kill")) snap.put("kill_switch", en);
                if (k.contains("maintenance")) snap.put("maintenance", en);
                snap.put("emergency_" + k, en);
            }
        } catch (Exception ignored) {}
    }

    /** schema: feature_key + enabled */
    private void mergeFeatureFlags(JSONObject snap) {
        ApiResult<String> r = http.get("/rest/v1/feature_flags?select=*&limit=200");
        if (!r.isSuccess() || r.data == null) return;
        try {
            JSONObject flags = snap.optJSONObject("feature_flags");
            if (flags == null) flags = new JSONObject();
            JSONArray arr = new JSONArray(r.data);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                String key = firstString(row, "feature_key", "key", "flag_key", "name", "flag");
                if (key == null) continue;
                boolean en = row.optBoolean("enabled", row.optBoolean("is_enabled", false));
                flags.put(key, en);
                String k = key.toLowerCase();
                if (k.contains("banner")) snap.put("banner_ads", en);
                if (k.contains("interstitial")) snap.put("interstitial_ads", en);
                if (k.contains("rewarded")) snap.put("rewarded_ads", en);
                if (k.equals("ads") || k.equals("premium")) snap.put(k, en);
            }
            snap.put("feature_flags", flags);
        } catch (Exception ignored) {}
    }

    private void mergeAppVersions(JSONObject snap) {
        // غالبًا القيم داخل remote_config.app_update؛ الجدول قد يكون فارغًا
        ApiResult<String> r = http.get("/rest/v1/app_versions?select=*&limit=5");
        if (!r.isSuccess() || r.data == null) return;
        try {
            JSONArray arr = new JSONArray(r.data);
            if (arr.length() == 0) return;
            // The contract uses version_code/version_name. Do not depend on row order.
            JSONObject row = arr.getJSONObject(0);
            int bestCode = row.optInt("version_code", -1);
            for (int i = 1; i < arr.length(); i++) {
                JSONObject candidate = arr.getJSONObject(i);
                int code = candidate.optInt("version_code", -1);
                if (code > bestCode) {
                    row = candidate;
                    bestCode = code;
                }
            }
            putIf(snap, "latest_version", firstString(row, "version_name", "latest_version", "version"));
            if (row.has("version_code")) snap.put("latest_version_code", row.optInt("version_code", 0));
            if (row.has("minimum_version_code")) snap.put("minimum_version_code", row.optInt("minimum_version_code", 1));
            putIf(snap, "minimum_version", firstString(row, "minimum_version", "min_version", "minimum_supported_version"));
            putIf(snap, "apk_url", firstString(row, "apk_url", "download_url", "url"));
            putIf(snap, "update_message", firstString(row, "update_message", "message"));
            putIf(snap, "release_notes", firstString(row, "release_notes", "notes"));
            if (row.has("force_update")) snap.put("force_update", row.optBoolean("force_update", false));
        } catch (Exception ignored) {}
    }

    private void mergeMaintenanceControls(JSONObject snap) {
        ApiResult<String> r = http.get("/rest/v1/maintenance_controls?select=*&id=eq.true&limit=1");
        if (!r.isSuccess() || r.data == null) return;
        try {
            JSONArray arr = new JSONArray(r.data);
            if (arr.length() == 0) return;
            JSONObject row = arr.getJSONObject(0);
            boolean enabled = row.optBoolean("enabled", false);
            long now = System.currentTimeMillis();
            long start = parseRemoteTime(row, "starts_at");
            long end = parseRemoteTime(row, "ends_at");
            if (start > 0 && now < start) enabled = false;
            if (end > 0 && now > end) enabled = false;
            snap.put("maintenance", enabled);
            String msg = row.optString("message", "");
            if (!msg.isEmpty()) snap.put("maintenance_message", msg);
        } catch (Exception ignored) {}
    }

    private void mergeAdSettings(JSONObject snap) {
        // جدول ad_settings قد يكون فارغًا؛ الإعدادات الأساسية من remote_config.ad_settings
        ApiResult<String> r = http.get("/rest/v1/ad_settings?select=*&limit=20");
        if (!r.isSuccess() || r.data == null) return;
        try {
            JSONArray arr = new JSONArray(r.data);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row.has("ads_enabled")) snap.put("ads", row.optBoolean("ads_enabled", false));
                if (row.has("banner_enabled")) snap.put("banner_ads", row.optBoolean("banner_enabled", false));
                if (row.has("interstitial_enabled")) snap.put("interstitial_ads", row.optBoolean("interstitial_enabled", false));
                if (row.has("rewarded_enabled")) snap.put("rewarded_ads", row.optBoolean("rewarded_enabled", false));
            }
        } catch (Exception ignored) {}
    }

    private static JSONObject asObject(Object o) {
        if (o == null || o == JSONObject.NULL) return null;
        if (o instanceof JSONObject) return (JSONObject) o;
        if (o instanceof String) {
            String s = ((String) o).trim();
            if (s.startsWith("{")) {
                try { return new JSONObject(s); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void mergePremiumForCurrentUser(JSONObject snap) {
        String userId = AccountAuth.getSessionUserId(app);
        if (userId == null || userId.isEmpty()) userId = AppInfrastructure.getUserId(app);
        String remoteUid = (userId == null || userId.isEmpty()) ? "" : remoteUserUuid(userId);
        if (remoteUid.isEmpty() && (userId == null || userId.isEmpty())) {
            try {
                snap.put("premium_active", false);
                snap.put("premium_feature_keys", new JSONArray());
            } catch (Exception ignored) {}
            return;
        }
        // جرّب UUID المحوّل ثم local id ثم installation_id
        ApiResult<String> r = null;
        if (!remoteUid.isEmpty()) {
            r = http.get("/rest/v1/premium_grants?select=*&user_id=eq." + urlEncode(remoteUid) + "&limit=20");
        }
        if (r == null || !r.isSuccess() || r.data == null || "[]".equals(r.data.trim())) {
            if (userId != null && !userId.isEmpty()) {
                r = http.get("/rest/v1/premium_grants?select=*&user_id=eq." + urlEncode(userId) + "&limit=20");
            }
        }
        if (r == null || !r.isSuccess() || r.data == null || "[]".equals(r.data.trim())) {
            String inst = AppInfrastructure.getInstallationId(app);
            r = http.get("/rest/v1/premium_grants?select=*&installation_id=eq." + urlEncode(inst) + "&limit=20");
        }
        boolean active = false;
        JSONArray featureKeys = new JSONArray();
        if (r != null && r.isSuccess() && r.data != null) {
            try {
                JSONArray arr = new JSONArray(r.data);
                long now = System.currentTimeMillis();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    boolean en = row.optBoolean("active", true);
                    if (!en) continue;
                    String status = row.optString("status", "active");
                    if ("revoked".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)) continue;
                    if (isGrantExpired(row, now)) continue;
                    active = true;
                    // feature_keys: array أو string JSON
                    Object fk = row.opt("feature_keys");
                    if (fk instanceof JSONArray) {
                        JSONArray a = (JSONArray) fk;
                        for (int j = 0; j < a.length(); j++) {
                            String k = a.optString(j, "").trim();
                            if (!k.isEmpty()) featureKeys.put(k);
                        }
                    } else if (fk != null) {
                        String s = String.valueOf(fk).trim();
                        if (s.startsWith("[")) {
                            JSONArray a = new JSONArray(s);
                            for (int j = 0; j < a.length(); j++) {
                                String k = a.optString(j, "").trim();
                                if (!k.isEmpty()) featureKeys.put(k);
                            }
                        } else if (!s.isEmpty()) {
                            featureKeys.put(s);
                        }
                    }
                    String single = row.optString("feature_key", "").trim();
                    if (!single.isEmpty()) featureKeys.put(single);
                }
            } catch (Exception ignored) {}
        }
        try {
            snap.put("premium_active", active);
            snap.put("premium_feature_keys", featureKeys);
        } catch (Exception ignored) {}
    }

    private static boolean isGrantExpired(JSONObject row, long nowMs) {
        try {
            long exp = row.optLong("expires_at", 0);
            if (exp > 1_000_000_000_000L && exp < nowMs) return true; // epoch ms
            if (exp > 1_000_000_000L && exp < nowMs / 1000L) return true; // epoch sec
            String expS = row.optString("expires_at", "");
            if (expS.length() >= 10) {
                // ISO-8601 تقريبي: قارن بادئة التاريخ إن أمكن
                try {
                    java.time.Instant inst = java.time.Instant.parse(expS);
                    if (inst.toEpochMilli() < nowMs) return true;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * يحوّل userId المحلي (مثل U-XXXX) إلى UUID ثابت لعمود uuid في Supabase.
     * لا يغيّر الـuserId المحلي في AccountAuth.
     */
    public static String remoteUserUuid(String localUserId) {
        if (localUserId == null) localUserId = "";
        return java.util.UUID.nameUUIDFromBytes(
                ("myplan-user:" + localUserId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /** تسجيل/تحديث صف في app_users — بيانات أساسية فقط (لا Planner). */
    public ApiResult<Void> registerAppUser(AccountAuth.Account account) {
        if (!isReady()) return ApiResult.notConfigured();
        if (account == null || account.userId == null || account.userId.isEmpty()) {
            return ApiResult.validation(0, "لا يوجد حساب");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("id", remoteUserUuid(account.userId));
            body.put("local_user_id", account.userId);
            if (account.email != null && !account.email.isEmpty()) body.put("email", account.email);
            if (account.displayName != null && !account.displayName.isEmpty()) {
                body.put("display_name", account.displayName);
            }
            body.put("app_version", com.myplan.app.BuildConfig.VERSION_NAME);
            body.put("android_version", android.os.Build.VERSION.RELEASE);
            body.put("last_seen_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
                    .format(new java.util.Date()));
            // Prefer merge على id إن وُجدت سياسة/قيود
            ApiResult<String> r = http.postPrefer(
                    "/rest/v1/app_users?on_conflict=id",
                    "[" + body + "]",
                    "resolution=merge-duplicates,return=minimal");
            if (r.isSuccess()) return ApiResult.success(null);
            // محاولة INSERT عادية
            r = http.post("/rest/v1/app_users", "[" + body + "]");
            if (r.isSuccess()) return ApiResult.success(null);
            return ApiResult.unknown(r.message);
        } catch (Exception e) {
            return ApiResult.unknown("app_users register failed");
        }
    }

    /** تسجيل/تحديث جهاز — يطابق schema.devices مباشرة. */
    public ApiResult<Void> upsertDevice() {
        if (!isReady()) return ApiResult.notConfigured();
        try {
            JSONObject body = new JSONObject();
            String inst = AppInfrastructure.getInstallationId(app);
            body.put("installation_id", inst);
            String uid = AccountAuth.getSessionUserId(app);
            if (uid != null && !uid.isEmpty()) body.put("user_id", remoteUserUuid(uid));
            body.put("manufacturer", android.os.Build.MANUFACTURER);
            body.put("model", android.os.Build.MODEL);
            body.put("android_version", android.os.Build.VERSION.RELEASE);
            body.put("app_version", com.myplan.app.BuildConfig.VERSION_NAME);
            body.put("platform", "android");
            body.put("status", "active");
            body.put("last_seen_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
                    .format(new java.util.Date()));

            ApiResult<String> r = http.postPrefer(
                    "/rest/v1/devices?on_conflict=installation_id",
                    "[" + body + "]",
                    "resolution=merge-duplicates,return=minimal");
            if (r.isSuccess()) return ApiResult.success(null);
            return ApiResult.unknown(r.message);
        } catch (Exception e) {
            return ApiResult.unknown("device upsert failed");
        }
    }

    /** تحديث FCM token وربط الجهاز بالمستخدم. */
    public ApiResult<Void> upsertDeviceFcmToken(String token) {
        if (!isReady() || token == null || token.trim().isEmpty()) return ApiResult.notConfigured();
        try {
            JSONObject body = new JSONObject();
            body.put("fcm_token", token.trim());
            body.put("last_seen_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(new java.util.Date()));
            ApiResult<String> r = http.patch("/rest/v1/devices?installation_id=eq." + urlEncode(AppInfrastructure.getInstallationId(app)), body.toString());
            if (r.isSuccess()) return ApiResult.success(null);
            return ApiResult.unknown(r.message);
        } catch (Exception e) { return ApiResult.unknown("FCM token update failed"); }
    }

    /** تسجيل security event عبر RPC؛ لا يسمح التطبيق بتغيير سجلات الإدارة مباشرة. */
    public ApiResult<Void> logSecurityEvent(String eventType, String severity, JSONObject details) {
        if (!isReady()) return ApiResult.notConfigured();
        try {
            JSONObject b = new JSONObject();
            b.put("p_installation_id", AppInfrastructure.getInstallationId(app));
            String uid = AccountAuth.getSessionUserId(app);
            if (uid != null && !uid.isEmpty()) b.put("p_user_id", remoteUserUuid(uid)); else b.put("p_user_id", JSONObject.NULL);
            b.put("p_event_type", eventType == null ? "unknown" : eventType);
            b.put("p_severity", severity == null ? "info" : severity);
            b.put("p_details", details == null ? new JSONObject() : details);
            ApiResult<String> r = http.post("/rest/v1/rpc/myplan_log_security_event", b.toString());
            return r.isSuccess() ? ApiResult.success(null) : ApiResult.unknown(r.message);
        } catch (Exception e) { return ApiResult.unknown("security event failed"); }
    }

    /** GET عام لطبقة AdminBanGate — بدون لمس Planner. */
    public ApiResult<String> httpGet(String pathAndQuery) {
        return http.get(pathAndQuery);
    }

    /**
     * رسائل المطور → المستخدم فقط من المصدر:
     * message_type=in_app AND (target_type=all OR target_type=user+target_id).
     * لا يجلب contact / user_to_dev / support.
     */
    public ApiResult<JSONArray> fetchInboxMessages() {
        if (!isReady()) return ApiResult.notConfigured();
        JSONArray out = new JSONArray();
        try {
            String inst = AppInfrastructure.getInstallationId(app);
            JSONObject b = new JSONObject().put("p_installation_id", inst);
            ApiResult<String> chat = http.post("/rest/v1/rpc/myplan_get_support_messages", b.toString());
            if (chat.isSuccess() && chat.data != null) {
                JSONArray a = new JSONArray(chat.data);
                for (int i=0;i<a.length();i++) {
                    JSONObject x=a.getJSONObject(i);
                    JSONObject m=new JSONObject();
                    m.put("id",x.optLong("id",0));
                    m.put("title","محادثة الدعم");
                    m.put("body",("admin".equalsIgnoreCase(x.optString("sender_type",""))
                            ? "الدعم: " : "أنت: ") + x.optString("body",""));
                    m.put("created_at",x.optString("created_at",""));
                    m.put("target_type","conversation");
                    m.put("is_active",true);
                    m.put("message_type","in_app");
                    m.put("sender_type",x.optString("sender_type",""));
                    out.put(m);
                }
            }
            // Keep broadcast announcements/in-app messages in the same Inbox.
            String localUid = AccountAuth.getSessionUserId(app);
            if (localUid == null || localUid.isEmpty()) localUid = AppInfrastructure.getUserId(app);
            String remoteId = (localUid == null || localUid.isEmpty()) ? "" : remoteUserUuid(localUid);
            String q = "/rest/v1/remote_messages?select=id,title,body,created_at,target_type,target_id,is_active,message_type"
                    + "&is_active=eq.true&message_type=eq.in_app"
                    + (remoteId.isEmpty()
                        ? "&target_type=eq.all"
                        : "&or=(target_type.eq.all,and(target_type.eq.user,target_id.eq."+remoteId+"))")
                    + "&order=created_at.desc&limit=50";
            ApiResult<String> r=http.get(q);
            if(r.isSuccess() && r.data!=null){
                JSONArray a=new JSONArray(r.data);
                for(int i=0;i<a.length();i++) out.put(a.getJSONObject(i));
            }
            return ApiResult.success(out);
        } catch(Exception e) { return ApiResult.unknown("inbox fetch failed"); }
    }

    public ApiResult<Void> markSupportMessagesRead() {
        if (!isReady()) return ApiResult.notConfigured();
        try {
            JSONObject b=new JSONObject().put("p_installation_id",AppInfrastructure.getInstallationId(app));
            ApiResult<String> r=http.post("/rest/v1/rpc/myplan_mark_support_read",b.toString());
            return r.isSuccess()?ApiResult.success(null):ApiResult.unknown(r.message);
        } catch(Exception e){return ApiResult.unknown("mark support read failed");}
    }

    /** حذف رسالة من remote_messages. لا يخفي محليًا عند الفشل. */
    public ApiResult<Void> deleteMessage(long id) {
        if (!isReady()) return ApiResult.notConfigured();
        if (id <= 0) return ApiResult.validation(0, "معرّف غير صالح");
        try {
            ApiResult<String> r = http.delete("/rest/v1/remote_messages?id=eq." + id);
            if (r.isSuccess()) return ApiResult.success(null);
            return ApiResult.unknown(r.message != null ? r.message : "تعذّر الحذف");
        } catch (Exception e) {
            return ApiResult.unknown("delete message failed");
        }
    }

    /**
     * User → Developer: message_type=contact + target_type=support.
     * لا تظهر في inbox المطور داخل التطبيق.
     */
    public ApiResult<Void> submitMessage(String type, String message, String userId, String installationId) {
        if (!isReady()) return ApiResult.notConfigured();
        try {
            // Primary contract: real two-way support conversation.
            JSONObject rpc = new JSONObject();
            rpc.put("p_installation_id", installationId == null || installationId.isEmpty()
                    ? AppInfrastructure.getInstallationId(app) : installationId);
            String uid = userId;
            if (uid == null || uid.isEmpty()) uid = AccountAuth.getSessionUserId(app);
            if (uid == null || uid.isEmpty()) uid = AppInfrastructure.getUserId(app);
            rpc.put("p_user_id", uid == null || uid.isEmpty() ? JSONObject.NULL : remoteUserUuid(uid));
            rpc.put("p_body", message == null ? "" : message);
            ApiResult<String> chat = http.post("/rest/v1/rpc/myplan_send_support_message", rpc.toString());
            if (chat.isSuccess()) return ApiResult.success(null);

            JSONObject body = new JSONObject();
            body.put("title", type == null || type.isEmpty() ? "تواصل" : type);
            body.put("body", message == null ? "" : message);
            // remote_messages contract accepts in_app/announcement/push/maintenance/update/warning.
            // A user->Control Center message is stored as a user-targeted in_app message,
            // so it remains visible to admins without violating the schema CHECK constraint.
            body.put("message_type", "in_app");
            body.put("target_type", "user");
            body.put("is_active", true);
            String localUid = userId;
            if (localUid == null || localUid.isEmpty()) {
                localUid = AccountAuth.getSessionUserId(app);
            }
            if (localUid == null || localUid.isEmpty()) {
                localUid = AppInfrastructure.getUserId(app);
            }
            if (localUid != null && !localUid.isEmpty()) {
                body.put("target_id", remoteUserUuid(localUid));
            }
            ApiResult<String> r = http.post("/rest/v1/remote_messages", "[" + body + "]");
            if (r.isSuccess()) return ApiResult.success(null);
            return ApiResult.unknown(r.message);
        } catch (Exception e) {
            return ApiResult.unknown("submit message failed");
        }
    }

    /** يجلب رسائل Control Center التي يجب أن تظهر داخل التطبيق أو كإشعار. */
    private void mergeRemoteMessages(JSONObject snap) {
        try {
            String localUid = AccountAuth.getSessionUserId(app);
            if (localUid == null || localUid.isEmpty()) localUid = AppInfrastructure.getUserId(app);
            String remoteId = (localUid == null || localUid.isEmpty()) ? "" : remoteUserUuid(localUid);
            String base = "/rest/v1/remote_messages?select=*&is_active=eq.true"
                    + "&order=priority.desc,created_at.desc&limit=100";
            ApiResult<String> r = http.get(base);
            if (!r.isSuccess() || r.data == null) return;

            JSONArray src = new JSONArray(r.data);
            JSONArray inbox = new JSONArray();
            JSONArray push = new JSONArray();
            long now = System.currentTimeMillis();

            for (int i = 0; i < src.length(); i++) {
                JSONObject o = src.getJSONObject(i);
                if (!o.optBoolean("is_active", true)) continue;

                long start = parseRemoteTime(o, "starts_at");
                long end = parseRemoteTime(o, "expires_at");
                if (start > 0 && now < start) continue;
                if (end > 0 && now > end) continue;

                String type = o.optString("message_type", "").trim().toLowerCase();
                String target = o.optString("target_type", "all").trim().toLowerCase();
                String targetId = o.optString("target_id", "").trim();

                boolean audienceOk = "all".equals(target);
                if ("user".equals(target) && !remoteId.isEmpty()) {
                    audienceOk = remoteId.equalsIgnoreCase(targetId)
                            || (localUid != null && localUid.equalsIgnoreCase(targetId));
                } else if ("device".equals(target)) {
                    audienceOk = AppInfrastructure.getInstallationId(app).equalsIgnoreCase(targetId);
                }
                if (!audienceOk) continue;

                if ("in_app".equals(type) || "announcement".equals(type)
                        || "maintenance".equals(type) || "update".equals(type)
                        || "warning".equals(type)) {
                    inbox.put(o);
                }
                if ("push".equals(type)) push.put(o);
            }

            snap.put("remote_messages", inbox);
            snap.put("remote_push_messages", push);
        } catch (Exception ignored) {}
    }

    private static long parseRemoteTime(JSONObject o, String key) {
        try {
            if (!o.has(key) || o.isNull(key)) return 0;
            String s = o.optString(key, "");
            if (s.isEmpty()) return 0;
            try { return java.time.Instant.parse(s).toEpochMilli(); } catch (Exception ignored) {}
            long n = o.optLong(key, 0);
            if (n > 1_000_000_000_000L) return n;
            if (n > 1_000_000_000L) return n * 1000L;
        } catch (Exception ignored) {}
        return 0;
    }

    /** الأعمدة المؤكدة: message. RLS يمنع INSERT لـ anon حاليًا. */
    public ApiResult<Void> reportError(String category, String message) {
        if (!isReady()) return ApiResult.notConfigured();
        try {
            JSONObject body = new JSONObject();
            body.put("message", (category == null ? "app" : category) + ": " + (message == null ? "" : message));
            ApiResult<String> r = http.post("/rest/v1/error_logs", "[" + body + "]");
            if (r.isSuccess()) return ApiResult.success(null);
            return ApiResult.unknown(r.message);
        } catch (Exception e) {
            return ApiResult.unknown("error log failed");
        }
    }

    private static String firstString(JSONObject row, String... keys) {
        for (String k : keys) {
            if (row.has(k) && !row.isNull(k)) {
                String v = row.optString(k, null);
                if (v != null && !v.isEmpty()) return v;
            }
        }
        return null;
    }

    private static void putIf(JSONObject snap, String key, String val) throws Exception {
        if (val != null && !val.isEmpty()) snap.put(key, val);
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
