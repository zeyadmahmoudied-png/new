package com.myplan.app.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.api.ApiResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Account/device enforcement for the new Control Center contract.
 * profiles.status = banned|disabled => account blocked.
 * devices.status = blocked => device blocked.
 *
 * No Planner data is touched.
 */
public final class AdminBanGate {
    private AdminBanGate() {}

    private static final String PREFS = "myplan_admin_ban_v2";
    private static final String KEY_ACC = "account_banned";
    private static final String KEY_DEV = "device_banned";
    private static final String KEY_ACC_MSG = "account_message";
    private static final String KEY_DEV_MSG = "device_message";
    private static final String KEY_CHECKED = "checked_at";

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

    public static BanStatus cached(Context c) {
        SharedPreferences p = sp(c);
        BanStatus s = new BanStatus();
        s.accountBanned = p.getBoolean(KEY_ACC, false);
        s.deviceBanned = p.getBoolean(KEY_DEV, false);
        s.accountMessage = p.getString(KEY_ACC_MSG, "");
        s.deviceMessage = p.getString(KEY_DEV_MSG, "");
        s.fromCache = true;
        return s;
    }

    public static void save(Context c, BanStatus s) {
        sp(c).edit()
                .putBoolean(KEY_ACC, s.accountBanned)
                .putBoolean(KEY_DEV, s.deviceBanned)
                .putString(KEY_ACC_MSG, s.accountMessage == null ? "" : s.accountMessage)
                .putString(KEY_DEV_MSG, s.deviceMessage == null ? "" : s.deviceMessage)
                .putLong(KEY_CHECKED, System.currentTimeMillis())
                .apply();
    }

    public static BanStatus refresh(Context c) {
        Context app = c.getApplicationContext();
        BanStatus previous = cached(app);
        if (!SupabaseConfig.isConfigured(app)) return previous;

        BanStatus out = new BanStatus();
        out.fromCache = false;
        boolean accountFetched = false;
        boolean deviceFetched = false;
        SupabaseHttp http = new SupabaseHttp(app);

        try {
            String local = AccountAuth.getSessionUserId(app);
            if (local == null || local.isEmpty()) local = AppInfrastructure.getUserId(app);
            String remote = SupabaseRepository.remoteUserUuid(local);

            if (!remote.isEmpty()) {
                ApiResult<String> r = http.get("/rest/v1/profiles?id=eq." + enc(remote)
                        + "&select=status&limit=1");
                if (r.isSuccess() && r.data != null) {
                    JSONArray a = new JSONArray(r.data);
                    accountFetched = true;
                    if (a.length() > 0) {
                        String status = a.getJSONObject(0).optString("status", "active");
                        out.accountBanned = "banned".equalsIgnoreCase(status)
                                || "disabled".equalsIgnoreCase(status);
                        if (out.accountBanned) out.accountMessage =
                                "تم حظر هذا الحساب من لوحة التحكم.";
                    }
                }
            }

            String inst = AppInfrastructure.getInstallationId(app);
            ApiResult<String> d = http.get("/rest/v1/devices?installation_id=eq."
                    + enc(inst) + "&select=status&limit=1");
            if (d.isSuccess() && d.data != null) {
                JSONArray a = new JSONArray(d.data);
                deviceFetched = true;
                if (a.length() > 0) {
                    out.deviceBanned =
                            "blocked".equalsIgnoreCase(a.getJSONObject(0).optString("status", ""));
                    if (out.deviceBanned) out.deviceMessage =
                            "تم حظر هذا الجهاز من لوحة التحكم.";
                }
            }
        } catch (Throwable ignored) {}

        if (!accountFetched) {
            out.accountBanned = previous.accountBanned;
            out.accountMessage = previous.accountMessage;
        }
        if (!deviceFetched) {
            out.deviceBanned = previous.deviceBanned;
            out.deviceMessage = previous.deviceMessage;
        }
        save(app, out);
        return out;
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return s == null ? "" : s; }
    }
}
