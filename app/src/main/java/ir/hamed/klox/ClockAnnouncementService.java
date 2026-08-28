package ir.hamed.klox;

import android.app.*;
import android.content.*;
import android.os.*;

public class ClockAnnouncementService extends Service {
    private AudioTimeSpeaker speaker;
    private static final String CHANNEL = "clock_announcement";
    @Override public void onCreate() {
        super.onCreate(); createChannel();
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        android.widget.RemoteViews rv = new android.widget.RemoteViews(getPackageName(), R.layout.notification_large);
        rv.setTextViewText(R.id.notificationTitle, "سخنگوی ساعت");
        rv.setTextViewText(R.id.notificationText, "در حال اعلام ساعت…");
        b.setContentTitle("سخنگوی ساعت").setContentText("در حال اعلام ساعت…").setCustomContentView(rv).setContent(rv).setSmallIcon(R.mipmap.ic_launcher_modern).setOngoing(true).setOnlyAlertOnce(true);
        startForeground(1201, b.build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        speaker=new AudioTimeSpeaker(this); speaker.speakScheduledTime();
        new Thread(()->{try{Thread.sleep(15000);}catch(Exception ignored){} stopSelf();}).start();
        return START_NOT_STICKY;
    }
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"اعلام ساعت",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}}
    @Override public void onDestroy(){if(speaker!=null)speaker.stop();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
