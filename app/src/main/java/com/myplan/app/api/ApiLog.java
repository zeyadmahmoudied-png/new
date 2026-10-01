package com.myplan.app.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Safe API logs — never stores tokens/passwords/Authorization/body secrets. */
public final class ApiLog {
    private static final String PREFS = "myplan_api_v1";
    private static final String KEY = "api_logs_json";
    private static final int MAX = 80;

    private ApiLog() {}

    public static void append(Context c, String method, String path, int status, long durationMs,
                              String category, String requestId) {
        try {
            SharedPreferences sp = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("ts", System.currentTimeMillis());
            o.put("method", method == null ? "" : method);
            o.put("path", path == null ? "" : path);
            o.put("status", status);
            o.put("durationMs", durationMs);
            o.put("category", category == null ? "" : category);
            if (requestId != null && !requestId.isEmpty()) {
                o.put("requestId", requestId);
            }
            JSONArray next = new JSONArray();
            next.put(o);
            for (int i = 0; i < a.length() && next.length() < MAX; i++) next.put(a.get(i));
            sp.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<String> lines(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String rid = o.optString("requestId", "");
                String ridPart = rid.isEmpty() ? "" : (" · id=" + rid.substring(0, Math.min(8, rid.length())));
                out.add(o.optString("method") + " " + o.optString("path")
                        + " · " + o.optInt("status") + " · " + o.optLong("durationMs") + "ms · "
                        + o.optString("category") + ridPart);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void clear(Context c) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, "[]").apply();
    }
}
