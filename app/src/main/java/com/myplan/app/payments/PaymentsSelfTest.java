package com.myplan.app.payments;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Offline self-tests — no fake SUCCESS purchase. */
public final class PaymentsSelfTest {
    private PaymentsSelfTest() {}

    public static final class Case {
        public final String name;
        public final boolean pass;
        public final String detail;
        Case(String n, boolean p, String d) { name = n; pass = p; detail = d; }
    }

    public static List<Case> run(Context c) {
        List<Case> out = new ArrayList<>();
        SubscriptionRepository repo = PaymentLayer.subscriptions(c);
        PaymentProvider p = repo.provider();

        out.add(new Case("provider_not_configured", !p.isConfigured(), p.providerName()));
        out.add(new Case("provider_not_connected", !p.isConnected(), "expected disconnected"));

        BillingResult<?> q = repo.queryProducts();
        out.add(new Case("query_not_configured", q.kind == BillingResult.Kind.NOT_CONFIGURED, q.kind.name()));

        BillingResult<?> buy = repo.purchase("premium_monthly");
        out.add(new Case("purchase_not_configured", buy.kind == BillingResult.Kind.NOT_CONFIGURED, buy.kind.name()));

        BillingResult<?> empty = repo.purchase("");
        out.add(new Case("empty_product_invalid_or_nc",
                empty.kind == BillingResult.Kind.INVALID_PRODUCT || empty.kind == BillingResult.Kind.NOT_CONFIGURED,
                empty.kind.name()));

        BillingResult<?> restore = repo.restorePurchases();
        out.add(new Case("restore_not_configured", restore.kind == BillingResult.Kind.NOT_CONFIGURED, restore.kind.name()));

        BillingResult<?> active = repo.activeSubscription();
        out.add(new Case("active_sub_not_configured", active.kind == BillingResult.Kind.NOT_CONFIGURED, active.kind.name()));

        // Developer test independence: billing path must not flip entitlement
        boolean before = com.myplan.app.AppInfrastructure.isPremiumTestMode(c);
        repo.purchase("premium_yearly");
        boolean after = com.myplan.app.AppInfrastructure.isPremiumTestMode(c);
        out.add(new Case("purchase_does_not_change_dev_test", before == after, "before=" + before + " after=" + after));

        out.add(new Case("status_label", "NOT_CONFIGURED".equals(repo.statusLabel()), repo.statusLabel()));

        // Catalog placeholders exist but configured=false
        boolean allUnconfigured = true;
        for (ProductInfo pi : repo.catalogPlaceholders()) {
            if (pi.configured) allUnconfigured = false;
        }
        out.add(new Case("catalog_placeholders_unconfigured", allUnconfigured, "ok"));

        return out;
    }
}
