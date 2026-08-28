package ir.hamed.klox;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** Schedules up to five independent daily reminders. */
public final class ReminderManager {
    private static final int REMINDER_COUNT = 5;
    private static final int REQUEST_BASE = 8301;
    private static final String ACTION_PREFIX = "ir.hamed.klox.REMINDER_";

    private ReminderManager() {}

    public static void schedule(Context ctx) {
        Context c = ctx.getApplicationContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        for (int slot = 1; slot <= REMINDER_COUNT; slot++) {
            scheduleOne(c, p, am, slot);
        }
    }

    private static void scheduleOne(Context c, SharedPreferences p, AlarmManager am, int slot) {
        cancelOne(c, am, slot);

        String prefix = key(slot);
        if (!p.getBoolean(prefix + "Enabled", false)) return;
        String text = p.getString(prefix + "Text", "").trim();
        if (text.isEmpty()) return;

        int min = Math.max(0, Math.min(1439, p.getInt(prefix + "Minute", 9 * 60)));
        Calendar now = Calendar.getInstance();
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.HOUR_OF_DAY, min / 60);
        target.set(Calendar.MINUTE, min % 60);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        int daysMask = p.getInt(prefix + "DaysMask", 127);
        if (daysMask == 0) daysMask = 127;
        // Find the next selected weekday at the configured time. Calendar uses Sunday=1..Saturday=7.
        boolean found = false;
        for (int d = 0; d < 8; d++) {
            int dow = target.get(Calendar.DAY_OF_WEEK);
            if (target.after(now) && (daysMask & (1 << (dow - 1))) != 0) {
                found = true;
                break;
            }
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        if (!found) return;

        Intent intent = new Intent(c, ReminderReceiver.class)
                .setAction(ACTION_PREFIX + slot)
                .putExtra(ReminderReceiver.EXTRA_SLOT, slot);
        PendingIntent pi = PendingIntent.getBroadcast(
                c, REQUEST_BASE + slot, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentFlags());

        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
        }
    }

    public static void cancel(Context ctx) {
        Context c = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int slot = 1; slot <= REMINDER_COUNT; slot++) cancelOne(c, am, slot);
    }

    private static void cancelOne(Context c, AlarmManager am, int slot) {
        Intent intent = new Intent(c, ReminderReceiver.class)
                .setAction(ACTION_PREFIX + slot)
                .putExtra(ReminderReceiver.EXTRA_SLOT, slot);
        PendingIntent pi = PendingIntent.getBroadcast(
                c, REQUEST_BASE + slot, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentFlags());
        am.cancel(pi);
    }

    public static void fireNow(Context ctx, int slot) {
        if (slot < 1 || slot > REMINDER_COUNT) slot = 1;
        ReminderService.start(ctx, slot);
    }

    /** Backward-compatible test call. */
    public static void fireNow(Context ctx) {
        fireNow(ctx, 1);
    }

    private static String key(int slot) {
        return "reminder" + slot;
    }

    private static int pendingIntentFlags() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    public static void migrateLegacyReminder(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (p.getBoolean("reminderMigrationDone", false)) return;
        boolean legacyEnabled = p.getBoolean("reminderEnabled", false);
        String legacyText = p.getString("reminderText", "").trim();
        if (legacyEnabled || !legacyText.isEmpty()) {
            SharedPreferences.Editor e = p.edit()
                    .putBoolean("reminder1Enabled", legacyEnabled)
                    .putString("reminder1Text", legacyText)
                    .putInt("reminder1Minute", p.getInt("reminderMinute", 9 * 60))
                    .putInt("reminder1DaysMask", p.getInt("reminderDaysMask", 127));
            String audio = p.getString("reminderAudioUri", "");
            if (!audio.isEmpty()) e.putString("reminder1AudioUri", audio);
            e.apply();
        }
        p.edit().putBoolean("reminderMigrationDone", true).apply();
    }
}
