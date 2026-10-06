package com.myplan.app.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Lightweight Supabase Auth session bridge.
 * Keeps the existing local AccountAuth/UI unchanged while giving REST requests
 * a real Supabase user JWT when the same email/password exists in Supabase Auth.
 */
public final class SupabaseAuthSession {
    private static final String PREFS = "myplan_supabase_auth_v1";
    private static final String ACCESS = "access_token";
    private static final String REFRESH = "refresh_token";
    private static final String USER_ID = "user_id";
    private static final String EXPIRES_AT = "expires_at";

    private SupabaseAuthSession() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String accessToken(Context c) {
        return sp(c).getString(ACCESS, "");
    }

    public static String userId(Context c) {
        return sp(c).getString(USER_ID, "");
    }

    public static boolean isSignedIn(Context c) {
        return !accessToken(c).isEmpty() && !userId(c).isEmpty();
    }

    public static void clear(Context c) {
        sp(c).edit().clear().apply();
    }

    /**
     * Best-effort bridge. Local account authentication remains authoritative for the
     * existing UI; Supabase Auth is used when configured so RLS/RPC can see auth.uid().
     */
    public static boolean ensureSignedIn(Context c, String email, String password, boolean signUp, String displayName) {
        if (!SupabaseConfig.isBuildConfigured()) return false;
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) return false;

        if (isSignedIn(c) && !isExpired(c)) return true;

        if (!accessToken(c).isEmpty() && refresh(c)) return true;

        JSONObject result = authRequest(
                c,
                signUp ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password",
                signUp
                        ? signupBody(email, password, displayName)
                        : loginBody(email, password)
        );
        if (result == null) return false;

        String token = result.optString("access_token", "");
        String refresh = result.optString("refresh_token", "");
        JSONObject user = result.optJSONObject("user");
        String uid = user == null ? result.optString("user_id", "") : user.optString("id", "");

        if (token.isEmpty() || uid.isEmpty()) {
            // Email confirmation may be enabled; the account exists but there is no
            // authenticated session yet. Do not break local login.
            return false;
        }

        save(c, token, refresh, uid, result.optLong("expires_in", 3600));
        return true;
    }

    public static boolean refresh(Context c) {
        String rt = sp(c).getString(REFRESH, "");
        if (rt.isEmpty() || !SupabaseConfig.isBuildConfigured()) return false;

        JSONObject result = authRequest(
                c,
                "/auth/v1/token?grant_type=refresh_token",
                new JSONObjectSafe().put("refresh_token", rt).json()
        );
        if (result == null) return false;

        String token = result.optString("access_token", "");
        String nextRefresh = result.optString("refresh_token", rt);
        if (token.isEmpty()) return false;

        String uid = result.optString("user_id", sp(c).getString(USER_ID, ""));
        JSONObject user = result.optJSONObject("user");
        if (user != null) uid = user.optString("id", uid);
        if (uid.isEmpty()) return false;

        save(c, token, nextRefresh, uid, result.optLong("expires_in", 3600));
        return true;
    }

    public static boolean isExpired(Context c) {
        long expiresAt = sp(c).getLong(EXPIRES_AT, 0);
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt - 30_000L;
    }

    private static void save(Context c, String token, String refresh, String uid, long expiresInSec) {
        long expiresAt = System.currentTimeMillis() + Math.max(60L, expiresInSec) * 1000L;
        sp(c).edit()
                .putString(ACCESS, token)
                .putString(REFRESH, refresh == null ? "" : refresh)
                .putString(USER_ID, uid)
                .putLong(EXPIRES_AT, expiresAt)
                .apply();
    }

    private static String loginBody(String email, String password) {
        return new JSONObjectSafe().put("email", email.trim()).put("password", password).json();
    }

    private static String signupBody(String email, String password, String displayName) {
        JSONObjectSafe b = new JSONObjectSafe().put("email", email.trim()).put("password", password);
        if (displayName != null && !displayName.trim().isEmpty()) {
            JSONObject data = new JSONObject();
            try { data.put("display_name", displayName.trim()); } catch (Exception ignored) {}
            b.putObject("data", data);
        }
        return b.json();
    }

    private static JSONObject authRequest(Context c, String path, String body) {
        HttpURLConnection conn = null;
        try {
            String base = SupabaseConfig.getUrl(c);
            String key = SupabaseConfig.getAnonKey(c);
            conn = (HttpURLConnection) new URL(base + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("apikey", key);
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            conn.getOutputStream().write(bytes);
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String raw = readAll(in);
            if (code < 200 || code >= 300 || raw.isEmpty()) return null;
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try (Scanner s = new Scanner(in, "UTF-8").useDelimiter("\A")) {
            return s.hasNext() ? s.next() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static final class JSONObjectSafe {
        private final JSONObject o = new JSONObject();
        JSONObjectSafe put(String key, String value) {
            try { o.put(key, value); } catch (Exception ignored) {}
            return this;
        }
        JSONObjectSafe putObject(String key, JSONObject value) {
            try { o.put(key, value); } catch (Exception ignored) {}
            return this;
        }
        String json() { return o.toString(); }
    }
}
