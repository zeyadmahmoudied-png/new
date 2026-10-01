package com.myplan.app.notifications;

public final class NotificationRequest {
    public NotificationType type;
    public String title;
    public String body;
    public long triggerAtMs;
    public String channelId;
    public boolean requiresPush;
}
