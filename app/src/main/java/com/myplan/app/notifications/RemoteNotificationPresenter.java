package com.myplan.app.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.myplan.app.MainActivity;
import com.myplan.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shows remote push messages when the app polls Control Center, with local de-duplication. */
public final class RemoteNotificationPresenter {
    private static final String PREFS = "myplan_remote_push_v1";
    private static final String CHANNEL_ID = "myplan_remote";

    private RemoteNotificationPresenter() {}

    public static void showPending(Context context, JSONArray messages) {
        if (context == null || messages == null || messages.length() == 0) return;
        Context app = context.getApplicationContext();
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "إشعارات My Plan", NotificationManager.IMPORTANCE_HIGH));
        }

        android.content.SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int i = 0; i < messages.length(); i++) {
            try {
                JSONObject o = messages.getJSONObject(i);
                String id = o.optString("id", "");
                if (id.isEmpty() || sp.getBoolean("shown_" + id, false)) continue;

                String title = o.optString("title", "My Plan");
                String body = o.optString("body", "لديك إشعار جديد");
                String deepLink = o.optString("deep_link", "");
                Intent intent = new Intent(app, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                if (!deepLink.isEmpty()) intent.putExtra("myplan_deep_link", deepLink);

                PendingIntent pi = PendingIntent.getActivity(
                        app, id.hashCode(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                Notification.Builder b = Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(app, CHANNEL_ID)
                        : new Notification.Builder(app);
                b.setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new Notification.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setContentIntent(pi);

                nm.notify(id.hashCode(), b.build());
                sp.edit().putBoolean("shown_" + id, true).apply();
            } catch (Exception ignored) {}
        }
    }
}
