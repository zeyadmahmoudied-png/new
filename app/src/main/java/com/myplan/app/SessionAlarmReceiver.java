package com.myplan.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class SessionAlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "myplan_session_alarm";
    public static String lastMissedRecvDiag = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Context app = context.getApplicationContext();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)) {
            try {
                Planner p = new Planner(app);
                SessionAlarmScheduler.resync(app, p);
            } catch (Exception ignored) {}
            return;
        }

        // إشعار فوات المحاضرة (نهاية الجلسة دون إنجاز)
        if (SessionAlarmScheduler.ACTION_SESSION_MISSED.equals(action)) {
            handleMissedSession(app, intent);
            return;
        }

        boolean fire = SessionAlarmScheduler.ACTION_SESSION_START.equals(action)
                || SessionAlarmScheduler.ACTION_SNOOZE_FIRE.equals(action);
        if (!fire) return;

        Planner planner;
        try { planner = new Planner(app); } catch (Exception e) { return; }
        if (planner.settings != null && !planner.settings.alarmEnabled) return;

        String sid = intent.getStringExtra("sessionId");
        if (sid != null && SessionAlarmScheduler.dismissedSet(app).contains(sid)) return;

        Planner.Session session = null;
        if (sid != null) {
            for (Planner.Session s : planner.sessions) if (sid.equals(s.id)) { session = s; break; }
        }
        if (session != null) {
            if (session.done) return;
            long now = System.currentTimeMillis();
            if (SessionAlarmScheduler.sessionEndMs(session) <= now - 60_000) return;
        }

        String title = intent.getStringExtra("title");
        String subject = intent.getStringExtra("subject");
        String time = intent.getStringExtra("time");
        int index = intent.getIntExtra("index", 1);
        int total = intent.getIntExtra("total", 1);
        if (session != null) {
            if (title == null || title.length() == 0) title = session.taskName;
            if (subject == null) subject = session.subject;
            if (time == null) time = session.timeLabel();
            index = session.sessionIndex;
            total = session.sessionTotal;
        }
        if (title == null || title.length() == 0) title = "جلسة مذاكرة";
        if (subject == null) subject = "";
        if (time == null) time = "";

        postFullScreenNotification(app, sid, title, subject, time, index, total);

        Intent ring = new Intent(app, AlarmSoundService.class);
        ring.putExtra("sessionId", sid);
        ring.putExtra("title", title);
        ring.putExtra("subject", subject);
        ring.putExtra("time", time);
        ring.putExtra("index", index);
        ring.putExtra("total", total);
        ring.putExtra("vibrate", planner.settings == null || planner.settings.alarmVibrate);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) app.startForegroundService(ring);
            else app.startService(ring);
        } catch (Exception e) {
            try { app.startService(ring); } catch (Exception ignored) {}
        }

        Intent ui = new Intent(app, AlarmActivity.class);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ui.putExtra("sessionId", sid);
        ui.putExtra("title", title);
        ui.putExtra("subject", subject);
        ui.putExtra("time", time);
        ui.putExtra("index", index);
        ui.putExtra("total", total);
        try { app.startActivity(ui); } catch (Exception ignored) {}
    }

    private void handleMissedSession(Context app, Intent intent) {
        String sid = intent != null ? intent.getStringExtra("sessionId") : null;
        lastMissedRecvDiag = "recv sid=" + sid + " t=" + System.currentTimeMillis();
        if (sid == null) return;
        Planner planner;
        try { planner = new Planner(app); } catch (Exception e) { return; }
        Planner.Session session = null;
        for (Planner.Session s : planner.sessions) {
            if (s != null && sid.equals(s.id)) { session = s; break; }
        }
        if (session == null || session.done) return;
        // علّم missed واحفظ
        try {
            long now = System.currentTimeMillis();
            if (SessionAlarmScheduler.sessionEndMs(session) <= now) {
                session.missed = true;
                planner.save();
            }
        } catch (Exception ignored) {}
        if (session.done) return;
        if (SessionAlarmScheduler.wasMissedNotified(app, sid)) return;
        String name = session.taskName != null && session.taskName.length() > 0
                ? session.taskName
                : (intent.getStringExtra("title") != null ? intent.getStringExtra("title") : "محاضرة");
        postMissedNotification(app, sid, name);
        SessionAlarmScheduler.markMissedNotified(app, sid);
    }

    /** إشعار نظام: محاضرة فائتة — الضغط يفتح Smart Recovery */
    static void postMissedNotification(Context app, String sessionId, String taskName) {
        ensureMissedChannel(app);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(app, MainActivity.class);
        open.setAction(Intent.ACTION_MAIN);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("openMissedSessionId", sessionId);
        open.putExtra("openSmartRecovery", true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        int req = 700000 + (SessionAlarmScheduler.missedCodeOf(sessionId) % 100000);
        PendingIntent pi = PendingIntent.getActivity(app, req, open, flags);

        String title = "محاضرة فائتة";
        String body = (taskName != null ? taskName : "محاضرة") + " لم يتم تنفيذها في موعدها.";
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(app, CHANNEL_MISSED_ID);
        } else {
            b = new Notification.Builder(app);
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 21) b.setColor(0xFFE25563);
        try {
            nm.notify(req, b.build());
        } catch (Exception ignored) {}
    }

    public static final String CHANNEL_MISSED_ID = "myplan_session_missed";

    static void ensureMissedChannel(Context app) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL_MISSED_ID);
        if (ch != null) return;
        ch = new NotificationChannel(CHANNEL_MISSED_ID, "محاضرات فائتة", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("تنبيه عند فوات موعد جلسة دراسية");
        ch.enableVibration(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    static void postFullScreenNotification(Context app, String sid, String title,
                                           String subject, String time, int index, int total) {
        ensureChannel(app);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent ui = new Intent(app, AlarmActivity.class);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ui.putExtra("sessionId", sid);
        ui.putExtra("title", title);
        ui.putExtra("subject", subject);
        ui.putExtra("time", time);
        ui.putExtra("index", index);
        ui.putExtra("total", total);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        int req = SessionAlarmScheduler.codeOf(sid == null ? title : sid);
        PendingIntent full = PendingIntent.getActivity(app, req, ui, flags);

        String body = subject;
        if (body.length() > 0) body += "  ·  ";
        body += time;
        if (total > 0) body += "  ·  جلسة " + Math.max(1, index) + "/" + total;

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(app, CHANNEL_ID);
        else {
            b = new Notification.Builder(app);
            b.setPriority(Notification.PRIORITY_MAX);
        }
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان وقت المذاكرة")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title + "\n" + body))
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 21) b.setColor(0xFF3B6BFF);
        try { nm.notify(9000 + (req % 10000), b.build()); } catch (Exception ignored) {}
    }

    static void cancelHeadsUp(Context app) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.cancelAll(); } catch (Exception ignored) {}
        }
    }

    static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
        if (ch != null) return;
        ch = new NotificationChannel(CHANNEL_ID, "منبّه جلسات المذاكرة", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("منبّه كامل عند بداية كل جلسة");
        ch.enableVibration(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(false);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound != null) {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(sound, aa);
        }
        nm.createNotificationChannel(ch);
    }
}
