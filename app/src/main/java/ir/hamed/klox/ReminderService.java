package ir.hamed.klox;
import android.app.*;import android.content.*;import android.media.*;import android.net.Uri;import android.os.*;
public class ReminderService extends Service{
 private MediaPlayer player; private static final String CH="reminders";
 public static void start(Context c){Intent i=new Intent(c,ReminderService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
 public void onCreate(){super.onCreate();
  if(Build.VERSION.SDK_INT>=26){
   NotificationChannel ch=new NotificationChannel(CH,"ياداندازي ها",NotificationManager.IMPORTANCE_HIGH);
   getSystemService(NotificationManager.class).createNotificationChannel(ch);
  }
 }
 public int onStartCommand(Intent intent,int flags,int id){
  SharedPreferences p=getSharedPreferences("settings",MODE_PRIVATE); String text=p.getString("reminderText","یادآوری"); 
  Notification n;
  if(Build.VERSION.SDK_INT>=26){
   n=new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_status_clock).setContentTitle("یادآوری").setContentText(text).setAutoCancel(true).setOngoing(false).build();
  }else{
   n=new Notification.Builder(this).setSmallIcon(R.drawable.ic_status_clock).setContentTitle("یادآوری").setContentText(text).setAutoCancel(true).setOngoing(false).build();
  }
  startForeground(1901,n); play(p.getString("reminderAudioUri","")); new Handler().postDelayed(this::stopSelf,12000); return START_NOT_STICKY;
 }
 private void play(String uri){try{
  if(uri!=null&&!uri.isEmpty()) player=MediaPlayer.create(this,Uri.parse(uri));
  if(player==null) player=MediaPlayer.create(this,R.raw.reminder_chime);
  if(player!=null){player.setOnCompletionListener(mp->{mp.release();player=null;});player.start();}
 }catch(Exception ignored){}}
 public void onDestroy(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}