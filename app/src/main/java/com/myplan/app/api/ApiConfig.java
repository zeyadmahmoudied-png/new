package com.myplan.app.api;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * API environment configuration. Production is never auto-enabled without explicit base URL.
 * No secrets stored here.
 */
public final class ApiConfig {
    private ApiConfig() {}

    public enum Environment { DEVELOPMENT, STAGING, PRODUCTION }

    private static final String PREFS = "myplan_api_v1";
    private static final String KEY_ENV = "environment";
    private static final String KEY_BASE = "base_url_override";
    private static final String KEY_API_VERSION = "api_version";
    private static final String KEY_ENABLED = "remote_enabled";
    private static final String KEY_LAST_SUCCESS = "last_success_ms";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_REQ = "last_request";

    /** Placeholder only — not a live backend. */
    public static final String DEV_BASE_DEFAULT = "";
    public static final String STAGING_BASE_DEFAULT = "";
    public static final String PROD_BASE_DEFAULT = "";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Environment getEnvironment(Context c) {
        String v = sp(c).getString(KEY_ENV, Environment.DEVELOPMENT.name());
        try { return Environment.valueOf(v); }
        catch (Exception e) { return Environment.DEVELOPMENT; }
    }

    public static void setEnvironment(Context c, Environment env) {
        sp(c).edit().putString(KEY_ENV, env.name()).apply();
    }

    public static String getApiVersionPath(Context c) {
        return sp(c).getString(KEY_API_VERSION, "v1");
    }

    public static void setApiVersionPath(Context c, String v) {
        sp(c).edit().putString(KEY_API_VERSION, v == null || v.isEmpty() ? "v1" : v).apply();
    }

    /** Explicit opt-in; default false until a real base URL is configured. */
    public static boolean isRemoteEnabled(Context c) {
        return sp(c).getBoolean(KEY_ENABLED, false);
    }

    public static void setRemoteEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    public static String getBaseUrlOverride(Context c) {
        return sp(c).getString(KEY_BASE, "");
    }

    public static void setBaseUrlOverride(Context c, String url) {
        sp(c).edit().putString(KEY_BASE, url == null ? "" : url.trim()).apply();
    }

    public static String resolveBaseUrl(Context c) {
        String override = getBaseUrlOverride(c);
        if (override != null && !override.isEmpty()) return stripSlash(override);
        switch (getEnvironment(c)) {
            case STAGING: return stripSlash(STAGING_BASE_DEFAULT);
            case PRODUCTION: return stripSlash(PROD_BASE_DEFAULT);
            default: return stripSlash(DEV_BASE_DEFAULT);
        }
    }

    public static boolean isConfigured(Context c) {
        String base = resolveBaseUrl(c);
        return isRemoteEnabled(c) && base != null && !base.isEmpty();
    }

    /** Production must be HTTPS only. */
    public static boolean isBaseUrlAllowed(Context c, String url) {
        if (url == null || url.isEmpty()) return false;
        String u = url.toLowerCase();
        if (getEnvironment(c) == Environment.PRODUCTION) {
            return u.startsWith("https://");
        }
        return u.startsWith("https://") || u.startsWith("http://localhost") || u.startsWith("http://127.0.0.1");
    }

    public static String endpoint(Context c, String path) {
        String base = resolveBaseUrl(c);
        String ver = getApiVersionPath(c);
        if (path == null) path = "";
        if (path.startsWith("/")) path = path.substring(1);
        return base + "/api/" + ver + "/" + path;
    }

    public static void recordSuccess(Context c, String requestLabel) {
        sp(c).edit()
                .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
                .putString(KEY_LAST_REQ, requestLabel == null ? "" : requestLabel)
                .putString(KEY_LAST_ERROR, "")
                .apply();
    }

    public static void recordError(Context c, String requestLabel, String error) {
        sp(c).edit()
                .putString(KEY_LAST_REQ, requestLabel == null ? "" : requestLabel)
                .putString(KEY_LAST_ERROR, error == null ? "" : error)
                .apply();
    }

    public static long getLastSuccessMs(Context c) { return sp(c).getLong(KEY_LAST_SUCCESS, 0); }
    public static String getLastError(Context c) { return sp(c).getString(KEY_LAST_ERROR, ""); }
    public static String getLastRequest(Context c) { return sp(c).getString(KEY_LAST_REQ, ""); }

    private static String stripSlash(String s) {
        if (s == null) return "";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
