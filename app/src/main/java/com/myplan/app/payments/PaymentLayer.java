package com.myplan.app.payments;

import android.content.Context;

/** Resolves billing provider. No Google Play Billing SDK wired. */
public final class PaymentLayer {
    private PaymentLayer() {}

    public static PaymentProvider provider(Context c) {
        // Future: return GooglePlayBillingProvider when SDK + products are ready.
        return new NotConfiguredPaymentProvider();
    }

    public static SubscriptionRepository subscriptions(Context c) {
        return new SubscriptionRepository(c);
    }

    /** Future remote verify via existing ApiClient — NOT_CONFIGURED until backend exists. */
    public static com.myplan.app.api.ApiResult<SubscriptionState> verifyPurchaseRemote(
            Context c, String productId, String purchaseReference) {
        return com.myplan.app.api.ApiResult.notConfigured();
    }
}
