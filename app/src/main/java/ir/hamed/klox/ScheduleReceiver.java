package ir.hamed.klox;
import android.content.*;import android.os.Build;
public class ScheduleReceiver extends BroadcastReceiver{public static final String ACTION_ANNOUNCE="ir.hamed.klox.ANNOUNCE";@Override public void onReceive(Context context,Intent intent){if(!ACTION_ANNOUNCE.equals(intent.getAction()))return;Intent service=new Intent(context,ClockAnnouncementService.class);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);ScheduleManager.scheduleNext(context);}}
