package com.myplan.app.supabase;

import android.content.Context;

/**
 * Remote backend intentionally disabled.
 * My Plan is local-only.
 */
public final class SupabaseConfig {
    private SupabaseConfig() {}

    public static boolean isEnabled(Context c) { return false; }
    public static void setEnabled(Context c, boolean on) {}
    public static String getUrl(Context c) { return ""; }
    public static String getAnonKey(Context c) { return ""; }
    public static void setUrlOverride(Context c, String url) {}
    public static void setAnonOverride(Context c, String key) {}
    public static boolean isConfigured(Context c) { return false; }
    public static boolean isBuildConfigured() { return false; }
}