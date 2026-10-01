package com.myplan.app.notifications;

public final class NotConfiguredPushProvider implements PushProvider {
    @Override public String providerName() { return "none"; }
    @Override public boolean isConfigured() { return false; }
    @Override public NotificationResult<String> getRegistrationToken() { return NotificationResult.notConfigured(); }
    @Override public NotificationResult<Void> sendTestPush() { return NotificationResult.notConfigured(); }
}
