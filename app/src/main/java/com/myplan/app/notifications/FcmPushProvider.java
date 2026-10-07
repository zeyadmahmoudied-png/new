package com.myplan.app.notifications;

import android.content.Context;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.concurrent.TimeUnit;

/** Client-side FCM registration. Server-side sends remain Control Center/backend responsibility. */
public final class FcmPushProvider implements PushProvider {
    private final Context app;

    public FcmPushProvider(Context c) {
        this.app = c.getApplicationContext();
    }

    @Override public String providerName() { return "fcm"; }

    @Override public boolean isConfigured() {
        try {
            return !FirebaseApp.getApps(app).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public NotificationResult<String> getRegistrationToken() {
        if (!isConfigured()) return NotificationResult.notConfigured();
        try {
            String token = Tasks.await(FirebaseMessaging.getInstance().getToken(), 12, TimeUnit.SECONDS);
            return token == null || token.isEmpty() ? NotificationResult.error("FCM token empty")
                    : NotificationResult.success(token);
        } catch (Throwable t) { return NotificationResult.error("FCM unavailable"); }
    }
    @Override public NotificationResult<Void> sendTestPush() {
        return NotificationResult.notConfigured();
    }
}
