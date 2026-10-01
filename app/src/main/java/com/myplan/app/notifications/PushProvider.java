package com.myplan.app.notifications;

/** Abstraction for FCM / other push later. */
public interface PushProvider {
    String providerName();
    boolean isConfigured();
    NotificationResult<String> getRegistrationToken();
    NotificationResult<Void> sendTestPush();
}
