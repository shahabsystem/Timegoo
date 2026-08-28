package ir.hamed.klox;
import android.content.*;
public class ReminderReceiver extends BroadcastReceiver{
 public void onReceive(Context c,Intent i){if(!ReminderManager.class.getName().isEmpty()){ReminderService.start(c);ReminderManager.schedule(c);}}
}