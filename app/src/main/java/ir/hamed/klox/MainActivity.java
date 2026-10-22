package ir.hamed.klox;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private TextView clock, date, statusText;
    private AudioTimeSpeaker speaker;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        statusText = findViewById(R.id.statusText);
        applyPrefs();
        speaker = new AudioTimeSpeaker(this);
        findViewById(R.id.speak).setOnClickListener(v -> speakTime());
        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.exit).setOnClickListener(v -> exitNow());
        ScheduleManager.scheduleNext(this);
        ReminderManager.schedule(this);
        updatePersistentNotification(this);
        if (prefs.getBoolean("shakeToSpeak", false)) {
            try { ShakeService.start(this); } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 900);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) { applyPrefs(); updateClock(); }
        handler.removeCallbacks(clockTicker);
        handler.post(clockTicker);
    }

    private void updateClock() {
        Date now = new Date();
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(now);
        String d = formattedDate(now, prefs.getString("dateMode", "jalali"));
        clock.setText(toPersian(time));
        date.setText(toPersian(d));
        boolean enabled = prefs.getBoolean("enabled", true);
        statusText.setText(enabled ? "فعال • زمان‌بندی در پس‌زمینه روشن است" : "غیرفعال");
        statusText.setTextColor(enabled ? 0xff32d889 : 0xffff6b6b);
    }

    private String toPersian(String s) {
        return s.replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳').replace('4','۴').replace('5','۵').replace('6','۶').replace('7','۷').replace('8','۸').replace('9','۹');
    }

    private void speakTime() { if (speaker != null) speaker.speakCurrentTime(); }

    private void applyPrefs() {
        float size = Math.max(72f, prefs.getFloat("fontSize", 82));
        clock.setTextSize(size);
        date.setTextSize(Math.max(30f, size * .38f));
        String font = prefs.getString("fontChoice", "yekan");
        try {
            Typeface tf;
            if ("vazir".equals(font)) tf = Typeface.createFromAsset(getAssets(), "fonts/Vazir.ttf");
            else if ("yekan".equals(font)) tf = Typeface.createFromAsset(getAssets(), "fonts/YEKAN.TTF");
            else tf = Typeface.create("serif", Typeface.NORMAL);
            clock.setTypeface(tf); date.setTypeface(tf);
        } catch (Exception ignored) {}
        int color = prefs.getInt("color", 0xffd89b2b);
        clock.setTextColor(color);
        getWindow().setStatusBarColor(0xff050b14);
        getWindow().setNavigationBarColor(0xff050b14);

        String bg = prefs.getString("homeBackground", "landscape");
        ImageView image = findViewById(R.id.homeBackground);
        String customUri = prefs.getString("homeCustomBackgroundUri", "");
        if ("custom".equals(bg) && !customUri.isEmpty()) {
            try {
                image.setImageURI(android.net.Uri.parse(customUri));
                if (image.getDrawable() != null) return;
            } catch (Exception ignored) {}
        }
        if ("plain".equals(bg)) image.setImageResource(R.drawable.bg_home_plain);
        else if ("warm".equals(bg)) image.setImageResource(R.drawable.bg_home_warm);
        else image.setImageResource(R.drawable.bg_home_landscape);
    }

    private void exitNow() { if (speaker != null) speaker.stop(); finishAndRemoveTask(); }

    private String formattedDate(Date date, String mode) {
        Calendar c = Calendar.getInstance(); c.setTime(date);
        if ("gregorian".equals(mode)) return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(date);
        int gy=c.get(Calendar.YEAR), gm=c.get(Calendar.MONTH)+1, gd=c.get(Calendar.DAY_OF_MONTH);
        int[] r=jalali(gy,gm,gd); return String.format(Locale.US,"%04d/%02d/%02d",r[0],r[1],r[2]);
    }

    private static int[] jalali(int gy,int gm,int gd){
        int[] gdm={31,(gy%4==0&&(gy%100!=0||gy%400==0))?29:28,31,30,31,30,31,31,30,31,30,31};
        int gy2=gy-1600, gm2=gm-1, gd2=gd-1; int gdn=365*gy2+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400;
        for(int i=0;i<gm2;i++)gdn+=gdm[i]; gdn+=gd2; int jdn=gdn-79; int jy=979+33*(jdn/12053); jdn%=12053; jy+=4*(jdn/1461); jdn%=1461;
        if(jdn>=366){jy+=(jdn-1)/365;jdn=(jdn-1)%365;} int jm,jd;
        if(jdn<186){jm=1+jdn/31;jd=1+jdn%31;}else{jm=7+(jdn-186)/30;jd=1+(jdn-186)%30;} return new int[]{jy,jm,jd};
    }

    public static void updatePersistentNotification(Context ctx){
        SharedPreferences p=ctx.getSharedPreferences("settings",Context.MODE_PRIVATE);
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        final String ch="clock_persistent";
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"ساعت دائمی",NotificationManager.IMPORTANCE_LOW));
        if(!p.getBoolean("persistentNotification",false)){nm.cancel(1701);return;}
        String mode=p.getString("dateMode","jalali"); Calendar now=Calendar.getInstance(); String date;
        if("gregorian".equals(mode)) date=new SimpleDateFormat("yyyy/MM/dd",Locale.US).format(now.getTime());
        else {int[] j=jalali(now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,now.get(Calendar.DAY_OF_MONTH));date=String.format(Locale.US,"%04d/%02d/%02d",j[0],j[1],j[2]);}
        String notificationText=toPersianStatic(date);
        android.widget.RemoteViews rv=new android.widget.RemoteViews(ctx.getPackageName(),R.layout.notification_large);
        rv.setTextViewText(R.id.notificationTitle,"تاریخ امروز"); rv.setTextViewText(R.id.notificationText,notificationText);
        Notification n;
        if(Build.VERSION.SDK_INT>=26)n=new Notification.Builder(ctx,ch).setSmallIcon(R.drawable.ic_status_clock).setCustomContentView(rv).setContentTitle("تاریخ امروز").setContentText(notificationText).setOngoing(true).setOnlyAlertOnce(true).build();
        else n=new Notification.Builder(ctx).setSmallIcon(R.drawable.ic_status_clock).setContent(rv).setContentTitle("تاریخ امروز").setContentText(notificationText).setOngoing(true).setOnlyAlertOnce(true).build();
        nm.notify(1701,n);
    }
    private static String toPersianStatic(String s){return s.replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳').replace('4','۴').replace('5','۵').replace('6','۶').replace('7','۷').replace('8','۸').replace('9','۹');}
    @Override protected void onDestroy(){handler.removeCallbacks(clockTicker);if(speaker!=null)speaker.stop();super.onDestroy();}
}
