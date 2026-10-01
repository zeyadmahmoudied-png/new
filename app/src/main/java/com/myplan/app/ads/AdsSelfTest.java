package com.myplan.app.ads;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class AdsSelfTest {
    private AdsSelfTest() {}

    public static final class Case {
        public final String name;
        public final boolean pass;
        public final String detail;
        Case(String n, boolean p, String d) { name = n; pass = p; detail = d; }
    }

    public static List<Case> run(Context c) {
        List<Case> out = new ArrayList<>();
        AdService svc = AdsLayer.service(c);
        AdProvider p = svc.provider();

        out.add(new Case("provider_not_configured", !p.isConfigured(), p.providerName()));
        out.add(new Case("provider_not_initialized", !p.isInitialized(), "ok"));
        out.add(new Case("status_not_configured", "NOT_CONFIGURED".equals(svc.statusLabel()), svc.statusLabel()));

        AdResult<Void> load = svc.load(AdPlacement.TODAY);
        out.add(new Case("load_blocked",
                load.kind == AdResult.Kind.NOT_CONFIGURED || load.kind == AdResult.Kind.DISABLED,
                load.kind.name()));

        AdResult<Void> show = svc.show(AdPlacement.TASKS);
        out.add(new Case("show_blocked",
                show.kind == AdResult.Kind.NOT_CONFIGURED || show.kind == AdResult.Kind.DISABLED,
                show.kind.name()));

        out.add(new Case("should_show_false_without_provider",
                !svc.shouldShowAds(AdPlacement.STATS), "ok"));

        out.add(new Case("revenue_not_configured",
                "NOT_CONFIGURED".equals(AdsLayer.revenueStatus()), AdsLayer.revenueStatus()));

        // Premium suppression path (uses current entitlement; does not change it)
        boolean premium = com.myplan.app.AppInfrastructure.isPremiumActive(c);
        if (premium) {
            out.add(new Case("premium_suppresses",
                    svc.isPremiumSuppressingAds() && !svc.shouldShowAds(AdPlacement.TODAY),
                    "premium active"));
        } else {
            out.add(new Case("free_still_needs_provider",
                    !svc.shouldShowAds(AdPlacement.TODAY), "free but provider missing"));
        }

        return out;
    }
}
