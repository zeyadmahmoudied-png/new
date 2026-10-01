package com.myplan.app.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.api.ApiResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * مزامنة Remote Features في الخلفية — لا تلمس Planner.
 */
public final class RemoteSyncCoordinator {
    private RemoteSyncCoordinator() {}

    public static String lastStatus = "idle";

    public static void syncAsync(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                lastStatus = doSync(app);
            } catch (Exception e) {
                lastStatus = "error";
            }
        }, "remote-sync").start();
    }

    public static String doSync(Context app) {
        if (!SupabaseConfig.isConfigured(app)) {
            lastStatus = "not_configured";
            return lastStatus;
        }
        SupabaseRepository repo = new SupabaseRepository(app);
        android.util.Log.d("RemoteSync", "REMOTE_SYNC_START");
        ApiResult<JSONObject> fetched = repo.fetchAndCacheControlState();
        if (fetched.isSuccess()) {
            applyFeatureFlagsToLocal(app);
            applyRemotePremiumGrant(app);
            flushPendingContactMessages(app, repo);
            AnnouncementManager.fetchAndCache(app);
            RemoteScheduledActionManager.syncAndApply(app);
            lastStatus = "ok";
            android.util.Log.d("RemoteSync", "REMOTE_SYNC_SUCCESS");
        } else if (fetched.kind == ApiResult.Kind.NOT_CONFIGURED) {
            lastStatus = "not_configured";
            android.util.Log.d("RemoteSync", "REMOTE_SYNC_FAILED not_configured");
        } else if (fetched.kind == ApiResult.Kind.NETWORK_ERROR || fetched.kind == ApiResult.Kind.TIMEOUT) {
            lastStatus = "offline_cache";
            android.util.Log.d("RemoteSync", "REMOTE_SYNC_PARTIAL offline_cache");
        } else {
            lastStatus = "fetch_failed:" + fetched.message;
            android.util.Log.d("RemoteSync", "REMOTE_SYNC_FAILED " + lastStatus);
        }
        // تسجيل المستخدم/الجهاز + تحديث الحظر — مستقل عن نجاح Config
        try {
            AccountAuth.Account acc = AccountAuth.getCurrentAccount(app);
            if (acc != null) {
                ApiResult<Void> ur = repo.registerAppUser(acc);
                android.util.Log.d("RemoteSync", "USER_REGISTER " + (ur != null && ur.isSuccess()));
            }
            ApiResult<Void> dr = repo.upsertDevice();
            android.util.Log.d("RemoteSync", "DEVICE_UPSERT " + (dr != null && dr.isSuccess()));
            AdminBanGate.refresh(app);
        } catch (Exception ignored) {}
        return lastStatus;
    }

    /** يحدّث Flags المحلية من الكاش البعيد بدون مسح قيم غير معروفة */
    private static void applyFeatureFlagsToLocal(Context c) {
        JSONObject snap = RemoteControlCache.loadSnapshot(c);
        if (snap == null) return;
        AppInfrastructure.Flags f = AppInfrastructure.getFlags(c);
        JSONObject flags = snap.optJSONObject("feature_flags");
        if (flags != null) {
            if (flags.has("premium")) f.premium = flags.optBoolean("premium", f.premium);
            if (flags.has("ads")) f.ads = flags.optBoolean("ads", f.ads);
            if (flags.has("ai")) f.ai = flags.optBoolean("ai", f.ai);
            if (flags.has("cloud")) f.cloud = flags.optBoolean("cloud", f.cloud);
        }
        if (snap.has("ads")) f.ads = snap.optBoolean("ads", f.ads);
        if (snap.has("premium")) f.premium = snap.optBoolean("premium", f.premium);
        AppInfrastructure.setFlags(c, f);
    }

    /** يضيف/يزيل entitlement مصدره server حسب premium_grants — لا يمس developer_test */
    private static void applyRemotePremiumGrant(Context c) {
        boolean remoteOn = RemoteControlCache.remotePremiumActive(c);
        // استخدام set عبر entitlement list موجود في AppInfrastructure بصعوبة —
        // نخزّن فقط علمًا في الكاش؛ isPremiumActive يُوسَّع للقراءة.
        SharedPreferences p = c.getSharedPreferences("myplan_remote_control_v1", Context.MODE_PRIVATE);
        p.edit().putBoolean("premium_active", remoteOn).apply();
    }

    public static void flushPendingContactMessages(Context c, SupabaseRepository repo) {
        SharedPreferences sp = c.getSharedPreferences("myplan_contact_v1", Context.MODE_PRIVATE);
        String raw = sp.getString("messages", "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String status = o.optString("status", "pending");
                if ("sent".equals(status)) continue;
                ApiResult<Void> r = repo.submitMessage(
                        o.optString("type", "أخرى"),
                        o.optString("message", ""),
                        o.optString("userId", ""),
                        o.optString("installationId", ""));
                if (r.isSuccess()) {
                    o.put("status", "sent");
                    changed = true;
                }
            }
            if (changed) sp.edit().putString("messages", arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
