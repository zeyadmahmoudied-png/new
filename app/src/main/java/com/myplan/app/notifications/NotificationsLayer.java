package com.myplan.app.notifications;

import android.content.Context;

/** Facade for remote push; local study alarms remain separate. */
public final class NotificationsLayer {
    private NotificationsLayer() {}

    public static PushProvider pushProvider(Context c) {
        // FCM is used only when google-services/Firebase initialization is actually present.
        FcmPushProvider fcm = new FcmPushProvider(c);
        return fcm.isConfigured() ? fcm : new NotConfiguredPushProvider();
    }

    public static NotificationService service(Context c) {
        return new NotificationService(c);
    }
}
