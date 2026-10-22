package ir.hamed.klox;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimegooWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_timegoo);
            String date = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date());
            views.setTextViewText(R.id.widgetDate, "تاریخ میلادی: " + persian(date));
            Intent launch = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, 701, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetRoot, pi);
            manager.updateAppWidget(id, views);
        }
    }
    private static String persian(String s) {
        return s.replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳')
                .replace('4','۴').replace('5','۵').replace('6','۶').replace('7','۷')
                .replace('8','۸').replace('9','۹');
    }
}
