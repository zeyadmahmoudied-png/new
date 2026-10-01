package com.myplan.app.payments;

import android.content.Context;

import java.util.Arrays;
import java.util.List;

/**
 * App-facing subscription API. Never grants Premium from NOT_CONFIGURED results.
 * Developer Test Premium remains independent in AppInfrastructure.
 */
public final class SubscriptionRepository {
    private final Context app;
    private final PaymentProvider provider;

    public SubscriptionRepository(Context c) {
        this(c, PaymentLayer.provider(c));
    }

    public SubscriptionRepository(Context c, PaymentProvider provider) {
        this.app = c.getApplicationContext();
        this.provider = provider == null ? new NotConfiguredPaymentProvider() : provider;
    }

    public PaymentProvider provider() { return provider; }

    /** Placeholder catalog — not real Play Console products until configured. */
    public List<ProductInfo> catalogPlaceholders() {
        return Arrays.asList(
                ProductInfo.placeholder("premium_monthly", "Premium شهري", "monthly"),
                ProductInfo.placeholder("premium_yearly", "Premium سنوي", "yearly")
        );
    }

    public BillingResult<List<ProductInfo>> queryProducts() {
        BillingResult<List<ProductInfo>> r = provider.queryProducts(
                Arrays.asList("premium_monthly", "premium_yearly"));
        PaymentLog.append(app, "queryProducts", r.kind.name(), r.message);
        return r;
    }

    public BillingResult<PurchaseState> purchase(String productId) {
        if (productId == null || productId.isEmpty()) {
            return BillingResult.invalidProduct("productId empty");
        }
        BillingResult<PurchaseState> r = provider.purchase(productId);
        PaymentLog.append(app, "purchase:" + productId, r.kind.name(), r.message);
        // IMPORTANT: do not call AppInfrastructure.setPremium* on NOT_CONFIGURED / failure
        return r;
    }

    public BillingResult<List<SubscriptionState>> restorePurchases() {
        BillingResult<List<SubscriptionState>> r = provider.restorePurchases();
        PaymentLog.append(app, "restore", r.kind.name(), r.message);
        return r;
    }

    public BillingResult<SubscriptionState> activeSubscription() {
        return provider.getActiveSubscription();
    }

    public String statusLabel() {
        if (!provider.isConfigured()) return "NOT_CONFIGURED";
        if (!provider.isConnected()) return "NOT_CONNECTED";
        return "CONFIGURED";
    }
}
