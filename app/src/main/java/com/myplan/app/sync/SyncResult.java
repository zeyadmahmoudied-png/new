package com.myplan.app.sync;

/** Typed result for sync operations. Never pretends SUCCESS without a real backend. */
public final class SyncResult<T> {
    public enum Kind {
        SUCCESS,
        PARTIAL_SUCCESS,
        IDLE,
        SYNCING,
        CONFLICT,
        AUTH_REQUIRED,
        NETWORK_ERROR,
        SERVER_ERROR,
        CANCELLED,
        NOT_CONFIGURED,
        NOT_CONNECTED,
        UNKNOWN_ERROR
    }

    public final Kind kind;
    public final T data;
    public final String message;

    private SyncResult(Kind kind, T data, String message) {
        this.kind = kind;
        this.data = data;
        this.message = message == null ? "" : message;
    }

    public boolean isSuccess() { return kind == Kind.SUCCESS || kind == Kind.PARTIAL_SUCCESS; }

    public static <T> SyncResult<T> notConfigured() {
        return new SyncResult<>(Kind.NOT_CONFIGURED, null, "Cloud Sync backend not configured");
    }

    public static <T> SyncResult<T> notConnected() {
        return new SyncResult<>(Kind.NOT_CONNECTED, null, "Cloud Sync not connected");
    }

    public static <T> SyncResult<T> authRequired() {
        return new SyncResult<>(Kind.AUTH_REQUIRED, null, "Authentication required for sync");
    }

    public static <T> SyncResult<T> idle() {
        return new SyncResult<>(Kind.IDLE, null, "Idle");
    }

    public static <T> SyncResult<T> success(T data) {
        return new SyncResult<>(Kind.SUCCESS, data, "OK");
    }

    public static <T> SyncResult<T> network(String msg) {
        return new SyncResult<>(Kind.NETWORK_ERROR, null, msg);
    }

    public static <T> SyncResult<T> error(String msg) {
        return new SyncResult<>(Kind.UNKNOWN_ERROR, null, msg);
    }
}
