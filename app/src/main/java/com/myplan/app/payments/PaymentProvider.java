package com.myplan.app.payments;

import java.util.List;

/**
 * Abstraction over a billing SDK (e.g. Google Play Billing later).
 * Current production wiring must remain NOT_CONFIGURED until a real SDK is integrated.
 */
public interface PaymentProvider {
    String providerName();
    boolean isConfigured();
    boolean isConnected();
    BillingResult<List<ProductInfo>> queryProducts(List<String> productIds);
    BillingResult<PurchaseState> purchase(String productId);
    BillingResult<List<SubscriptionState>> restorePurchases();
    BillingResult<SubscriptionState> getActiveSubscription();
    void disconnect();
}
