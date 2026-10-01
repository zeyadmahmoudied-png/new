package com.myplan.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmSoundService extends Service {
    public static final String ACTION_STOP = "com.myplan.app.STOP_ALARM_SOUND";
    private Ringtone ringtone;
    private Vibrator vibrator;
    private AudioManager audio;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            silence();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        String title = intent != null ? intent.getStringExtra("title") : "جلسة مذاكرة";
        boolean vibrate = intent == null || intent.getBooleanExtra("vibrate", true);
        String sid = intent != null ? intent.getStringExtra("sessionId") : "";
        startForeground(42, buildNote(title == null ? "جلسة مذاكرة" : title, sid));
        startRing(vibrate);
        return START_STICKY;
    }

    private Notification buildNote(String title, String sid) {
        SessionAlarmReceiver.ensureChannel(this);
        Intent ui = new Intent(this, AlarmActivity.class);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ui.putExtra("sessionId", sid);
        ui.putExtra("title", title);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 42, ui, flags);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, SessionAlarmReceiver.CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("منبّه المذاكرة")
                .setContentText(title)
                .setContentIntent(pi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM);
        return b.build();
    }

    private void startRing(boolean vibrate) {
        silence();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        try {
            if (audio != null) {
                audio.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } catch (Exception ignored) {}
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } else {
                    ringtone.setStreamType(AudioManager.STREAM_ALARM);
                }
                ringtone.setLooping(true);
                ringtone.play();
            }
        } catch (Exception ignored) {}
        if (vibrate) {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 700, 400, 700, 800};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        }
    }

    private void silence() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
        ringtone = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        try {
            if (audio != null) audio.abandonAudioFocus(null);
        } catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        silence();
        super.onDestroy();
    }

    public static void stop(Context ctx) {
        try {
            Intent i = new Intent(ctx, AlarmSoundService.class);
            i.setAction(ACTION_STOP);
            ctx.startService(i);
        } catch (Exception ignored) {}
        try { ctx.stopService(new Intent(ctx, AlarmSoundService.class)); } catch (Exception ignored) {}
    }
}
