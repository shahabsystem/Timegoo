package ir.hamed.klox;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private TextView clock, date, statusText;
    private AudioTimeSpeaker speaker;
    private SharedPreferences prefs;
    private TextView weatherCity, weatherSummary;
    private LinearLayout weatherForecast;
    private final java.util.concurrent.ExecutorService weatherExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
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
        weatherCity = findViewById(R.id.weatherCity);
        weatherSummary = findViewById(R.id.weatherSummary);
        weatherForecast = findViewById(R.id.weatherForecast);
        applyPrefs();
        weatherCity.setText(prefs.getString("weatherCityDisplay", "تهران"));
        findViewById(R.id.weatherRefresh).setOnClickListener(v -> loadWeather());
        findViewById(R.id.weatherChangeCity).setOnClickListener(v -> showCityDialog());
        loadWeather();
        speaker = new AudioTimeSpeaker(this);
        findViewById(R.id.speak).setOnClickListener(v -> speakTime());
        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.bingBackgrounds).setOnClickListener(v -> {
            try {
                Intent bing = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.bing.com/images/search?q=beautiful+wallpapers+landscape+4k"));
                startActivity(bing);
                Toast.makeText(this, "تصویر را از Bing ذخیره کنید؛ سپس در تنظیمات، «انتخاب تصویر پس‌زمینه» را بزنید.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "مرورگر برای باز کردن Bing پیدا نشد.", Toast.LENGTH_SHORT).show();
            }
        });
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

    private void showCityDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString("weatherCityQuery", "Tehran"));
        input.setHint("مثلاً: تهران یا شیراز");
        new AlertDialog.Builder(this)
                .setTitle("شهر هواشناسی")
                .setMessage("نام شهر را وارد کنید")
                .setView(input)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره و دریافت", (dialog, which) -> {
                    String city = input.getText().toString().trim();
                    if (!city.isEmpty()) {
                        String display = city;
                        if (city.equalsIgnoreCase("Tehran")) display = "تهران";
                        prefs.edit().putString("weatherCityQuery", city).putString("weatherCityDisplay", display).apply();
                        weatherCity.setText(display);
                        loadWeather();
                    }
                }).show();
    }

    private void loadWeather() {
        if (weatherSummary == null || weatherForecast == null) return;
        weatherSummary.setText("در حال دریافت پیش‌بینی هوا...");
        weatherForecast.removeAllViews();
        String city = prefs.getString("weatherCityQuery", "Tehran");
        weatherExecutor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(city, "UTF-8");
                JSONObject geo = fetchJson("https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=fa&format=json");
                JSONArray results = geo.optJSONArray("results");
                if (results == null || results.length() == 0) throw new Exception("شهر پیدا نشد؛ نام شهر را بررسی کنید.");
                JSONObject place = results.getJSONObject(0);
                double lat = place.getDouble("latitude");
                double lon = place.getDouble("longitude");
                String resolvedName = place.optString("name", city);
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3";
                JSONObject forecast = fetchJson(url);
                JSONObject daily = forecast.getJSONObject("daily");
                JSONArray dates = daily.getJSONArray("time");
                JSONArray codes = daily.getJSONArray("weather_code");
                JSONArray highs = daily.getJSONArray("temperature_2m_max");
                JSONArray lows = daily.getJSONArray("temperature_2m_min");
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String display = prefs.getString("weatherCityDisplay", resolvedName);
                    weatherCity.setText(display);
                    weatherSummary.setText("پیش‌بینی سه روز آینده • " + display + "  |  منبع: Open-Meteo");
                    weatherForecast.removeAllViews();
                    for (int i = 0; i < Math.min(3, dates.length()); i++) {
                        String dateText = dates.optString(i, "");
                        String dayName = getPersianDayName(dateText, i);
                        int code = codes.optInt(i, 0);
                        String icon = weatherIcon(code);
                        String description = weatherDescription(code);
                        String high = formatTemp(highs.optDouble(i, Double.NaN));
                        String low = formatTemp(lows.optDouble(i, Double.NaN));
                        LinearLayout item = new LinearLayout(this);
                        item.setOrientation(LinearLayout.VERTICAL);
                        item.setGravity(Gravity.CENTER);
                        item.setPadding(4, 10, 4, 10);
                        GradientDrawable shape = new GradientDrawable();
                        shape.setColor(0x55202D40);
                        shape.setCornerRadius(dp(12));
                        shape.setStroke(dp(1), 0x446D829B);
                        item.setBackground(shape);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                        lp.setMargins(dp(3), 0, dp(3), 0);
                        weatherForecast.addView(item, lp);
                        addWeatherText(item, dayName, 12, 0xFFD6DCE6, true);
                        addWeatherText(item, icon, 27, 0xFFFFFFFF, false);
                        addWeatherText(item, description, 11, 0xFFD6DCE6, true);
                        addWeatherText(item, high, 17, 0xFFF4B94F, true);
                        addWeatherText(item, low, 14, 0xFFB9C5D5, false);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    weatherSummary.setText("دریافت هواشناسی ناموفق بود. اینترنت را بررسی کنید یا نام شهر را تغییر دهید.");
                    weatherForecast.removeAllViews();
                });
            }
        });
    }

    private JSONObject fetchJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Timegoo-Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    private void addWeatherText(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(2), 0, dp(2));
        parent.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private String getPersianDayName(String date, int offset) {
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            Calendar c = Calendar.getInstance();
            c.setTime(parsed);
            String[] days = {"یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"};
            return offset == 0 ? "امروز" : offset == 1 ? "فردا" : days[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception ignored) { return offset == 0 ? "امروز" : offset == 1 ? "فردا" : "روز سوم"; }
    }

    private String formatTemp(double temp) {
        if (Double.isNaN(temp)) return "—";
        return toPersian(String.valueOf((int) Math.round(temp))) + "°";
    }

    private String weatherIcon(int code) {
        if (code == 0) return "☀️";
        if (code == 1 || code == 2) return "🌤️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 67) return "🌦️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌧️";
        if (code >= 85 && code <= 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }

    private String weatherDescription(int code) {
        if (code == 0) return "آفتابی";
        if (code == 1) return "صاف";
        if (code == 2) return "نیمه‌ابری";
        if (code == 3) return "ابری";
        if (code == 45 || code == 48) return "مه";
        if (code >= 51 && code <= 57) return "نم‌نم";
        if (code >= 61 && code <= 67) return "باران";
        if (code >= 71 && code <= 77) return "برف";
        if (code >= 80 && code <= 82) return "رگبار";
        if (code >= 85 && code <= 86) return "برف‌پراکنده";
        if (code >= 95) return "رعدوبرق";
        return "متغیر";
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
    @Override protected void onDestroy(){handler.removeCallbacks(clockTicker);if(speaker!=null)speaker.stop();weatherExecutor.shutdownNow();super.onDestroy();}
}
