package com.myplan.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class SessionAlarmScheduler {
    public static final String ACTION_SESSION_START = "com.myplan.app.SESSION_START";
    public static final String ACTION_SNOOZE_FIRE = "com.myplan.app.SESSION_SNOOZE";
    /** إطلاق عند انتهاء نافذة الجلسة دون إنجاز → إشعار فائتة */
    public static final String ACTION_SESSION_MISSED = "com.myplan.app.SESSION_MISSED";
    private static final String PREFS = "myplan_alarms";
    private static final String KEY_IDS = "codes";
    private static final String KEY_DISMISSED = "dismissed";
    private static final String KEY_SNOOZE = "snooze";
    private static final String KEY_MISSED_NOTIFIED = "missed_notified";
    public static int lastCount = 0;
    public static String lastMissedSchedDiag = "";

    public static void resync(Context ctx, Planner planner) {
        if (ctx == null || planner == null) return;
        Context app = ctx.getApplicationContext();
        cancelAll(app);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean startAlarms = planner.settings != null && planner.settings.alarmEnabled;
        Set<String> dismissed = dismissedSet(app);
        long now = System.currentTimeMillis();
        StringBuilder codes = new StringBuilder();
        Set<Integer> used = new HashSet<>();
        int missedSched = 0;
        for (Planner.Session s : planner.sessions) {
            if (s == null || s.done || s.id == null) continue;
            long end = sessionEndMs(s);
            // إشعار الفوات يُجدول دائمًا عند نهاية الجلسة — مستقل عن منبّه البداية
            if (end > now && !wasMissedNotified(app, s.id)) {
                int missCode = missedCodeOf(s.id);
                if (used.add(missCode)) {
                    long missWhen = end;
                    if (missWhen < now + 800) missWhen = now + 1500;
                    PendingIntent mpi = pendingMissed(app, s, missCode);
                    scheduleAt(app, am, missWhen, mpi, s.taskName);
                    if (codes.length() > 0) codes.append(',');
                    codes.append(missCode);
                    missedSched++;
                }
            }
            if (!startAlarms) continue;
            if (dismissed.contains(s.id)) continue;
            if (end <= now) continue;
            long when = snoozeUntil(app, s.id);
            if (when <= now) when = triggerAt(s);
            if (when < now - 2000) continue;
            if (when < now + 800) when = now + 1500;
            int code = codeOf(s.id);
            if (!used.add(code)) continue;
            PendingIntent pi = pending(app, s, code, false);
            scheduleAt(app, am, when, pi, s.taskName);
            if (codes.length() > 0) codes.append(',');
            codes.append(code);
        }
        lastCount = used.size();
        lastMissedSchedDiag = "missedAlarms=" + missedSched + " totalCodes=" + lastCount
                + " startAlarms=" + startAlarms;
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_IDS, codes.toString()).apply();
    }

    public static int missedCodeOf(String id) {
        int h = codeOf(id);
        return h ^ 0x5A5A5A5A;
    }

    static PendingIntent pendingMissed(Context app, Planner.Session s, int code) {
        Intent i = new Intent(app, SessionAlarmReceiver.class);
        i.setAction(ACTION_SESSION_MISSED);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        i.putExtra("sessionId", s.id);
        i.putExtra("title", s.taskName == null ? "محاضرة" : s.taskName);
        i.putExtra("subject", s.subject == null ? "" : s.subject);
        i.putExtra("time", s.timeLabel());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(app, code, i, flags);
    }

    public static boolean wasMissedNotified(Context ctx, String sessionId) {
        if (sessionId == null) return true;
        Set<String> set = missedNotifiedSet(ctx);
        return set.contains(sessionId);
    }

    public static void markMissedNotified(Context ctx, String sessionId) {
        if (sessionId == null) return;
        SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> set = missedNotifiedSet(ctx);
        if (set.contains(sessionId)) return;
        set.add(sessionId);
        p.edit().putString(KEY_MISSED_NOTIFIED, join(set)).apply();
    }

    public static void clearMissedNotified(Context ctx, String sessionId) {
        if (sessionId == null) return;
        Set<String> set = missedNotifiedSet(ctx);
        if (!set.remove(sessionId)) return;
        ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MISSED_NOTIFIED, join(set)).apply();
    }

    static Set<String> missedNotifiedSet(Context ctx) {
        Set<String> out = new HashSet<>();
        String raw = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MISSED_NOTIFIED, "");
        if (raw.length() == 0) return out;
        for (String p : raw.split(",")) if (p.trim().length() > 0) out.add(p.trim());
        return out;
    }

    public static void scheduleTestIn(Context ctx, int seconds) {
        Context app = ctx.getApplicationContext();
        Planner.Session s = new Planner.Session();
        s.id = "test-alarm";
        s.taskName = "تجربة المنبّه";
        s.subject = "My Plan";
        s.day = Planner.todayStr();
        Calendar n = Calendar.getInstance();
        s.startMin = n.get(Calendar.HOUR_OF_DAY) * 60 + n.get(Calendar.MINUTE);
        s.endMin = s.startMin + 1;
        s.sessionIndex = 1;
        s.sessionTotal = 1;
        unmarkDismissed(app, s.id);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int code = codeOf(s.id);
        PendingIntent pi = pending(app, s, code, false);
        scheduleAt(app, am, System.currentTimeMillis() + Math.max(8, seconds) * 1000L, pi, s.taskName);
        rememberCode(app, code);
    }

    public static void scheduleSnooze(Context ctx, Planner.Session s, int minutes) {
        if (ctx == null || s == null || s.id == null) return;
        Context app = ctx.getApplicationContext();
        unmarkDismissed(app, s.id);
        long when = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        putSnooze(app, s.id, when);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int code = codeOf(s.id);
        PendingIntent pi = pending(app, s, code, true);
        scheduleAt(app, am, when, pi, s.taskName);
        rememberCode(app, code);
    }

    public static void markDismissed(Context ctx, String sessionId) {
        if (sessionId == null) return;
        SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> set = dismissedSet(ctx);
        set.add(sessionId);
        p.edit().putString(KEY_DISMISSED, join(set)).apply();
        clearSnooze(ctx, sessionId);
        cancelOne(ctx, sessionId);
    }

    public static void unmarkDismissed(Context ctx, String sessionId) {
        Set<String> set = dismissedSet(ctx);
        if (set.remove(sessionId)) {
            ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_DISMISSED, join(set)).apply();
        }
    }

    public static void cancelAll(Context ctx) {
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_IDS, "");
        if (am != null && raw.length() > 0) {
            for (String part : raw.split(",")) {
                try {
                    int code = Integer.parseInt(part.trim());
                    am.cancel(pendingStub(app, code));
                } catch (Exception ignored) {}
            }
        }
        p.edit().putString(KEY_IDS, "").apply();
    }

    static void cancelOne(Context ctx, String sessionId) {
        AlarmManager am = (AlarmManager) ctx.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingStub(ctx.getApplicationContext(), codeOf(sessionId)));
    }

    static void scheduleAt(Context app, AlarmManager am, long when, PendingIntent pi, String label) {
        try {
            Intent show = new Intent(app, MainActivity.class);
            show.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent showPi = PendingIntent.getActivity(app, 1, show, flags);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showPi), pi);
            return;
        } catch (Exception ignored) {}
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (Exception ignored) {}
    }

    public static int codeOf(String id) {
        int h = id == null ? 0 : id.hashCode();
        if (h == Integer.MIN_VALUE) h = 1;
        return Math.abs(h);
    }

    static long triggerAt(Planner.Session s) {
        Calendar c = Planner.dayCal(s.day);
        int start = Math.max(0, s.startMin);
        c.set(Calendar.HOUR_OF_DAY, start / 60);
        c.set(Calendar.MINUTE, start % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long sessionEndMs(Planner.Session s) {
        Calendar c = Planner.dayCal(s.day);
        int end = s.endMin;
        if (end <= s.startMin) end += 24 * 60;
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, end);
        return c.getTimeInMillis();
    }

    static PendingIntent pending(Context app, Planner.Session s, int code, boolean snooze) {
        Intent i = new Intent(app, SessionAlarmReceiver.class);
        i.setAction(snooze ? ACTION_SNOOZE_FIRE : ACTION_SESSION_START);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        i.putExtra("sessionId", s.id);
        i.putExtra("title", s.taskName == null ? "جلسة مذاكرة" : s.taskName);
        i.putExtra("subject", s.subject == null ? "" : s.subject);
        i.putExtra("time", s.timeLabel());
        i.putExtra("index", s.sessionIndex);
        i.putExtra("total", s.sessionTotal);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(app, code, i, flags);
    }

    static PendingIntent pendingStub(Context app, int code) {
        Intent i = new Intent(app, SessionAlarmReceiver.class);
        i.setAction(ACTION_SESSION_START);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(app, code, i, flags);
    }

    static Set<String> dismissedSet(Context ctx) {
        Set<String> out = new HashSet<>();
        String raw = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DISMISSED, "");
        if (raw.length() == 0) return out;
        for (String p : raw.split(",")) if (p.trim().length() > 0) out.add(p.trim());
        return out;
    }

    static void putSnooze(Context ctx, String id, long when) {
        SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_SNOOZE, "");
        StringBuilder sb = new StringBuilder();
        if (raw.length() > 0) {
            for (String part : raw.split(",")) {
                if (part.startsWith(id + ":")) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(part);
            }
        }
        if (sb.length() > 0) sb.append(',');
        sb.append(id).append(':').append(when);
        p.edit().putString(KEY_SNOOZE, sb.toString()).apply();
    }

    static void clearSnooze(Context ctx, String id) {
        SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_SNOOZE, "");
        if (raw.length() == 0) return;
        StringBuilder sb = new StringBuilder();
        for (String part : raw.split(",")) {
            if (part.startsWith(id + ":")) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(part);
        }
        p.edit().putString(KEY_SNOOZE, sb.toString()).apply();
    }

    static long snoozeUntil(Context ctx, String id) {
        String raw = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SNOOZE, "");
        if (raw.length() == 0) return 0;
        String prefix = id + ":";
        for (String part : raw.split(",")) {
            if (part.startsWith(prefix)) {
                try { return Long.parseLong(part.substring(prefix.length())); } catch (Exception e) { return 0; }
            }
        }
        return 0;
    }

    static void rememberCode(Context ctx, int code) {
        SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_IDS, "");
        String c = String.valueOf(code);
        if (raw.contains(c)) return;
        p.edit().putString(KEY_IDS, raw.length() == 0 ? c : raw + "," + c).apply();
    }

    static String join(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }
}
