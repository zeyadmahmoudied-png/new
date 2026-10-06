package com.myplan.app.supabase;

import android.content.Context;

import com.myplan.app.api.ApiLog;
import com.myplan.app.api.ApiResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** عميل HTTP خفيف لـ PostgREST / REST — بدون SDK. */
public final class SupabaseHttp {
    private final Context app;

    public SupabaseHttp(Context c) {
        this.app = c.getApplicationContext();
    }

    public ApiResult<String> get(String pathAndQuery) {
        return exec("GET", pathAndQuery, null, null);
    }

    public ApiResult<String> post(String pathAndQuery, String jsonBody) {
        return exec("POST", pathAndQuery, jsonBody, null);
    }

    public ApiResult<String> postPrefer(String pathAndQuery, String jsonBody, String prefer) {
        return exec("POST", pathAndQuery, jsonBody, prefer);
    }

    public ApiResult<String> patch(String pathAndQuery, String jsonBody) {
        return exec("PATCH", pathAndQuery, jsonBody, null);
    }

    public ApiResult<String> delete(String pathAndQuery) {
        return exec("DELETE", pathAndQuery, null, null);
    }

    private ApiResult<String> exec(String method, String path, String body, String preferHeader) {
        if (!SupabaseConfig.isConfigured(app)) {
            return ApiResult.notConfigured();
        }
        String base = SupabaseConfig.getUrl(app);
        String key = SupabaseConfig.getAnonKey(app);
        if (key.toLowerCase().contains("service_role")) {
            return ApiResult.validation(0, "Service Role غير مسموح في التطبيق");
        }
        String urlStr = base + (path.startsWith("/") ? path : "/" + path);
        long t0 = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("apikey", key);
            String sessionToken = SupabaseAuthSession.accessToken(app);\n            conn.setRequestProperty("Authorization", "Bearer " + (sessionToken.isEmpty() ? key : sessionToken));
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if ("POST".equals(method) || "PATCH".equals(method)) {
                String pref = (preferHeader != null && !preferHeader.isEmpty())
                        ? preferHeader : "return=minimal";
                conn.setRequestProperty("Prefer", pref);
            }
            if (body != null && ("POST".equals(method) || "PATCH".equals(method) || "PUT".equals(method))) {
                conn.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = readAll(stream);
            long dur = System.currentTimeMillis() - t0;
            ApiLog.append(app, method, path, code, dur, code >= 400 ? "error" : "ok", null);
            if (code >= 200 && code < 300) return ApiResult.success(resp == null ? "" : resp);
            if (code == 401 || code == 403) return ApiResult.auth(code, "غير مصرح (RLS/Key)");
            if (code == 404) return ApiResult.validation(code, "المسار أو الجدول غير موجود");
            if (code >= 500) return ApiResult.server(code, "خطأ خادم");
            return ApiResult.unknown("HTTP " + code);
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - t0;
            ApiLog.append(app, method, path, 0, dur, "network", null);
            return ApiResult.network("تعذّر الاتصال بـ Supabase");
        }
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try (Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
