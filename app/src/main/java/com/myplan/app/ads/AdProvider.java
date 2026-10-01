package com.myplan.app.ads;

/** Abstraction for AdMob (or other) later. */
public interface AdProvider {
    String providerName();
    boolean isConfigured();
    boolean isInitialized();
    AdResult<Void> initialize();
    AdResult<Void> load(AdPlacement placement);
    AdResult<Void> show(AdPlacement placement);
    void destroy();
}
