package com.myplan.app.payments;

/** Catalog product — IDs are placeholders until Play Console products exist. */
public final class ProductInfo {
    public final String productId;
    public final String title;
    public final String description;
    public final String priceLabel; // empty when not connected
    public final String currencyCode;
    public final String period; // monthly | yearly | unknown
    public final boolean configured;

    public ProductInfo(String productId, String title, String description,
                       String priceLabel, String currencyCode, String period, boolean configured) {
        this.productId = productId;
        this.title = title;
        this.description = description;
        this.priceLabel = priceLabel == null ? "" : priceLabel;
        this.currencyCode = currencyCode == null ? "" : currencyCode;
        this.period = period == null ? "unknown" : period;
        this.configured = configured;
    }

    public static ProductInfo placeholder(String productId, String title, String period) {
        return new ProductInfo(productId, title, "", "", "", period, false);
    }
}
