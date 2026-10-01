package com.myplan.app.notifications;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.concurrent.TimeUnit;

/** Client-side FCM registration. Server-side sends remain Control Center/backend responsibility. */
public final class FcmPushProvider implements PushProvider {
    @Override public String providerName() { return "fcm"; }
    @Override public boolean isConfigured() { return true; }

    @Override public NotificationResult<String> getRegistrationToken() {
        try {
            String token = Tasks.await(FirebaseMessaging.getInstance().getToken(), 12, TimeUnit.SECONDS);
            return token == null || token.isEmpty()
                    ? NotificationResult.notConfigured()
                    : NotificationResult.success(token);
        } catch (Throwable t) {
            return NotificationResult.notConfigured();
        }
    }

    @Override public NotificationResult<Void> sendTestPush() {
        // Test sends are intentionally performed by the Control Center/backend.
        return NotificationResult.notConfigured();
    }
}
