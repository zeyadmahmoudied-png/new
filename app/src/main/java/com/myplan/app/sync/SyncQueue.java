package com.myplan.app.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local pending-ops queue. Operations are recorded locally only.
 * Nothing is uploaded while backend is NOT_CONFIGURED.
 */
public final class SyncQueue {
    private static final String PREFS = "myplan_sync_v1";
    private static final String KEY = "pending_ops";
    private static final int MAX = 200;

    private final Context app;

    public SyncQueue(Context c) {
        this.app = c.getApplicationContext();
    }

    public void enqueue(SyncItem item) {
        if (item == null) return;
        try {
            SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("op", item.operation == null ? "UPDATE" : item.operation);
            o.put("type", item.entityType == null ? "" : item.entityType.name());
            o.put("id", item.stableId == null ? "" : item.stableId);
            o.put("ts", item.enqueuedAtMs > 0 ? item.enqueuedAtMs : System.currentTimeMillis());
            // Do not store passwords/secrets; payload optional and local-only
            if (item.payloadJson != null && item.payloadJson.length() < 4000) {
                o.put("payload", item.payloadJson);
            }
            JSONArray next = new JSONArray();
            next.put(o);
            for (int i = 0; i < a.length() && next.length() < MAX; i++) next.put(a.get(i));
            sp.edit().putString(KEY, next.toString()).apply();
            SyncLog.append(app, "enqueue", item.operation + " " + item.entityType, "local_only");
        } catch (Exception ignored) {}
    }

    public int pendingCount() {
        try {
            JSONArray a = new JSONArray(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
            return a.length();
        } catch (Exception e) {
            return 0;
        }
    }

    public List<String> pendingSummaries() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
            for (int i = 0; i < a.length() && i < 30; i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(o.optString("op") + " · " + o.optString("type") + " · " + o.optString("id"));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void clear() {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, "[]").apply();
    }
}
