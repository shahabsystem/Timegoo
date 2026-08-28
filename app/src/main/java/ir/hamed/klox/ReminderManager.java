package ir.hamed.klox;
import android.app.*;import android.content.*;import android.media.*;import android.net.Uri;import android.os.*;import java.util.*;
public final class ReminderManager{
 private static final int REQUEST=8301; private static final String ACTION="ir.hamed.klox.REMINDER";
 private ReminderManager(){}
 public static void schedule(Context ctx){
  Context c=ctx.getApplicationContext(); SharedPreferences p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);
  cancel(c); if(!p.getBoolean("reminderEnabled",false)) return;
  String text=p.getString("reminderText","").trim(); if(text.isEmpty()) return;
  Calendar n=Calendar.getInstance(), t=(Calendar)n.clone();
  int min=Math.max(0,Math.min(1439,p.getInt("reminderMinute",540)));
  t.set(Calendar.HOUR_OF_DAY,min/60);t.set(Calendar.MINUTE,min%60);t.set(Calendar.SECOND,0);t.set(Calendar.MILLISECOND,0);
  if(!t.after(n))t.add(Calendar.DAY_OF_YEAR,1);
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); if(am==null)return;
  Intent i=new Intent(c,ReminderReceiver.class).setAction(ACTION);
  PendingIntent pi=PendingIntent.getBroadcast(c,REQUEST,i,PendingIntent.FLAG_UPDATE_CURRENT|flag());
  if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.getTimeInMillis(),pi);
  else if(Build.VERSION.SDK_INT>=23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.getTimeInMillis(),pi); else am.set(AlarmManager.RTC_WAKEUP,t.getTimeInMillis(),pi);
 }
 public static void cancel(Context ctx){AlarmManager am=(AlarmManager)ctx.getApplicationContext().getSystemService(Context.ALARM_SERVICE);if(am==null)return;Intent i=new Intent(ctx,ReminderReceiver.class).setAction(ACTION);PendingIntent pi=PendingIntent.getBroadcast(ctx,REQUEST,i,PendingIntent.FLAG_UPDATE_CURRENT|flag());am.cancel(pi);}
 private static int flag(){return Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0;}
 public static void fireNow(Context ctx){ReminderService.start(ctx);}
}