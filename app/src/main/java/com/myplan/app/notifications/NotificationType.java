package com.myplan.app.notifications;

public enum NotificationType {
    SESSION_REMINDER,   // local / alarm-adjacent
    EXAM_REMINDER,      // local
    TASK_REMINDER,      // local
    CLOUD_EVENT,        // push — backend
    ANNOUNCEMENT        // push — backend
}
