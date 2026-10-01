package com.myplan.app.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * آخر Remote Config صالح محليًا — offline fallback.
 * لا يمس بيانات Planner.
 */
public final class RemoteControlCache {
    private RemoteControlCache() {}

    private static final String PREFS = "myplan_remote_control_v1";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveSnapshot(Context c, JSONObject snapshot) {
        if (snapshot == null) return;
        SharedPreferences.Editor ed = sp(c).edit()
                .putString("snapshot_json", snapshot.toString())
                .putLong("updated_at", System.currentTimeMillis())
                .putBoolean("premium_active", snapshot.optBoolean("premium_active", false));
        try {
            Object fk = snapshot.opt("premium_feature_keys");
            if (fk instanceof org.json.JSONArray) {
                ed.putString("premium_feature_keys", fk.toString());
            } else if (fk != null) {
                ed.putString("premium_feature_keys", String.valueOf(fk));
            } else {
                ed.putString("premium_feature_keys", "[]");
            }
        } catch (Exception e) {
            ed.putString("premium_feature_keys", "[]");
        }
        ed.apply();
    }

    public static JSONObject loadSnapshot(Context c) {
        try {
            String j = sp(c).getString("snapshot_json", null);
            if (j == null || j.isEmpty()) return null;
            return new JSONObject(j);
        } catch (Exception e) {
            return null;
        }
    }

    public static long updatedAt(Context c) {
        return sp(c).getLong("updated_at", 0);
    }

    public static boolean maintenance(Context c) {
        return optBool(c, "maintenance", false);
    }

    public static boolean killSwitch(Context c) {
        return optBool(c, "kill_switch", false);
    }

    public static boolean forceUpdate(Context c) {
        return optBool(c, "force_update", false);
    }

    public static String minVersion(Context c) {
        return optStr(c, "minimum_version", "");
    }

    public static String latestVersion(Context c) {
        return optStr(c, "latest_version", "");
    }

    public static String apkUrl(Context c) {
        return optStr(c, "apk_url", "");
    }

    public static String updateMessage(Context c) {
        return optStr(c, "update_message", "");
    }

    public static String releaseNotes(Context c) {
        return optStr(c, "release_notes", "");
    }

    public static boolean flag(Context c, String key, boolean def) {
        JSONObject o = loadSnapshot(c);
        if (o == null) return def;
        try {
            JSONObject flags = o.optJSONObject("feature_flags");
            if (flags != null && flags.has(key)) return flags.optBoolean(key, def);
            if (o.has(key)) return o.optBoolean(key, def);
        } catch (Exception ignored) {}
        return def;
    }

    public static boolean remotePremiumActive(Context c) {
        return optBool(c, "premium_active", false);
    }

    private static boolean optBool(Context c, String key, boolean def) {
        JSONObject o = loadSnapshot(c);
        if (o == null) return def;
        return o.optBoolean(key, def);
    }

    private static String optStr(Context c, String key, String def) {
        JSONObject o = loadSnapshot(c);
        if (o == null) return def;
        String v = o.optString(key, def);
        return v == null ? def : v;
    }
}
