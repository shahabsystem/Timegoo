package ir.hamed.klox;
import android.content.*;
public class BootReceiver extends BroadcastReceiver{@Override public void onReceive(Context context,Intent intent){String a=intent.getAction();if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a))ScheduleManager.scheduleNext(context);ReminderManager.schedule(context);MainActivity.updatePersistentNotification(context);}}
