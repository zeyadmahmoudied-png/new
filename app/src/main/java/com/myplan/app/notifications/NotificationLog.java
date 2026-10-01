package com.myplan.app.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class NotificationLog {
    private static final String PREFS = "myplan_notifications_v1";
    private static final String KEY = "notif_logs";
    private static final int MAX = 60;

    private NotificationLog() {}

    public static void append(Context c, String action, String status, String message) {
        try {
            SharedPreferences sp = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("ts", System.currentTimeMillis());
            o.put("action", action == null ? "" : action);
            o.put("status", status == null ? "" : status);
            String msg = message == null ? "" : message;
            if (msg.length() > 120) msg = msg.substring(0, 120);
            o.put("message", msg);
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
                out.add(o.optString("action") + " · " + o.optString("status") + " · " + o.optString("message"));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void clear(Context c) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, "[]").apply();
    }
}
