package com.myplan.app.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;


import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.myplan.app.MainActivity;
import com.myplan.app.R;
import com.myplan.app.AppInfrastructure;
import com.myplan.app.supabase.SupabaseRepository;

/** Real FCM receiver. Firebase project config is supplied through google-services.json. */
public final class FcmPushService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "myplan_remote";

    @Override public void onNewToken(String token) {
        try { new SupabaseRepository(getApplicationContext()).upsertDeviceFcmToken(token); }
        catch (Exception ignored) {}
    }

    @Override public void onMessageReceived(RemoteMessage msg) {
        String title = msg.getData().get("title");
        String body = msg.getData().get("body");
        if (title == null && msg.getNotification() != null) title = msg.getNotification().getTitle();
        if (body == null && msg.getNotification() != null) body = msg.getNotification().getBody();
        if (title == null) title = "My Plan";
        if (body == null) body = "لديك إشعار جديد";
        show(title, body, msg.getData().get("deep_link"));
    }

    private void show(String title, String body, String deepLink) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "إشعارات My Plan", NotificationManager.IMPORTANCE_HIGH));
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (deepLink != null && !deepLink.isEmpty()) i.putExtra("myplan_deep_link", deepLink);
        PendingIntent pi = PendingIntent.getActivity(this, 8801, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pi);
        nm.notify((int)(System.currentTimeMillis() & 0x7fffffff), b.build());
    }
}
