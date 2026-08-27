package ir.hamed.klox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String EXTRA_SLOT = "reminder_slot";

    @Override
    public void onReceive(Context context, Intent intent) {
        int slot = intent != null ? intent.getIntExtra(EXTRA_SLOT, 1) : 1;
        ReminderService.start(context, slot);
        ReminderManager.schedule(context);
    }
}
