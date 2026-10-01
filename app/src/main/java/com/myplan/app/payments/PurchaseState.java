package com.myplan.app.payments;

public final class PurchaseState {
    public String productId;
    public String status; // none | pending | purchased | cancelled | error
    public String purchaseReference;
    public long purchasedAtMs;
    public String provider;

    public static PurchaseState none() {
        PurchaseState p = new PurchaseState();
        p.status = "none";
        p.provider = "none";
        return p;
    }
}
