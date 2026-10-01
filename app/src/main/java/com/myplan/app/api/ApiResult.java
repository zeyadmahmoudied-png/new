package com.myplan.app.api;

/** Typed result for all remote operations. Never pretend success without a real backend. */
public final class ApiResult<T> {
    public enum Kind {
        SUCCESS,
        NETWORK_ERROR,
        AUTH_ERROR,
        SERVER_ERROR,
        VALIDATION_ERROR,
        TIMEOUT,
        NOT_CONFIGURED,
        UNKNOWN_ERROR
    }

    public final Kind kind;
    public final T data;
    public final int httpCode;
    public final String message;
    public final String errorCategory;

    private ApiResult(Kind kind, T data, int httpCode, String message, String errorCategory) {
        this.kind = kind;
        this.data = data;
        this.httpCode = httpCode;
        this.message = message == null ? "" : message;
        this.errorCategory = errorCategory == null ? "" : errorCategory;
    }

    public boolean isSuccess() { return kind == Kind.SUCCESS; }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(Kind.SUCCESS, data, 200, "OK", "success");
    }

    public static <T> ApiResult<T> notConfigured() {
        return new ApiResult<>(Kind.NOT_CONFIGURED, null, 0, "Backend Not Configured", "not_configured");
    }

    public static <T> ApiResult<T> network(String msg) {
        return new ApiResult<>(Kind.NETWORK_ERROR, null, 0, msg, "network");
    }

    public static <T> ApiResult<T> timeout(String msg) {
        return new ApiResult<>(Kind.TIMEOUT, null, 0, msg, "timeout");
    }

    public static <T> ApiResult<T> auth(int code, String msg) {
        return new ApiResult<>(Kind.AUTH_ERROR, null, code, msg, "auth");
    }

    public static <T> ApiResult<T> validation(int code, String msg) {
        return new ApiResult<>(Kind.VALIDATION_ERROR, null, code, msg, "validation");
    }

    public static <T> ApiResult<T> server(int code, String msg) {
        return new ApiResult<>(Kind.SERVER_ERROR, null, code, msg, "server");
    }

    public static <T> ApiResult<T> unknown(String msg) {
        return new ApiResult<>(Kind.UNKNOWN_ERROR, null, 0, msg, "unknown");
    }

    public static <T> ApiResult<T> fromHttp(int code, String bodyMsg) {
        if (code >= 200 && code < 300) return success(null);
        if (code == 401 || code == 403) return auth(code, bodyMsg);
        if (code == 404 || code == 409 || code == 422) return validation(code, bodyMsg);
        if (code >= 500) return server(code, bodyMsg);
        return unknown("HTTP " + code + ": " + bodyMsg);
    }
}
