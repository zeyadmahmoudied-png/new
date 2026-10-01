package com.myplan.app.ads;

import android.content.Context;

/**
 * App-facing ads API.
 * AdsShown = providerReady && !premiumActive && placementEnabled
 * Never invents impressions or revenue.
 */
public final class AdService {
    private final Context app;
    private final AdProvider provider;

    public AdService(Context c) {
        this(c, AdsLayer.provider(c));
    }

    public AdService(Context c, AdProvider provider) {
        this.app = c.getApplicationContext();
        this.provider = provider == null ? new NotConfiguredAdProvider() : provider;
    }

    public AdProvider provider() { return provider; }

    public boolean isPremiumSuppressingAds() {
        return com.myplan.app.AppInfrastructure.isPremiumActive(app);
    }

    public boolean isPlacementEnabled(AdPlacement placement) {
        // Future: remote/local flags. Default true for architecture; still blocked by provider.
        return placement != null;
    }

    /** Whether UI may attempt to show an ad. */
    public boolean shouldShowAds(AdPlacement placement) {
        return provider.isConfigured()
                && provider.isInitialized()
                && !isPremiumSuppressingAds()
                && isPlacementEnabled(placement);
    }

    public AdResult<Void> load(AdPlacement placement) {
        if (isPremiumSuppressingAds()) {
            AdLog.append(app, "load:" + placement, "DISABLED", "premium");
            return AdResult.disabled("Premium suppresses ads");
        }
        if (!provider.isConfigured()) {
            AdLog.append(app, "load:" + placement, "NOT_CONFIGURED", "");
            return AdResult.notConfigured();
        }
        AdResult<Void> r = provider.load(placement);
        AdLog.append(app, "load:" + placement, r.kind.name(), r.message);
        return r;
    }

    public AdResult<Void> show(AdPlacement placement) {
        if (!shouldShowAds(placement)) {
            if (isPremiumSuppressingAds()) return AdResult.disabled("Premium suppresses ads");
            if (!provider.isConfigured()) return AdResult.notConfigured();
            return AdResult.disabled("placement or provider not ready");
        }
        AdResult<Void> r = provider.show(placement);
        AdLog.append(app, "show:" + placement, r.kind.name(), r.message);
        return r;
    }

    public String statusLabel() {
        if (!provider.isConfigured()) return "NOT_CONFIGURED";
        if (!provider.isInitialized()) return "NOT_INITIALIZED";
        return "READY";
    }
}
