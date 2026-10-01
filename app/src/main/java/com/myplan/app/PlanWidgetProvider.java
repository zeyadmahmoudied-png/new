package com.myplan.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.List;

/** Widget يقرأ مباشرة من Planner المحلي — بدون شبكة. */
public class PlanWidgetProvider extends AppWidgetProvider {
    public static String lastUpdateDiag = "";

    public static final String EXTRA_SIZE = "widgetSize";

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        updateSize(context, mgr, ids, sizeOf(this));
    }

    static String sizeOf(AppWidgetProvider p) {
        if (p instanceof PlanWidgetProviderLarge) return "large";
        if (p instanceof PlanWidgetProviderMedium) return "medium";
        return "small";
    }

    public static void updateAll(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        if (mgr == null) return;
        try {
            updateSize(app, mgr, mgr.getAppWidgetIds(new ComponentName(app, PlanWidgetProvider.class)), "small");
            updateSize(app, mgr, mgr.getAppWidgetIds(new ComponentName(app, PlanWidgetProviderMedium.class)), "medium");
            updateSize(app, mgr, mgr.getAppWidgetIds(new ComponentName(app, PlanWidgetProviderLarge.class)), "large");
        } catch (Exception ignored) {}
    }

    static void updateSize(Context app, AppWidgetManager mgr, int[] ids, String size) {
        if (ids == null || ids.length == 0) return;
        Planner planner;
        try { planner = new Planner(app); } catch (Exception e) { return; }
        String today = Planner.todayStr();
        int now = Planner.nowMinOfDay();
        List<Planner.Session> todayList = new ArrayList<>();
        for (Planner.Session s : planner.sessions) {
            if (s != null && today.equals(s.day) && !s.done) todayList.add(s);
        }
        todayList.sort((a, b) -> Integer.compare(a.startMin, b.startMin));

        Planner.Session current = null, next = null;
        for (Planner.Session s : todayList) {
            int end = s.endMin <= s.startMin ? s.endMin + 24 * 60 : s.endMin;
            if (s.startMin <= now && now < end) current = s;
            else if (s.startMin > now && next == null) next = s;
        }
        Planner.Session focus = current != null ? current : next;

        int layout = R.layout.widget_small;
        if ("medium".equals(size)) layout = R.layout.widget_medium;
        if ("large".equals(size)) layout = R.layout.widget_large;

        String label, title, time;
        if (todayList.isEmpty()) {
            label = "My Plan";
            title = "مفيش جلسات اليوم";
            time = "أضف مهامًا أو أنشئ خطتك";
        } else if ("small".equals(size)) {
            label = current != null ? "الآن" : "التالي";
            title = focus != null && focus.taskName != null ? focus.taskName : "جلسة";
            time = focus != null ? focus.timeLabel() : "";
        } else {
            label = "جدول اليوم";
            title = current != null
                    ? ("الآن: " + (current.taskName != null ? current.taskName : ""))
                    : (next != null ? ("التالي: " + next.taskName) : "جلسات اليوم");
            StringBuilder sb = new StringBuilder();
            int limit = "large".equals(size) ? 8 : 4;
            int n = 0;
            for (Planner.Session s : todayList) {
                if (n >= limit) break;
                if (sb.length() > 0) sb.append('\n');
                sb.append(s.timeLabel()).append("  ").append(s.taskName != null ? s.taskName : "");
                n++;
            }
            time = sb.toString();
        }

        lastUpdateDiag = "size=" + size + " sessions=" + todayList.size()
                + " source=Planner.local now=" + now;

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(app.getPackageName(), layout);
            rv.setTextViewText(R.id.widget_label, label);
            rv.setTextViewText(R.id.widget_title, title);
            rv.setTextViewText(R.id.widget_time, time);
            Intent open = new Intent(app, MainActivity.class);
            open.setAction(Intent.ACTION_MAIN);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (focus != null && focus.id != null) {
                open.putExtra("openSessionId", focus.id);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(app, 88000 + id, open, flags);
            rv.setOnClickPendingIntent(R.id.widget_root, pi);
            mgr.updateAppWidget(id, rv);
        }
    }
}
