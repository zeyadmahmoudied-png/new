package com.myplan.app.payments;

/** Local representation of a subscription record. Not proof of a real purchase. */
public final class SubscriptionState {
    public String subscriptionId;
    public String productId;
    public String userId;
    public String installationId;
    public String status; // none | active | expired | cancelled | pending | unknown
    public long startTimeMs;
    public long expiryTimeMs;
    public boolean autoRenewing;
    public String provider; // none | google_play | unknown
    public String environment; // none | sandbox | production
    public String purchaseReference; // token/ref from provider — never a secret key
    public long lastVerifiedAtMs;
    public String source; // purchase | server | local | developer_test

    public static SubscriptionState none() {
        SubscriptionState s = new SubscriptionState();
        s.status = "none";
        s.provider = "none";
        s.environment = "none";
        s.source = "";
        return s;
    }
}
