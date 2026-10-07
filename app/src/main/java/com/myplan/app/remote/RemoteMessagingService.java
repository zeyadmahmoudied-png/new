package com.myplan.app.remote;

import android.content.Context;

import com.myplan.app.AccountAuth;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.api.ApiResult;
import com.myplan.app.supabase.SupabaseConfig;
import com.myplan.app.supabase.SupabaseHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Clean two-way Support inbox.
 *
 * User -> Control Center and Control Center -> User are conversation messages.
 * System announcements are deliberately NOT stored here.
 */
public final class RemoteMessagingService {
    private RemoteMessagingService() {}

    public static ApiResult<JSONArray> fetchInboxMessages(Context context) {
        Context app = context.getApplicationContext();
        if (!SupabaseConfig.isConfigured(app)) return ApiResult.notConfigured();

        SupabaseHttp http = new SupabaseHttp(app);
        String uid = remoteUserId(AccountAuth.getSessionUserId(app));
        if (uid.isEmpty()) uid = remoteUserId(AppInfrastructure.getUserId(app));
        if (uid.isEmpty()) return ApiResult.success(new JSONArray());

        try {
            ApiResult<String> conv = http.get("/rest/v1/conversations?user_id=eq."
                    + enc(uid) + "&select=id,status,created_at,updated_at&limit=1");
            String conversationId = "";
            if (conv.isSuccess() && conv.data != null) {
                JSONArray a = new JSONArray(conv.data);
                if (a.length() > 0) conversationId = a.getJSONObject(0).optString("id", "");
            }

            if (conversationId.isEmpty()) return ApiResult.success(new JSONArray());

            ApiResult<String> rows = http.get("/rest/v1/conversation_messages?conversation_id=eq."
                    + enc(conversationId)
                    + "&select=id,conversation_id,sender_type,body,created_at,read_at"
                    + "&order=created_at.asc&limit=200");
            if (!rows.isSuccess() || rows.data == null) return ApiResult.unknown(rows.message);

            JSONArray source = new JSONArray(rows.data);
            JSONArray out = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                JSONObject x = source.getJSONObject(i);
                JSONObject m = new JSONObject();
                m.put("id", x.optString("id", ""));
                m.put("title", "محادثة الدعم");
                String sender = x.optString("sender_type", "user");
                m.put("body", ("admin".equalsIgnoreCase(sender) ? "الدعم: " : "أنت: ")
                        + x.optString("body", ""));
                m.put("created_at", x.optString("created_at", ""));
                m.put("target_type", "conversation");
                m.put("message_type", "support");
                m.put("is_active", true);
                m.put("sender_type", sender);
                m.put("read", !x.optString("read_at", "").isEmpty());
                out.put(m);
            }
            return ApiResult.success(out);
        } catch (Throwable t) {
            return ApiResult.unknown("تعذر قراءة محادثة الدعم");
        }
    }

    public static ApiResult<Void> send(Context context, String message) {
        Context app = context.getApplicationContext();
        if (!SupabaseConfig.isConfigured(app)) return ApiResult.notConfigured();

        String body = message == null ? "" : message.trim();
        if (body.isEmpty()) return ApiResult.validation(0, "الرسالة فارغة");

        String uid = remoteUserId(AccountAuth.getSessionUserId(app));
        if (uid.isEmpty()) uid = remoteUserId(AppInfrastructure.getUserId(app));
        if (uid.isEmpty()) return ApiResult.validation(0, "لا يوجد حساب");

        SupabaseHttp http = new SupabaseHttp(app);
        try {
            String conversationId = findOrCreateConversation(http, uid);
            if (conversationId.isEmpty()) return ApiResult.unknown("تعذر إنشاء المحادثة");

            JSONObject row = new JSONObject();
            row.put("conversation_id", conversationId);
            row.put("sender_type", "user");
            row.put("sender_user_id", uid);
            row.put("body", body);
            ApiResult<String> r = http.post("/rest/v1/conversation_messages",
                    "[" + row + "]");
            return r.isSuccess() ? ApiResult.success(null) : ApiResult.unknown(r.message);
        } catch (Throwable t) {
            return ApiResult.unknown("تعذر إرسال الرسالة");
        }
    }

    public static ApiResult<Void> markRead(Context context) {
        Context app = context.getApplicationContext();
        if (!SupabaseConfig.isConfigured(app)) return ApiResult.notConfigured();
        String uid = remoteUserId(AccountAuth.getSessionUserId(app));
        if (uid.isEmpty()) return ApiResult.success(null);
        SupabaseHttp http = new SupabaseHttp(app);
        try {
            ApiResult<String> conv = http.get("/rest/v1/conversations?user_id=eq."
                    + enc(uid) + "&select=id&limit=1");
            if (!conv.isSuccess() || conv.data == null) return ApiResult.unknown(conv.message);
            JSONArray a = new JSONArray(conv.data);
            if (a.length() == 0) return ApiResult.success(null);
            String cid = a.getJSONObject(0).optString("id", "");
            if (cid.isEmpty()) return ApiResult.success(null);
            JSONObject patch = new JSONObject().put("read_at", now());
            ApiResult<String> r = http.patch("/rest/v1/conversation_messages?conversation_id=eq."
                    + enc(cid) + "&sender_type=eq.admin&read_at=is.null", patch.toString());
            return r.isSuccess() ? ApiResult.success(null) : ApiResult.unknown(r.message);
        } catch (Throwable t) {
            return ApiResult.unknown("تعذر تحديث حالة القراءة");
        }
    }

    private static String findOrCreateConversation(SupabaseHttp http, String uid) {
        try {
            ApiResult<String> q = http.get("/rest/v1/conversations?user_id=eq."
                    + enc(uid) + "&select=id&limit=1");
            if (q.isSuccess() && q.data != null) {
                JSONArray a = new JSONArray(q.data);
                if (a.length() > 0) return a.getJSONObject(0).optString("id", "");
            }

            String id = UUID.randomUUID().toString();
            JSONObject row = new JSONObject();
            row.put("id", id);
            row.put("user_id", uid);
            row.put("status", "open");
            ApiResult<String> ins = http.post("/rest/v1/conversations", "[" + row + "]");
            return ins.isSuccess() ? id : "";
        } catch (Throwable t) {
            return "";
        }
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
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                java.util.Locale.US).format(new java.util.Date());
    }
}
