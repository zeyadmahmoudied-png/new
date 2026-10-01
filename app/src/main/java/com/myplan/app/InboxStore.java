package com.myplan.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** تخزين محلي لحالة قراءة رسائل Control Center. */
public final class InboxStore {
    private InboxStore() {}

    private static final String PREFS = "myplan_inbox_v1";
    private static final String KEY_CACHE = "messages_cache";
    private static final String KEY_READ = "read_ids";

    public static final class Msg {
        public long id;
        public String title = "";
        public String body = "";
        public String createdAt = "";
        public boolean read;
        public String targetType = "";
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> readIds(Context c) {
        Set<String> out = new HashSet<>();
        String raw = sp(c).getString(KEY_READ, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i, ""));
        } catch (Exception ignored) {}
        return out;
    }

    public static void markRead(Context c, long id) {
        Set<String> s = readIds(c);
        s.add(String.valueOf(id));
        JSONArray a = new JSONArray();
        for (String x : s) if (x != null && !x.isEmpty()) a.put(x);
        sp(c).edit().putString(KEY_READ, a.toString()).apply();
    }

    public static void saveCache(Context c, JSONArray arr) {
        sp(c).edit().putString(KEY_CACHE, arr == null ? "[]" : arr.toString()).apply();
    }

    public static List<Msg> list(Context c) {
        List<Msg> list = new ArrayList<>();
        Set<String> read = readIds(c);
        try {
            JSONArray arr = new JSONArray(sp(c).getString(KEY_CACHE, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Msg m = new Msg();
                m.id = o.optLong("id", 0);
                m.title = o.optString("title", "");
                m.body = o.optString("body", "");
                m.createdAt = o.optString("created_at", "");
                m.targetType = o.optString("target_type", "");
                m.read = read.contains(String.valueOf(m.id));
                list.add(m);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static int unreadCount(Context c) {
        int n = 0;
        for (Msg m : list(c)) if (!m.read) n++;
        return n;
    }

    /** إزالة رسالة من الـcache المحلي بعد حذف ناجح من الخادم. */
    public static void removeFromCache(Context c, long id) {
        try {
            JSONArray arr = new JSONArray(sp(c).getString(KEY_CACHE, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("id", 0) != id) next.put(o);
            }
            sp(c).edit().putString(KEY_CACHE, next.toString()).apply();
        } catch (Exception ignored) {}
    }
}
