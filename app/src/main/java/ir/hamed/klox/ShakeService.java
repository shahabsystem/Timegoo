package ir.hamed.klox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

public class ShakeService extends Service implements SensorEventListener {
    private static final String CHANNEL = "shake_clock";
    private static final int NOTIFICATION_ID = 1801;
    private static final float SHAKE_G = 2.35f;
    private static final long SHAKE_COOLDOWN_MS = 1800L;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private AudioTimeSpeaker speaker;
    private long lastShakeMs;

    public static void start(Context context) {
        Intent i = new Intent(context, ShakeService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }
    @Override public void onCreate() {
        super.onCreate(); createChannel();
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setContentTitle("سخنگوی ساعت").setContentText("اعلام ساعت با تکان دادن فعال است").setSmallIcon(R.drawable.ic_status_clock).setOngoing(true).setOnlyAlertOnce(true);
        startForeground(NOTIFICATION_ID, b.build());
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        if(sensorManager!=null){ accelerometer=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); if(accelerometer!=null) sensorManager.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_GAME); }
        speaker=new AudioTimeSpeaker(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        SharedPreferences p=getSharedPreferences("settings",MODE_PRIVATE);
        if(!p.getBoolean("shakeToSpeak",false)){stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(e.sensor.getType()!=Sensor.TYPE_ACCELEROMETER)return;
        if(!getSharedPreferences("settings",MODE_PRIVATE).getBoolean("shakeToSpeak",false))return;
        float x=e.values[0]/SensorManager.GRAVITY_EARTH,y=e.values[1]/SensorManager.GRAVITY_EARTH,z=e.values[2]/SensorManager.GRAVITY_EARTH;
        float g=(float)Math.sqrt(x*x+y*y+z*z); long now=System.currentTimeMillis();
        if(g>=SHAKE_G && now-lastShakeMs>=SHAKE_COOLDOWN_MS){lastShakeMs=now;if(speaker!=null)speaker.speakCurrentTime();}
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"اعلام با تکان",NotificationManager.IMPORTANCE_LOW);NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.createNotificationChannel(ch);}}
    @Override public void onDestroy(){if(sensorManager!=null)sensorManager.unregisterListener(this);if(speaker!=null)speaker.stop();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
