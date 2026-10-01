package com.myplan.app.api;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTPS-capable HTTP client. No fake responses.
 * When backend is not configured, all calls return NOT_CONFIGURED.
 * Tokens never logged. Production HTTP blocked via ApiConfig.
 */
public final class ApiClient {
    private final Context app;
    private final TokenStore tokens;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final RetryPolicy retry;

    public ApiClient(Context c) {
        this(c, new SecureTokenStore(c), 10000, 15000, RetryPolicy.defaults());
    }

    public ApiClient(Context c, TokenStore tokens, int connectMs, int readMs, RetryPolicy retry) {
        this.app = c.getApplicationContext();
        this.tokens = tokens == null ? new SecureTokenStore(c) : tokens;
        this.connectTimeoutMs = connectMs;
        this.readTimeoutMs = readMs;
        this.retry = retry == null ? RetryPolicy.defaults() : retry;
    }

    public TokenStore tokenStore() { return tokens; }

    public ApiResult<String> get(String path) {
        return execute("GET", path, null, true);
    }

    public ApiResult<String> post(String path, String jsonBody) {
        return execute("POST", path, jsonBody, true);
    }

    public ApiResult<String> put(String path, String jsonBody) {
        return execute("PUT", path, jsonBody, true);
    }

    public ApiResult<String> delete(String path) {
        return execute("DELETE", path, null, true);
    }

    public ApiResult<String> health() {
        return execute("GET", "health", null, false);
    }

    private ApiResult<String> execute(String method, String path, String body, boolean auth) {
        if (!ApiConfig.isConfigured(app)) {
            ApiLog.append(app, method, path, 0, 0, "not_configured", null);
            return ApiResult.notConfigured();
        }
        String base = ApiConfig.resolveBaseUrl(app);
        if (!ApiConfig.isBaseUrlAllowed(app, base)) {
            ApiConfig.recordError(app, method + " " + path, "URL not allowed for environment");
            return ApiResult.validation(0, "Base URL غير مسموح في هذه البيئة");
        }

        int attempts = 0;
        ApiResult<String> last = ApiResult.unknown("no attempt");
        while (attempts <= retry.maxRetries) {
            attempts++;
            String requestId = UUID.randomUUID().toString();
            long t0 = System.currentTimeMillis();
            try {
                String urlStr = ApiConfig.endpoint(app, path);
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(connectTimeoutMs);
                conn.setReadTimeout(readTimeoutMs);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("X-Request-Id", requestId);
                if (auth) {
                    String at = tokens.getAccessToken();
                    if (at != null && !at.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + at);
                    }
                }
                if (body != null && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
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
                // Never log Authorization or body secrets
                ApiLog.append(app, method, path, code, dur,
                        code >= 400 ? classifyCategory(code) : "ok", requestId);
                if (code >= 200 && code < 300) {
                    ApiConfig.recordSuccess(app, method + " " + path);
                    return ApiResult.success(resp);
                }
                last = fromHttpUserSafe(code, resp);
                ApiConfig.recordError(app, method + " " + path, last.message);
                if (code == 401) {
                    // Access invalid — status only; refresh is NOT_CONFIGURED without backend
                    if (tokens.getAccessStatus() == TokenStore.TokenStatus.VALID) {
                        // mark soft invalid without wiping refresh until server says so
                    }
                }
                if (!retry.shouldRetry(code, last.kind) || attempts > retry.maxRetries) {
                    return last;
                }
                sleep(retry.backoffMs(attempts));
            } catch (SocketTimeoutException ste) {
                long dur = System.currentTimeMillis() - t0;
                ApiLog.append(app, method, path, 0, dur, "timeout", null);
                last = ApiResult.timeout("انتهت مهلة الاتصال");
                ApiConfig.recordError(app, method + " " + path, last.message);
                if (attempts > retry.maxRetries) return last;
                sleep(retry.backoffMs(attempts));
            } catch (Exception ex) {
                long dur = System.currentTimeMillis() - t0;
                ApiLog.append(app, method, path, 0, dur, "network", null);
                last = ApiResult.network(userSafeNetworkMsg(ex));
                ApiConfig.recordError(app, method + " " + path, last.message);
                if (attempts > retry.maxRetries) return last;
                sleep(retry.backoffMs(attempts));
            }
        }
        return last;
    }

    /** User-facing messages — no raw body / stack / tokens. */
    public static ApiResult<String> fromHttpUserSafe(int code, String bodyIgnored) {
        if (code == 401) return ApiResult.auth(401, "Authentication Required");
        if (code == 403) return ApiResult.auth(403, "Access Denied");
        if (code == 404) return ApiResult.validation(404, "Not Found");
        if (code == 409) return ApiResult.validation(409, "Conflict");
        if (code == 422) return ApiResult.validation(422, "Validation Error");
        if (code == 429) return ApiResult.server(429, "Too Many Requests");
        if (code == 500 || code == 502 || code == 503 || code == 504) {
            return ApiResult.server(code, "Server Unavailable");
        }
        if (code >= 500) return ApiResult.server(code, "Server Unavailable");
        if (code >= 400) return ApiResult.validation(code, "Request Error");
        return ApiResult.unknown("HTTP " + code);
    }

    private static String classifyCategory(int code) {
        if (code == 401 || code == 403) return "auth";
        if (code == 429) return "rate_limit";
        if (code >= 500) return "server";
        if (code >= 400) return "validation";
        return "http_error";
    }

    private static String userSafeNetworkMsg(Exception ex) {
        return "خطأ شبكة";
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public static final class RetryPolicy {
        public final int maxRetries;
        public final long baseBackoffMs;

        public RetryPolicy(int maxRetries, long baseBackoffMs) {
            this.maxRetries = Math.max(0, Math.min(maxRetries, 5)); // hard cap
            this.baseBackoffMs = Math.max(100, baseBackoffMs);
        }

        public static RetryPolicy defaults() {
            return new RetryPolicy(2, 400);
        }

        public boolean shouldRetry(int httpCode, ApiResult.Kind kind) {
            if (kind == ApiResult.Kind.TIMEOUT || kind == ApiResult.Kind.NETWORK_ERROR) return true;
            if (httpCode == 429) return true; // rate limit — limited retries with backoff
            if (httpCode >= 500 && httpCode <= 599) return true;
            // Never retry client/auth/validation
            if (httpCode == 400 || httpCode == 401 || httpCode == 403
                    || httpCode == 404 || httpCode == 409 || httpCode == 422) return false;
            return false;
        }

        /** Exponential backoff with small jitter bound. */
        public long backoffMs(int attempt) {
            long exp = baseBackoffMs * (1L << Math.min(attempt, 4));
            return Math.min(exp, 8000L);
        }
    }
}
