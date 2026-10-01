package com.myplan.app.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import com.myplan.app.BuildConfig;

/**
 * إعدادات Supabase من BuildConfig / SharedPreferences.
 * Anon/Publishable فقط — لا Service Role أبدًا.
 */
public final class SupabaseConfig {
    private SupabaseConfig() {}

    private static final String PREFS = "myplan_supabase_v1";
    private static final String KEY_URL = "url_override";
    private static final String KEY_ANON = "anon_override";
    private static final String KEY_ENABLED = "enabled";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        if (sp(c).contains(KEY_ENABLED)) return sp(c).getBoolean(KEY_ENABLED, false);
        return isBuildConfigured();
    }

    public static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    public static String getUrl(Context c) {
        String o = sp(c).getString(KEY_URL, "");
        if (o != null && !o.trim().isEmpty()) return stripSlash(o.trim());
        String b = BuildConfig.SUPABASE_URL;
        return b == null ? "" : stripSlash(b.trim());
    }

    public static String getAnonKey(Context c) {
        String o = sp(c).getString(KEY_ANON, "");
        if (o != null && !o.trim().isEmpty()) return o.trim();
        String b = BuildConfig.SUPABASE_ANON_KEY;
        return b == null ? "" : b.trim();
    }

    public static void setUrlOverride(Context c, String url) {
        sp(c).edit().putString(KEY_URL, url == null ? "" : url.trim()).apply();
    }

    public static void setAnonOverride(Context c, String key) {
        // لا تخزّن service_role — رفض بسيط بالاسم
        if (key != null && key.toLowerCase().contains("service_role")) return;
        sp(c).edit().putString(KEY_ANON, key == null ? "" : key.trim()).apply();
    }

    public static boolean isConfigured(Context c) {
        if (!isEnabled(c)) return false;
        String u = getUrl(c);
        String k = getAnonKey(c);
        return u.startsWith("https://") && k.length() > 20;
    }

    public static boolean isBuildConfigured() {
        try {
            String u = BuildConfig.SUPABASE_URL;
            String k = BuildConfig.SUPABASE_ANON_KEY;
            return u != null && u.startsWith("https://") && k != null && k.length() > 20;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String stripSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
