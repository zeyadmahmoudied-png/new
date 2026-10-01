package com.myplan.app.ads;

public final class AdResult<T> {
    public enum Kind {
        SUCCESS,
        LOADING,
        LOADED,
        SHOWN,
        FAILED,
        NO_FILL,
        DISABLED,
        NOT_CONFIGURED,
        NOT_INITIALIZED,
        ERROR
    }

    public final Kind kind;
    public final T data;
    public final String message;

    private AdResult(Kind kind, T data, String message) {
        this.kind = kind;
        this.data = data;
        this.message = message == null ? "" : message;
    }

    public static <T> AdResult<T> notConfigured() {
        return new AdResult<>(Kind.NOT_CONFIGURED, null, "Ads provider not configured");
    }

    public static <T> AdResult<T> notInitialized() {
        return new AdResult<>(Kind.NOT_INITIALIZED, null, "Ads SDK not initialized");
    }

    public static <T> AdResult<T> disabled(String reason) {
        return new AdResult<>(Kind.DISABLED, null, reason);
    }

    public static <T> AdResult<T> failed(String msg) {
        return new AdResult<>(Kind.FAILED, null, msg);
    }

    public static <T> AdResult<T> success(T data) {
        return new AdResult<>(Kind.SUCCESS, data, "OK");
    }
}
