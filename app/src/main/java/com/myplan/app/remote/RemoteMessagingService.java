package com.myplan.app.remote;

import android.content.Context;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.api.ApiResult;
import com.myplan.app.supabase.SupabaseConfig;
import com.myplan.app.supabase.SupabaseHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * Fresh Control Center messaging contract.
 *
 * Both directions use public.remote_messages:
 * user -> center: message_type=contact, target_type=support
 * center -> user: message_type=in_app, target_type=user, target_id=<app_user_id>
 *
 * System announcements use the same table but are kept separate by message_type.
 */
public final class RemoteMessagingService {
    private RemoteMessagingService() {}

    public static ApiResult<Void> send(Context context, String message) {
        Context app = context.getApplicationContext();
        if (!SupabaseConfig.isConfigured(app)) return ApiResult.notConfigured();

        String body = message == null ? "" : message.trim();
        if (body.isEmpty()) return ApiResult.validation(0, "الرسالة فارغة");

        String uid = currentRemoteUserId(app);
        if (uid.isEmpty()) return ApiResult.validation(0, "لا يوجد حساب");

        SupabaseHttp http = new SupabaseHttp(app);
        try {
            JSONObject row = new JSONObject();
            row.put("title", "رسالة من المستخدم");
            row.put("body", body);
            row.put("message_type", "contact");
            row.put("type", "general");
            row.put("target_type", "support");
            row.put("target_id", uid);
            row.put("user_id", uid);
            row.put("is_active", true);
            row.put("created_at", now());

            ApiResult<String> r = http.post("/rest/v1/remote_messages", "[" + row + "]");
            return r.isSuccess()
                    ? ApiResult.success(null)
                    : ApiResult.unknown(r.message == null ? "تعذر إرسال الرسالة" : r.message);
        } catch (Throwable t) {
            return ApiResult.unknown("تعذر إرسال الرسالة");
        }
    }

    /** Existing Contact UI compatibility. */
    public static ApiResult<Void> send(Context context, String type, String message,
                                       String userId, String installationId) {
        return send(context, message);
    }

    public static ApiResult<JSONArray> fetchInboxMessages(Context context) {
        Context app = context.getApplicationContext();
        if (!SupabaseConfig.isConfigured(app)) return ApiResult.notConfigured();

        String uid = currentRemoteUserId(app);
        if (uid.isEmpty()) return ApiResult.success(new JSONArray());

        SupabaseHttp http = new SupabaseHttp(app);
        try {
            ApiResult<String> r = http.get("/rest/v1/remote_messages"
                    + "?target_type=eq.user&target_id=eq." + enc(uid)
                    + "&is_active=eq.true"
                    + "&message_type=in.(in_app,announcement,update,warning,new_feature)"
                    + "&select=id,title,body,message_type,type,target_type,target_id,created_at"
                    + "&order=created_at.asc&limit=200");
            if (!r.isSuccess() || r.data == null) return ApiResult.unknown(r.message);

            JSONArray source = new JSONArray(r.data);
            JSONArray out = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                JSONObject x = source.getJSONObject(i);
                JSONObject m = new JSONObject();
                m.put("id", x.optString("id", ""));
                m.put("title", x.optString("title", "رسالة من مركز التحكم"));
                m.put("body", x.optString("body", ""));
                m.put("created_at", x.optString("created_at", ""));
                m.put("target_type", x.optString("target_type", "user"));
                m.put("message_type", x.optString("message_type", "in_app"));
                m.put("is_active", true);
                m.put("sender_type", "admin");
                m.put("read", false);
                out.put(m);
            }
            return ApiResult.success(out);
        } catch (Throwable t) {
            return ApiResult.unknown("تعذر قراءة رسائل مركز التحكم");
        }
    }

    public static ApiResult<Void> markRead(Context context) {
        // The current Control Center contract has no mandatory read column.
        // Keep this method as a safe compatibility no-op.
        return ApiResult.success(null);
    }

    public static ApiResult<Void> deleteLegacyMessage(Context context, long id) {
        return ApiResult.validation(0, "الرسائل القديمة متوقفة؛ استخدم remote_messages");
    }

    private static String currentRemoteUserId(Context c) {
        String local = AccountAuth.getSessionUserId(c);
        if (local == null || local.isEmpty()) local = AppInfrastructure.getUserId(c);
        return RemoteControlService.toRemoteUserId(local);
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Throwable ignored) { return s == null ? "" : s; }
    }

    private static String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                java.util.Locale.US).format(new java.util.Date());
    }
}
