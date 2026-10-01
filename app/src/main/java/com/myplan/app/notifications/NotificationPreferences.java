package com.myplan.app.notifications;

import android.content.Context;
import android.content.SharedPreferences;

/** Local preference flags — independent of push provider readiness. */
public final class NotificationPreferences {
    private static final String PREFS = "myplan_notifications_v1";

    private NotificationPreferences() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean sessionReminders(Context c) { return sp(c).getBoolean("session_reminders", true); }
    public static boolean examReminders(Context c) { return sp(c).getBoolean("exam_reminders", true); }
    public static boolean taskReminders(Context c) { return sp(c).getBoolean("task_reminders", true); }
    public static boolean cloudEvents(Context c) { return sp(c).getBoolean("cloud_events", false); }
    public static boolean announcements(Context c) { return sp(c).getBoolean("announcements", false); }

    public static void setSessionReminders(Context c, boolean v) { sp(c).edit().putBoolean("session_reminders", v).apply(); }
    public static void setExamReminders(Context c, boolean v) { sp(c).edit().putBoolean("exam_reminders", v).apply(); }
    public static void setTaskReminders(Context c, boolean v) { sp(c).edit().putBoolean("task_reminders", v).apply(); }
    public static void setCloudEvents(Context c, boolean v) { sp(c).edit().putBoolean("cloud_events", v).apply(); }
    public static void setAnnouncements(Context c, boolean v) { sp(c).edit().putBoolean("announcements", v).apply(); }
}
