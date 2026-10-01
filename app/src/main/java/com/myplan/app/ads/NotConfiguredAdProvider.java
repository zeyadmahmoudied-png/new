package com.myplan.app.ads;

public final class NotConfiguredAdProvider implements AdProvider {
    @Override public String providerName() { return "none"; }
    @Override public boolean isConfigured() { return false; }
    @Override public boolean isInitialized() { return false; }
    @Override public AdResult<Void> initialize() { return AdResult.notConfigured(); }
    @Override public AdResult<Void> load(AdPlacement placement) { return AdResult.notConfigured(); }
    @Override public AdResult<Void> show(AdPlacement placement) { return AdResult.notConfigured(); }
    @Override public void destroy() {}
}
