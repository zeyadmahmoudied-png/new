package com.myplan.app.notifications;

public final class NotificationResult<T> {
    public enum Kind {
        SUCCESS,
        SCHEDULED_LOCAL,
        SENT,
        FAILED,
        CANCELLED,
        NOT_CONFIGURED,
        NOT_CONNECTED,
        PERMISSION_DENIED,
        ERROR
    }

    public final Kind kind;
    public final T data;
    public final String message;

    private NotificationResult(Kind kind, T data, String message) {
        this.kind = kind;
        this.data = data;
        this.message = message == null ? "" : message;
    }

    public static <T> NotificationResult<T> notConfigured() {
        return new NotificationResult<>(Kind.NOT_CONFIGURED, null, "Push provider not configured");
    }

    public static <T> NotificationResult<T> scheduledLocal() {
        return new NotificationResult<>(Kind.SCHEDULED_LOCAL, null, "Local schedule path");
    }

    public static <T> NotificationResult<T> permissionDenied() {
        return new NotificationResult<>(Kind.PERMISSION_DENIED, null, "Notification permission denied");
    }

    public static <T> NotificationResult<T> success(T data) {
        return new NotificationResult<>(Kind.SUCCESS, data, "OK");
    }
}
