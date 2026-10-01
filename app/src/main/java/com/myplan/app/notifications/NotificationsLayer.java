package com.myplan.app.notifications;

import android.content.Context;

/** Facade — no FCM SDK. Session alarms remain in existing Alarm system. */
public final class NotificationsLayer {
    private NotificationsLayer() {}

    public static PushProvider pushProvider(Context c) {
        return new NotConfiguredPushProvider();
    }

    public static NotificationService service(Context c) {
        return new NotificationService(c);
    }
}
