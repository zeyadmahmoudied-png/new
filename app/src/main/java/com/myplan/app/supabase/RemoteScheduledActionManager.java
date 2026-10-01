package com.myplan.app.supabase;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.myplan.app.api.ApiResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * طبقة إدارية منفصلة تمامًا عن Planner / Study Scheduler.
 * تقرأ cc_scheduled_actions وتتجاهل أي action غير معروف بأمان.
 */
public final class RemoteScheduledActionManager {
    private RemoteScheduledActionManager() {}

    private static final String TAG = "RemoteScheduledAction";
    private static final String PREFS = "myplan_remote_actions_v1";

    public static void syncAndApply(Context c) {
        if (!SupabaseConfig.isConfigured(c)) return;
        try {
            SupabaseRepository repo = new SupabaseRepository(c);
            ApiResult<String> r = repo.httpGet(
                    "/rest/v1/cc_scheduled_actions?select=*&limit=50");
            if (r == null || !r.isSuccess() || r.data == null) {
                Log.d(TAG, "REMOTE_SCHEDULED_FETCH_FAILED");
                return;
            }
            JSONArray arr = new JSONArray(r.data);
            SharedPreferences sp = c.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", String.valueOf(o.opt("id")));
                String type = o.optString("action_type", o.optString("type", "")).trim();
                if (id.isEmpty()) continue;
                if (sp.getBoolean("done_" + id, false)) continue;
                // لا ننفّذ أفعال إدارية حساسة من العميل — تسجيل فقط
                Log.d(TAG, "REMOTE_SCHEDULED_SEEN id=" + id + " type=" + type);
                // أفعال معروفة آمنة فقط (لا تمس Planner)
                if ("noop".equalsIgnoreCase(type) || type.isEmpty()) {
                    sp.edit().putBoolean("done_" + id, true).apply();
                }
                // أي نوع آخر: ignore safely
            }
        } catch (Exception e) {
            Log.d(TAG, "REMOTE_SCHEDULED_ERROR");
        }
    }
}
