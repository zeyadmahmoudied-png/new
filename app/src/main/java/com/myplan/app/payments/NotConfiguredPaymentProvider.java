package com.myplan.app.payments;

import java.util.Collections;
import java.util.List;

/** Default provider — no SDK, no fake success. */
public final class NotConfiguredPaymentProvider implements PaymentProvider {
    @Override public String providerName() { return "none"; }
    @Override public boolean isConfigured() { return false; }
    @Override public boolean isConnected() { return false; }

    @Override
    public BillingResult<List<ProductInfo>> queryProducts(List<String> productIds) {
        return BillingResult.notConfigured();
    }

    @Override
    public BillingResult<PurchaseState> purchase(String productId) {
        return BillingResult.notConfigured();
    }

    @Override
    public BillingResult<List<SubscriptionState>> restorePurchases() {
        return BillingResult.notConfigured();
    }

    @Override
    public BillingResult<SubscriptionState> getActiveSubscription() {
        return BillingResult.notConfigured();
    }

    @Override public void disconnect() {}
}
