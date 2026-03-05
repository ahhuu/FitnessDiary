package com.cz.fitnessdiary.utils;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.cz.fitnessdiary.receiver.ReminderReceiver;

import java.util.Calendar;
import java.util.List;

/**
 * 提醒管理类
 * 同时兼容旧训练提醒与新记录模块提醒
 */
public class ReminderManager {

    private static final String PREF_NAME = "training_reminder_prefs";
    private static final String KEY_ENABLED = "reminder_enabled";
    private static final String KEY_HOUR = "reminder_hour";
    private static final String KEY_MINUTE = "reminder_minute";
    private static final String KEY_AUTOSTART_GUIDED = "autostart_guided";

    public static final String ACTION_REMINDER = "com.cz.fitnessdiary.ACTION_TRAINING_REMINDER";
    public static final String ACTION_RECORD_REMINDER = "com.cz.fitnessdiary.ACTION_RECORD_REMINDER";

    public static final String EXTRA_SCHEDULE_ID = "extra_schedule_id";
    public static final String EXTRA_MODULE_TYPE = "extra_module_type";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_TARGET_ID = "extra_target_id";
    public static final String EXTRA_CONTENT = "extra_content";

    /**
     * 设置或更新旧版每日训练提醒
     */
    public static void setReminder(Context context, int hour, int minute) {
        saveSettings(context, true, hour, minute);

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && !notificationManager.areNotificationsEnabled()) {
            Toast.makeText(context, "通知权限未开启，提醒可能无法弹出。", Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        scheduleExact(context, pendingIntent, hour, minute);
    }

    /**
     * 取消旧版每日训练提醒
     */
    public static void cancelReminder(Context context) {
        saveSettings(context, false, getReminderHour(context), getReminderMinute(context));

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        alarmManager.cancel(pendingIntent);
    }

    /**
     * 恢复旧版训练提醒
     */
    public static void restoreReminder(Context context) {
        if (isReminderEnabled(context)) {
            setReminder(context, getReminderHour(context), getReminderMinute(context));
        }
    }

    /**
     * 新版：按 scheduleId 进行独立提醒调度
     */
    public static void schedule(Context context, ReminderSchedule schedule) {
        if (schedule == null || !schedule.isEnabled()) {
            return;
        }

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_RECORD_REMINDER);
        intent.putExtra(EXTRA_SCHEDULE_ID, schedule.getId());
        intent.putExtra(EXTRA_MODULE_TYPE, schedule.getModuleType());
        intent.putExtra(EXTRA_TITLE, schedule.getTitle());
        intent.putExtra(EXTRA_TARGET_ID, schedule.getTargetId());
        intent.putExtra(EXTRA_CONTENT, schedule.getContent());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) schedule.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        scheduleExact(context, pendingIntent, schedule.getHour(), schedule.getMinute());
    }

    public static void cancel(Context context, long scheduleId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_RECORD_REMINDER);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) scheduleId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        alarmManager.cancel(pendingIntent);
    }

    /**
     * 重启或提醒触发后重建所有启用中的提醒
     */
    public static void restoreAll(Context context) {
        List<ReminderSchedule> schedules = AppDatabase.getInstance(context)
                .reminderScheduleDao()
                .getEnabledSchedulesSync();
        if (schedules == null) {
            return;
        }

        for (ReminderSchedule schedule : schedules) {
            schedule(context, schedule);
        }
    }

    private static void scheduleExact(Context context, PendingIntent pendingIntent, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                        calendar.getTimeInMillis(),
                        pendingIntent);
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                        pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "闹钟设置失败，请检查精确闹钟权限", Toast.LENGTH_LONG).show();
        }
    }

    private static void saveSettings(Context context, boolean enabled, int hour, int minute) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pref.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
    }

    public static boolean isReminderEnabled(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static int getReminderHour(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_HOUR, 19);
    }

    public static int getReminderMinute(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_MINUTE, 0);
    }

    public static String getFormattedTime(Context context) {
        return String.format("%02d:%02d", getReminderHour(context), getReminderMinute(context));
    }

    public static boolean isAutoStartGuided(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART_GUIDED, false);
    }

    public static void setAutoStartGuided(Context context, boolean guided) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTOSTART_GUIDED, guided)
                .apply();
    }
}
