package com.myplan.app.supabase;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.api.ApiResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * حظر الحساب من app_users.status (banned/disabled).
 * حظر الجهاز من devices.status=blocked.
 * معرّف الجهاز: android_id أولًا ثم installation_id.
 */
public final class AdminBanGate {
    private AdminBanGate() {}

    private static final String PREFS = "myplan_admin_ban_v1";
    private static final String KEY_ACC_BANNED = "account_banned";
    private static final String KEY_DEV_BANNED = "device_banned";
    private static final String KEY_ACC_MSG = "account_ban_msg";
    private static final String KEY_DEV_MSG = "device_ban_msg";
    private static final String KEY_CHECKED_AT = "checked_at";
    private static final String KEY_ANDROID_ID = "last_android_id";

    public static final class BanStatus {
        public boolean accountBanned;
        public boolean deviceBanned;
        public String accountMessage = "";
        public String deviceMessage = "";
        public boolean fromCache;
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getAndroidId(Context c) {
        try {
            String id = Settings.Secure.getString(
                    c.getApplicationContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            return id == null ? "" : id.trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static BanStatus cached(Context c) {
        SharedPreferences p = sp(c);
        BanStatus s = new BanStatus();
        s.accountBanned = p.getBoolean(KEY_ACC_BANNED, false);
        s.deviceBanned = p.getBoolean(KEY_DEV_BANNED, false);
        s.accountMessage = p.getString(KEY_ACC_MSG, "");
        s.deviceMessage = p.getString(KEY_DEV_MSG, "");
        s.fromCache = true;
        return s;
    }

    public static void save(Context c, BanStatus s) {
        sp(c).edit()
                .putBoolean(KEY_ACC_BANNED, s.accountBanned)
                .putBoolean(KEY_DEV_BANNED, s.deviceBanned)
                .putString(KEY_ACC_MSG, s.accountMessage == null ? "" : s.accountMessage)
                .putString(KEY_DEV_MSG, s.deviceMessage == null ? "" : s.deviceMessage)
                .putString(KEY_ANDROID_ID, getAndroidId(c))
                .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                .apply();
    }

    /**
     * جلب حي من Supabase ثم تحديث الكاش عند نجاح الشبكة.
     * عند الفشل الشبكي يُعاد الكاش المحلي (الحظر يبقى Offline).
     */
    public static BanStatus refresh(Context c) {
        if (!SupabaseConfig.isConfigured(c)) {
            return cached(c);
        }
        BanStatus s = new BanStatus();
        s.fromCache = false;
        BanStatus prev = cached(c);
        boolean accFetched = false;
        boolean devFetched = false;
        SupabaseRepository repo = new SupabaseRepository(c);
        try {
            String localUid = AccountAuth.getSessionUserId(c);
            if (localUid == null || localUid.isEmpty()) {
                localUid = AppInfrastructure.getUserId(c);
            }
            if (localUid != null && !localUid.isEmpty()) {
                String remoteId = SupabaseRepository.remoteUserUuid(localUid);
                AccountAuth.Account a = AccountAuth.getCurrentAccount(c);
                String email = (a != null && a.email != null) ? a.email.trim() : "";
                // 1) بالمعرّف  2) بالإيميل  3) صريح banned=true
                ApiResult<String> r = repo.httpGet(
                        "/rest/v1/app_users?id=eq." + urlEnc(remoteId)
                                + "&select=id,email,status&limit=1");
                if ((!r.isSuccess() || r.data == null || "[]".equals(r.data.trim()))
                        && !email.isEmpty()) {
                    r = repo.httpGet(
                            "/rest/v1/app_users?email=eq." + urlEnc(email)
                                    + "&select=id,email,status&limit=1");
                }
                if ((!r.isSuccess() || r.data == null || "[]".equals(r.data.trim()))
                        && !email.isEmpty()) {
                    r = repo.httpGet(
                            "/rest/v1/app_users?email=eq." + urlEnc(email)
                                    + "&status=in.(banned,disabled)&select=id,email,status&limit=1");
                }
                if ((!r.isSuccess() || r.data == null || "[]".equals(r.data.trim()))) {
                    r = repo.httpGet(
                            "/rest/v1/app_users?id=eq." + urlEnc(remoteId)
                                    + "&status=in.(banned,disabled)&select=id,email,status&limit=1");
                }
                if (r.isSuccess() && r.data != null) {
                    accFetched = true;
                    JSONArray arr = new JSONArray(r.data);
                    if (arr.length() > 0) {
                        String status = arr.getJSONObject(0).optString("status", "active");
                        boolean banned = "banned".equalsIgnoreCase(status) || "disabled".equalsIgnoreCase(status);
                        s.accountBanned = banned;
                        if (banned) s.accountMessage = "تم حظر هذا الحساب من لوحة التحكم.";
                    } else {
                        // صف فارغ = غير محظور (أو RLS تخفي الصف — لا نكسر كاش الحظر السابق إن وُجد)
                        if (prev.accountBanned) {
                            accFetched = false; // احتفظ بالكاش
                        } else {
                            s.accountBanned = false;
                        }
                    }
                }
            } else {
                accFetched = true;
                s.accountBanned = false;
            }

            String androidId = getAndroidId(c);
            String inst = AppInfrastructure.getInstallationId(c);
            JSONObject matched = null;

            if (androidId != null && !androidId.isEmpty()) {
                ApiResult<String> byAid = repo.httpGet(
                        "/rest/v1/devices?installation_id=eq." + urlEnc(inst)
                                + "&select=status,installation_id&limit=5");
                if (byAid.isSuccess() && byAid.data != null) {
                    devFetched = true;
                    matched = firstBlockedOrFirst(byAid.data);
                }
            }
            if (matched == null && inst != null && !inst.isEmpty()) {
                ApiResult<String> byInst = repo.httpGet(
                        "/rest/v1/devices?installation_id=eq." + urlEnc(inst)
                                + "&select=status,installation_id&limit=5");
                if (byInst.isSuccess() && byInst.data != null) {
                    devFetched = true;
                    if (matched == null) matched = firstBlockedOrFirst(byInst.data);
                }
            }

            if (devFetched) {
                s.deviceBanned = matched != null && "blocked".equalsIgnoreCase(matched.optString("status", ""));
                if (s.deviceBanned) s.deviceMessage = "تم حظر هذا الجهاز من لوحة التحكم.";
            }
        } catch (Exception e) {
            return prev;
        }

        if (!accFetched) {
            s.accountBanned = prev.accountBanned;
            s.accountMessage = prev.accountMessage;
        }
        if (!devFetched) {
            s.deviceBanned = prev.deviceBanned;
            s.deviceMessage = prev.deviceMessage;
        }
        if (accFetched || devFetched) save(c, s);
        else return prev;
        return s;
    }

    private static JSONObject firstBlockedOrFirst(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject first = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (first == null) first = row;
                if ("blocked".equalsIgnoreCase(row.optString("status", ""))) return row;
            }
            return first;
        } catch (Exception e) {
            return null;
        }
    }

    private static String urlEnc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
