package com.myplan.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlarmActivity extends Activity {
    private String sessionId;
    private Planner planner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLock();
        planner = new Planner(this);
        sessionId = getIntent() != null ? getIntent().getStringExtra("sessionId") : null;
        String title = extra("title", "جلسة مذاكرة");
        String subject = extra("subject", "");
        String time = extra("time", "");
        int index = getIntent() != null ? getIntent().getIntExtra("index", 1) : 1;
        int total = getIntent() != null ? getIntent().getIntExtra("total", 1) : 1;
        if (sessionId != null) {
            for (Planner.Session s : planner.sessions) {
                if (sessionId.equals(s.id)) {
                    if (s.taskName != null && s.taskName.length() > 0) title = s.taskName;
                    if (s.subject != null) subject = s.subject;
                    time = s.timeLabel();
                    index = s.sessionIndex;
                    total = s.sessionTotal;
                    break;
                }
            }
        }
        setContentView(buildUi(title, subject, time, index, total));
    }

    private String extra(String k, String def) {
        if (getIntent() == null) return def;
        String v = getIntent().getStringExtra(k);
        return v == null || v.length() == 0 ? def : v;
    }

    private void showOverLock() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 26) {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) {
                try { km.requestDismissKeyguard(this, null); } catch (Exception ignored) {}
            }
        }
    }

    private View buildUi(String title, String subject, String time, int index, int total) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(0xFF070A10);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(36), dp(28), dp(36));

        TextView head = tv("حان وقت المذاكرة", 18, 0xFF8B93A7);
        head.setGravity(Gravity.CENTER);
        root.addView(head);

        TextView name = tv(title, 30, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(18), 0, dp(8));
        root.addView(name);

        if (subject.length() > 0) {
            TextView sub = tv(subject, 18, 0xFF3B6BFF);
            sub.setGravity(Gravity.CENTER);
            root.addView(sub);
        }

        if (total > 0) {
            TextView sess = tv("جلسة " + Math.max(1, index) + " / " + total, 16, 0xFF8B93A7);
            sess.setGravity(Gravity.CENTER);
            sess.setPadding(0, dp(6), 0, 0);
            root.addView(sess);
        }

        TextView clock = tv(prettyTime(time), 36, Color.WHITE);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(0, dp(28), 0, dp(36));
        root.addView(clock);

        TextView stop = action("إيقاف المنبه", 0xFFFF5C5C);
        stop.setOnClickListener(v -> dismissAlarm());
        root.addView(stop, btnLp());

        boolean snoozeOn = planner.settings == null || planner.settings.alarmSnooze;
        if (snoozeOn) {
            int mins = planner.settings != null ? planner.settings.snoozeMin : 5;
            if (mins != 10) mins = 5;
            final int sm = mins;
            TextView snooze = action("غفوة " + sm + " دقائق", 0xFF1C2436);
            snooze.setOnClickListener(v -> snoozeAlarm(sm));
            LinearLayout.LayoutParams lp = btnLp();
            lp.topMargin = dp(12);
            root.addView(snooze, lp);
        }
        return root;
    }

    private String prettyTime(String timeLabel) {
        if (timeLabel == null || timeLabel.length() < 5) return timeLabel == null ? "" : timeLabel;
        String start = timeLabel.substring(0, 5);
        try {
            int h = Integer.parseInt(start.substring(0, 2));
            int m = Integer.parseInt(start.substring(3, 5));
            String am = h >= 12 ? "م" : "ص";
            int h12 = h % 12;
            if (h12 == 0) h12 = 12;
            return String.format(java.util.Locale.US, "%d:%02d %s", h12, m, am);
        } catch (Exception e) { return start; }
    }

    private void dismissAlarm() {
        if (sessionId != null) SessionAlarmScheduler.markDismissed(this, sessionId);
        AlarmSoundService.stop(this);
        SessionAlarmReceiver.cancelHeadsUp(this);
        finish();
    }

    private void snoozeAlarm(int minutes) {
        Planner.Session session = null;
        if (sessionId != null) {
            for (Planner.Session s : planner.sessions) if (sessionId.equals(s.id)) { session = s; break; }
        }
        AlarmSoundService.stop(this);
        SessionAlarmReceiver.cancelHeadsUp(this);
        if (session != null) SessionAlarmScheduler.scheduleSnooze(this, session, minutes);
        finish();
    }

    private TextView tv(String s, float z, int c) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(z);
        t.setTextColor(c);
        return t;
    }

    private TextView action(String s, int bg) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(16), dp(18), dp(16), dp(18));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(16));
        t.setBackground(d);
        return t;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        dismissAlarm();
    }
}
