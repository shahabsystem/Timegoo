package ir.hamed.klox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

public class ReminderService extends Service {
    private MediaPlayer player;
    private static final String CH = "reminders";
    private static final String EXTRA_SLOT = "reminder_slot";

    public static void start(Context c, int slot) {
        Intent i = new Intent(c, ReminderService.class).putExtra(EXTRA_SLOT, slot);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void start(Context c) {
        start(c, 1);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH, "یادآوری‌ها", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int slot = intent != null ? intent.getIntExtra(EXTRA_SLOT, 1) : 1;
        if (slot < 1 || slot > 5) slot = 1;

        SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        String prefix = "reminder" + slot;
        String text = p.getString(prefix + "Text", "یادآوری");
        String audio = p.getString(prefix + "AudioUri", "");

        android.widget.RemoteViews rv = new android.widget.RemoteViews(getPackageName(), R.layout.notification_large);
        rv.setTextViewText(R.id.notificationTitle, "یادآوری " + slot);
        rv.setTextViewText(R.id.notificationText, text);
        Notification n;
        if (Build.VERSION.SDK_INT >= 26) {
            n = new Notification.Builder(this, CH)
                    .setSmallIcon(R.mipmap.ic_launcher_modern)
                    .setCustomContentView(rv)
                    .setContentTitle("یادآوری " + slot)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .build();
        } else {
            n = new Notification.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher_modern)
                    .setContent(rv)
                    .setContentTitle("یادآوری " + slot)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .build();
        }
        startForeground(1900 + slot, n);
        play(audio);
        new Handler().postDelayed(this::stopSelf, 15000);
        return START_NOT_STICKY;
    }

    private void play(String uri) {
        try {
            if (uri != null && !uri.isEmpty()) player = MediaPlayer.create(this, Uri.parse(uri));
            if (player == null) {
                int resId = getResources().getIdentifier("reminder_chime", "raw", getPackageName());
                if (resId != 0) player = MediaPlayer.create(this, resId);
            }
            if (player != null) {
                player.setOnCompletionListener(mp -> {
                    mp.release();
                    if (player == mp) player = null;
                });
                player.start();
            }
        } catch (Exception ignored) {
            if (player != null) {
                try { player.release(); } catch (Exception ignored2) {}
                player = null;
            }
        }
    }

    @Override public void onDestroy() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
