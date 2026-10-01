package com.myplan.app.notifications;

import android.content.Context;

/**
 * Local vs Push separation.
 * Does NOT replace SessionAlarmScheduler — study session alarms stay local.
 */
public final class NotificationService {
    private final Context app;
    private final PushProvider push;

    public NotificationService(Context c) {
        this(c, NotificationsLayer.pushProvider(c));
    }

    public NotificationService(Context c, PushProvider push) {
        this.app = c.getApplicationContext();
        this.push = push == null ? new NotConfiguredPushProvider() : push;
    }

    public PushProvider pushProvider() { return push; }

    public NotificationResult<Void> scheduleLocal(NotificationRequest req) {
        if (req == null) return NotificationResult.notConfigured();
        // Contract path: local scheduling is owned by existing Alarm system for sessions.
        // This method records intent only — does not fire fake push or break alarms.
        NotificationLog.append(app, "schedule_local", req.type == null ? "?" : req.type.name(), "contract");
        return NotificationResult.scheduledLocal();
    }

    public NotificationResult<Void> requestPush(NotificationRequest req) {
        if (!push.isConfigured()) {
            NotificationLog.append(app, "push", "NOT_CONFIGURED", "");
            return NotificationResult.notConfigured();
        }
        return NotificationResult.notConfigured();
    }

    public String pushStatus() {
        return push.isConfigured() ? "CONFIGURED" : "NOT_CONFIGURED";
    }

    public String permissionStateLabel() {
        // Accurate probing of POST_NOTIFICATIONS can be added later; no fake GRANTED.
        return "UNKNOWN_UNTIL_RUNTIME";
    }
}
