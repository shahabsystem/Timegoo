package ir.hamed.klox;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout root, slotContainer, reminderContainer;
    private SeekBar fontSize;
    private TextView preview;
    private static final int SLOTS = 7;
    private static final int REMINDERS = 5;
    private static final int PICK_REMINDER_AUDIO_BASE = 7100;
    private AudioTimeSpeaker speakerForTest;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        ReminderManager.migrateLegacyReminder(this);
        root = findViewById(R.id.root);
        slotContainer = findViewById(R.id.slotContainer);
        reminderContainer = findViewById(R.id.reminderContainer);

        Switch master = findViewById(R.id.master);
        master.setChecked(prefs.getBoolean("enabled", true));
        master.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("enabled", on).apply();
            if (on) ScheduleManager.scheduleNext(this); else ScheduleManager.cancel(this);
        });

        Switch shake = findViewById(R.id.shakeToSpeak);
        shake.setChecked(prefs.getBoolean("shakeToSpeak", false));
        shake.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("shakeToSpeak", on).apply();
            try {
                if (on) ShakeService.start(this);
                else stopService(new Intent(this, ShakeService.class));
            } catch (Exception e) {
                Toast.makeText(this, "اجرای سرویس تکان دادن ممکن نشد", Toast.LENGTH_SHORT).show();
            }
        });

        buildSlots();
        buildDingSelection();
        buildVolume();
        buildSpeed();
        buildDateMode();
        buildPersistentNotification();
        buildBatteryOptimization();
        buildReminders();
        buildAppearance();
        buildCreatorLinks();

        findViewById(R.id.back).setOnClickListener(v -> finish());
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am != null && !am.canScheduleExactAlarms()) {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                }
            } catch (Exception ignored) {}
        }
    }

    private void buildSlots() {
        LayoutInflater inf = LayoutInflater.from(this);
        for (int s = 0; s < SLOTS; s++) {
            View v = inf.inflate(R.layout.slot_item, slotContainer, false);
            slotContainer.addView(v);
            final int idx = s;
            TextView title = v.findViewById(R.id.slotTitle);
            Switch en = v.findViewById(R.id.enabled);
            Button start = v.findViewById(R.id.start), end = v.findViewById(R.id.end);
            SeekBar bar = v.findViewById(R.id.interval);
            TextView label = v.findViewById(R.id.intervalLabel);
            title.setText("بازه " + (s + 1));
            boolean def = s == 0;
            en.setChecked(prefs.getBoolean("slot" + s + "Enabled", def));
            int defStart = s == 0 ? 420 : s == 1 ? 840 : s == 2 ? 1200 : 0;
            int defEnd = s == 0 ? 600 : s == 1 ? 1200 : s == 2 ? 1440 : s == 3 ? 360 : 60;
            int st = prefs.getInt("slot" + s + "Start", defStart);
            int ed = prefs.getInt("slot" + s + "End", defEnd);
            int inter = prefs.getInt("slot" + s + "Interval", 30);
            start.setText("شروع: " + fmt(st));
            end.setText("پایان: " + fmt(ed));
            bar.setProgress(Math.max(0, Math.min(119, inter - 1)));
            label.setText("فاصله اعلام: " + fmtNumber(inter) + " دقیقه");
            en.setOnCheckedChangeListener((x, on) -> {
                prefs.edit().putBoolean("slot" + idx + "Enabled", on).apply();
                ScheduleManager.scheduleNext(this);
            });
            start.setOnClickListener(x -> pickTime(idx, true, start));
            end.setOnClickListener(x -> pickTime(idx, false, end));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                    int m = p + 1;
                    label.setText("فاصله اعلام: " + fmtNumber(m) + " دقیقه");
                    if (fromUser) prefs.edit().putInt("slot" + idx + "Interval", m).apply();
                }
                public void onStartTrackingTouch(SeekBar b) {}
                public void onStopTrackingTouch(SeekBar b) {
                    prefs.edit().putInt("slot" + idx + "Interval", b.getProgress() + 1).apply();
                    ScheduleManager.scheduleNext(SettingsActivity.this);
                }
            });
        }
    }

    private void pickTime(int idx, boolean startFlag, Button button) {
        int old = prefs.getInt("slot" + idx + (startFlag ? "Start" : "End"), startFlag ? 420 : 600);
        int h = (old / 60) % 24, m = old % 60;
        new TimePickerDialog(this, (view, hh, mm) -> {
            int val = hh * 60 + mm;
            prefs.edit().putInt("slot" + idx + (startFlag ? "Start" : "End"), val).apply();
            button.setText((startFlag ? "شروع: " : "پایان: ") + fmt(val));
            ScheduleManager.scheduleNext(this);
        }, h, m, true).show();
    }

    private void buildDingSelection() {
        RadioGroup group = findViewById(R.id.dingChoices);
        int saved = prefs.getInt("dingNumber", 1);
        int[] ids = {R.id.ding1, R.id.ding2, R.id.ding3, R.id.ding4, R.id.ding5};
        if (saved < 1 || saved > 5) saved = 1;
        group.check(ids[saved - 1]);
        group.setOnCheckedChangeListener((g, id) -> {
            for (int i = 0; i < ids.length; i++) {
                if (id == ids[i]) { prefs.edit().putInt("dingNumber", i + 1).apply(); break; }
            }
        });
        findViewById(R.id.testDing).setOnClickListener(v -> {
            if (speakerForTest == null) speakerForTest = new AudioTimeSpeaker(this);
            speakerForTest.testSelectedDing();
        });
    }

    private void buildDateMode() {
        RadioGroup g = findViewById(R.id.dateMode);
        String saved = prefs.getString("dateMode", "jalali");
        g.check("gregorian".equals(saved) ? R.id.dateGregorian : R.id.dateJalali);
        g.setOnCheckedChangeListener((x, id) -> prefs.edit().putString("dateMode", id == R.id.dateGregorian ? "gregorian" : "jalali").apply());
    }

    private void buildPersistentNotification() {
        Switch sw = findViewById(R.id.persistentNotification);
        sw.setChecked(prefs.getBoolean("persistentNotification", false));
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean("persistentNotification", on).apply();
            MainActivity.updatePersistentNotification(this);
        });
    }

    private void buildBatteryOptimization() {
        TextView status = findViewById(R.id.batteryStatus);
        Button button = findViewById(R.id.batterySettings);
        updateBatteryStatus(status);
        button.setOnClickListener(v -> {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                    if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } else {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }
                } else startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e) {
                try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                catch (Exception ignored) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }
        });
    }

    private void updateBatteryStatus(TextView status) {
        if (Build.VERSION.SDK_INT >= 23) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            boolean ok = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            status.setText(ok ? "وضعیت باتری: محدودیت بهینه‌سازی برای این برنامه حذف شده است ✓" : "وضعیت باتری: ممکن است سیستم اجرای پس‌زمینه را محدود کند");
        } else status.setText("وضعیت باتری: این نسخه اندروید محدودیت بهینه‌سازی باتری ندارد");
    }

    private void buildReminders() {
        LayoutInflater inf = LayoutInflater.from(this);
        for (int slot = 1; slot <= REMINDERS; slot++) {
            View v = inf.inflate(R.layout.reminder_item, reminderContainer, false);
            reminderContainer.addView(v);
            final int index = slot;
            String key = "reminder" + slot;
            TextView title = v.findViewById(R.id.reminderTitle);
            Switch enabled = v.findViewById(R.id.reminderEnabled);
            EditText text = v.findViewById(R.id.reminderText);
            Button time = v.findViewById(R.id.reminderStart);
            Button pickAudio = v.findViewById(R.id.pickReminderAudio);
            Button test = v.findViewById(R.id.testReminder);
            TextView end = v.findViewById(R.id.reminderEnd);
            TextView intervalLabel = v.findViewById(R.id.reminderIntervalLabel);
            SeekBar interval = v.findViewById(R.id.reminderInterval);
            end.setVisibility(View.GONE);
            intervalLabel.setVisibility(View.GONE);
            interval.setVisibility(View.GONE);

            title.setText("یادآوری " + fmtNumber(slot));
            enabled.setChecked(prefs.getBoolean(key + "Enabled", false));
            text.setText(prefs.getString(key + "Text", ""));
            int minute = prefs.getInt(key + "Minute", 9 * 60);
            time.setText("زمان: " + fmt(minute));
            String audio = prefs.getString(key + "AudioUri", "");
            pickAudio.setText(audio.isEmpty() ? "انتخاب صدای این یادآوری" : "صدای انتخاب‌شده ✓  (تغییر صدا)");

            enabled.setOnCheckedChangeListener((button, on) -> {
                saveReminder(index, text, on);
            });
            text.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) saveReminder(index, text, enabled.isChecked());
            });
            time.setOnClickListener(view -> {
                int old = prefs.getInt(key + "Minute", 9 * 60);
                new TimePickerDialog(this, (tp, h, m) -> {
                    int val = h * 60 + m;
                    prefs.edit().putInt(key + "Minute", val).apply();
                    time.setText("زمان: " + fmt(val));
                    saveReminder(index, text, enabled.isChecked());
                }, old / 60, old % 60, true).show();
            });
            pickAudio.setOnClickListener(view -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/*");
                startActivityForResult(i, PICK_REMINDER_AUDIO_BASE + index);
            });
            test.setOnClickListener(view -> ReminderManager.fireNow(this, index));
        }
    }

    private void saveReminder(int slot, EditText text, boolean enabled) {
        String key = "reminder" + slot;
        prefs.edit().putBoolean(key + "Enabled", enabled).putString(key + "Text", text.getText().toString().trim()).apply();
        ReminderManager.schedule(this);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode > PICK_REMINDER_AUDIO_BASE && requestCode <= PICK_REMINDER_AUDIO_BASE + REMINDERS
                && resultCode == RESULT_OK && data != null && data.getData() != null) {
            int slot = requestCode - PICK_REMINDER_AUDIO_BASE;
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            prefs.edit().putString("reminder" + slot + "AudioUri", u.toString()).apply();
            Toast.makeText(this, "صدای یادآوری " + fmtNumber(slot) + " انتخاب شد", Toast.LENGTH_SHORT).show();
            ReminderManager.schedule(this);
        }
    }

    private void buildVolume() {
        SeekBar v = findViewById(R.id.volume); TextView l = findViewById(R.id.volumeLabel);
        int saved = prefs.getInt("announcementVolume", 80);
        v.setProgress(Math.max(0, Math.min(80, saved - 20))); l.setText(fmtNumber(saved) + "٪");
        v.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) { int val = p + 20; l.setText(fmtNumber(val) + "٪"); if (f) prefs.edit().putInt("announcementVolume", val).apply(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { prefs.edit().putInt("announcementVolume", s.getProgress() + 20).apply(); }
        });
    }

    private void buildSpeed() {
        SeekBar v = findViewById(R.id.speed); TextView l = findViewById(R.id.speedLabel);
        int saved = Math.max(70, Math.min(130, prefs.getInt("announcementSpeed", 100)));
        v.setProgress(saved - 70); l.setText(fmtNumber(saved) + "٪");
        v.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) { int val = p + 70; l.setText(fmtNumber(val) + "٪"); if (f) prefs.edit().putInt("announcementSpeed", val).apply(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { prefs.edit().putInt("announcementSpeed", s.getProgress() + 70).apply(); }
        });
    }

    private void buildAppearance() {
        fontSize = findViewById(R.id.fontSize); preview = findViewById(R.id.fontPreview);
        float saved = prefs.getFloat("fontSize", 56);
        fontSize.setProgress(Math.max(0, Math.min(36, Math.round(saved - 32))));
        preview.setTextSize(saved / 2);
        fontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) { float z = 32 + p; prefs.edit().putFloat("fontSize", z).apply(); preview.setTextSize(z / 2); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        RadioGroup colors = findViewById(R.id.colors);
        int c = prefs.getInt("color", 0xffd89b2b);
        if (c == 0xff66bb6a) colors.check(R.id.green); else if (c == 0xffce93d8) colors.check(R.id.purple); else if (c == 0xff8ab4f8) colors.check(R.id.blue); else colors.check(R.id.orange);
        colors.setOnCheckedChangeListener((g, id) -> {
            int col = id == R.id.green ? 0xff66bb6a : id == R.id.purple ? 0xffce93d8 : id == R.id.blue ? 0xff8ab4f8 : 0xffd89b2b;
            prefs.edit().putInt("color", col).apply(); preview.setTextColor(col);
        });
        try { Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/YEKAN.TTF"); applyTypeface(root, tf); } catch (Exception ignored) {}
    }

    private void buildCreatorLinks() {
        findViewById(R.id.creatorEmail).setOnClickListener(v -> open("mailto:hamedmohammadinikche@gmail.com"));
        findViewById(R.id.creatorGithub).setOnClickListener(v -> open("https://github.com/shahabsystem"));
        findViewById(R.id.coffee).setOnClickListener(v -> open("https://coffeebede.com/shahabsystem"));
        findViewById(R.id.remit).setOnClickListener(v -> open("https://reymit.ir/shahabsystem"));
    }

    private void open(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) {}
    }

    private String fmt(int m) {
        m = ((m % 1440) + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
                .replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳').replace('4','۴')
                .replace('5','۵').replace('6','۶').replace('7','۷').replace('8','۸').replace('9','۹');
    }

    private String fmtNumber(int n) { return String.valueOf(n).replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳').replace('4','۴').replace('5','۵').replace('6','۶').replace('7','۷').replace('8','۸').replace('9','۹'); }

    @Override protected void onResume() {
        super.onResume();
        TextView status = findViewById(R.id.batteryStatus);
        if (status != null) updateBatteryStatus(status);
    }

    @Override protected void onDestroy() { if (speakerForTest != null) speakerForTest.stop(); super.onDestroy(); }

    private void applyTypeface(View v, Typeface tf) {
        if (v instanceof TextView) ((TextView) v).setTypeface(tf);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) applyTypeface(((ViewGroup) v).getChildAt(i), tf);
    }
}
