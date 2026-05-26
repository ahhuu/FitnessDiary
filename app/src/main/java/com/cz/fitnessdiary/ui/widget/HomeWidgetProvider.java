package com.cz.fitnessdiary.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_WIDGET_REFRESH = "com.cz.fitnessdiary.ACTION_WIDGET_REFRESH";
    private static final String TAG = "HomeWidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_WIDGET_REFRESH.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName name = new ComponentName(context, HomeWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(name);
            if (ids != null) onUpdate(context, manager, ids);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_home);

        // Set click intents immediately (no DB needed)
        Intent mainIntent = new Intent(context, com.cz.fitnessdiary.ui.MainActivity.class);
        PendingIntent mainPi = PendingIntent.getActivity(context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_widget_date, mainPi);

        Intent dietIntent = new Intent(context, com.cz.fitnessdiary.ui.MainActivity.class);
        dietIntent.putExtra("extra_module_type", "DIET");
        views.setOnClickPendingIntent(R.id.ll_widget_diet, PendingIntent.getActivity(
                context, 1, dietIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent waterIntent = new Intent(context, com.cz.fitnessdiary.ui.MainActivity.class);
        waterIntent.putExtra("extra_module_type", "WATER");
        views.setOnClickPendingIntent(R.id.ll_widget_water, PendingIntent.getActivity(
                context, 2, waterIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent sportIntent = new Intent(context, com.cz.fitnessdiary.ui.MainActivity.class);
        sportIntent.putExtra("extra_module_type", "SPORT");
        views.setOnClickPendingIntent(R.id.ll_widget_sport, PendingIntent.getActivity(
                context, 3, sportIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent sleepIntent = new Intent(context, com.cz.fitnessdiary.ui.MainActivity.class);
        sleepIntent.putExtra("extra_module_type", "SLEEP");
        views.setOnClickPendingIntent(R.id.ll_widget_sleep, PendingIntent.getActivity(
                context, 4, sleepIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        manager.updateAppWidget(widgetId, views);

        // Load data on background thread
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                long today = DateUtils.getTodayStartTimestamp();
                long dayEnd = today + 86400000L;

                String dateText = new SimpleDateFormat("MM月dd日 E", Locale.getDefault()).format(new Date());
                views.setTextViewText(R.id.tv_widget_date, dateText);

                int completed = db.dailyLogDao().getTodayCompletedCountSync(today);
                int total = db.dailyLogDao().getTodayPlanCountSync(today);
                views.setTextViewText(R.id.tv_widget_sport_val, total > 0 ? completed + "/" + total : "--");

                int targetCal = 2000;
                com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                if (user != null && user.getDailyCalorieTarget() > 0) targetCal = user.getDailyCalorieTarget();
                int consumedCal = 0;
                java.util.List<com.cz.fitnessdiary.database.entity.FoodRecord> foods =
                        db.foodRecordDao().getByDateRangeSync(today, dayEnd);
                if (foods != null) {
                    for (com.cz.fitnessdiary.database.entity.FoodRecord f : foods) consumedCal += f.getCalories();
                }
                int dietPct = targetCal > 0 ? Math.min(consumedCal * 100 / targetCal, 100) : 0;
                views.setProgressBar(R.id.progress_widget_diet, 100, dietPct, false);
                views.setTextViewText(R.id.tv_widget_diet_val, String.valueOf(consumedCal));

                int waterMl = db.waterRecordDao().getTodayTotalSync(today, dayEnd);
                int waterPct = Math.min(waterMl * 100 / 2000, 100);
                views.setProgressBar(R.id.progress_widget_water, 100, waterPct, false);
                views.setTextViewText(R.id.tv_widget_water_val, String.valueOf(waterMl));

                float sleepHours = 0;
                java.util.List<com.cz.fitnessdiary.database.entity.SleepRecord> sleeps =
                        db.sleepRecordDao().getSleepRecordsByDateRangeSync(today, dayEnd);
                if (sleeps != null && !sleeps.isEmpty()) {
                    for (com.cz.fitnessdiary.database.entity.SleepRecord s : sleeps)
                        sleepHours += s.getDuration() / 3600f;
                }
                views.setTextViewText(R.id.tv_widget_sleep_val,
                        sleepHours > 0 ? String.format(Locale.getDefault(), "%.1fh", sleepHours) : "--");

                int streak = 0;
                for (int i = 0; i < 365; i++) {
                    long day = today - (long) i * 86400000L;
                    if (db.dailyLogDao().getTodayCompletedCountSync(day) > 0) streak++;
                    else if (i > 0) break;
                }
                views.setTextViewText(R.id.tv_widget_streak, "连续" + streak + "天");

                new Handler(Looper.getMainLooper()).post(() ->
                        manager.updateAppWidget(widgetId, views));
            } catch (Exception e) {
                Log.e(TAG, "Widget update failed", e);
            }
        }).start();
    }
}
