package com.cz.fitnessdiary.utils;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;
import com.cz.fitnessdiary.receiver.ReminderReceiver;
import java.util.Calendar;

/**
 * 训练提醒管理类
 * 负责闹钟的设置、取消以及状态持久化
 */
public class ReminderManager {

    private static final String PREF_NAME = "training_reminder_prefs";
    private static final String KEY_ENABLED = "reminder_enabled";
    private static final String KEY_HOUR = "reminder_hour";
    private static final String KEY_MINUTE = "reminder_minute";
    private static final String KEY_AUTOSTART_GUIDED = "autostart_guided";

    public static final String ACTION_REMINDER = "com.cz.fitnessdiary.ACTION_TRAINING_REMINDER";

    /**
     * 设置或更新每日提醒
     */
    public static void setReminder(Context context, int hour, int minute) {
        saveSettings(context, true, hour, minute);

        // [v1.2] 权限预检
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && !notificationManager.areNotificationsEnabled()) {
            Toast.makeText(context, "⚠️ 通知权限未开启，提醒可能无法弹出。建议在系统设置中允许本应用“自启动”和“后台显示通知”。", Toast.LENGTH_LONG).show();
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null)
            return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        // 如果设定的时间已经过去，则设为明天
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // [v1.2] 使用 setAlarmClock 达到最高优先级 (系统任务栏会显示闹钟图标)
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
            e.printStackTrace();
            Toast.makeText(context, "⚠️ 闹钟设置失败：缺少精确闹钟权限，请在设置中开启", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 取消每日提醒
     */
    public static void cancelReminder(Context context) {
        saveSettings(context, false, getReminderHour(context), getReminderMinute(context));

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null)
            return;

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
     * 重启时恢复闹钟
     */
    public static void restoreReminder(Context context) {
        if (isReminderEnabled(context)) {
            setReminder(context, getReminderHour(context), getReminderMinute(context));
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
