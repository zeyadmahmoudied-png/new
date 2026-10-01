package com.myplan.app.payments;

/** Typed result for billing operations. Never pretends a real purchase succeeded. */
public final class BillingResult<T> {
    public enum Kind {
        SUCCESS,
        PENDING,
        CANCELLED,
        ALREADY_OWNED,
        INVALID_PRODUCT,
        NETWORK_ERROR,
        BILLING_UNAVAILABLE,
        NOT_CONFIGURED,
        NOT_CONNECTED,
        ERROR
    }

    public final Kind kind;
    public final T data;
    public final String message;
    public final String errorCode;

    private BillingResult(Kind kind, T data, String message, String errorCode) {
        this.kind = kind;
        this.data = data;
        this.message = message == null ? "" : message;
        this.errorCode = errorCode == null ? "" : errorCode;
    }

    public boolean isSuccess() { return kind == Kind.SUCCESS; }

    public static <T> BillingResult<T> success(T data) {
        return new BillingResult<>(Kind.SUCCESS, data, "OK", "ok");
    }

    public static <T> BillingResult<T> notConfigured() {
        return new BillingResult<>(Kind.NOT_CONFIGURED, null, "Billing provider not configured", "not_configured");
    }

    public static <T> BillingResult<T> notConnected() {
        return new BillingResult<>(Kind.NOT_CONNECTED, null, "Billing provider not connected", "not_connected");
    }

    public static <T> BillingResult<T> pending(String msg) {
        return new BillingResult<>(Kind.PENDING, null, msg, "pending");
    }

    public static <T> BillingResult<T> cancelled() {
        return new BillingResult<>(Kind.CANCELLED, null, "User cancelled", "cancelled");
    }

    public static <T> BillingResult<T> alreadyOwned() {
        return new BillingResult<>(Kind.ALREADY_OWNED, null, "Already owned", "already_owned");
    }

    public static <T> BillingResult<T> invalidProduct(String msg) {
        return new BillingResult<>(Kind.INVALID_PRODUCT, null, msg, "invalid_product");
    }

    public static <T> BillingResult<T> network(String msg) {
        return new BillingResult<>(Kind.NETWORK_ERROR, null, msg, "network");
    }

    public static <T> BillingResult<T> unavailable(String msg) {
        return new BillingResult<>(Kind.BILLING_UNAVAILABLE, null, msg, "billing_unavailable");
    }

    public static <T> BillingResult<T> error(String msg) {
        return new BillingResult<>(Kind.ERROR, null, msg, "error");
    }
}
