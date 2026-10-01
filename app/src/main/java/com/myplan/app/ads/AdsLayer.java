package com.myplan.app.ads;

import android.content.Context;

/** Resolves ads provider. No AdMob SDK wired — no fake impressions. */
public final class AdsLayer {
    private AdsLayer() {}

    public static AdProvider provider(Context c) {
        // Future: return AdMobProvider when SDK + unit IDs are ready (test vs prod separated).
        return new NotConfiguredAdProvider();
    }

    public static AdService service(Context c) {
        return new AdService(c);
    }

    /** Future revenue from official dashboard only. */
    public static String revenueStatus() {
        return "NOT_CONFIGURED";
    }
}
