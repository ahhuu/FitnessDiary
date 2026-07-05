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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 提醒管理类
 * 兼容旧训练提醒（已废弃）与新智能推送（含4子项独立配置）
 */
public class ReminderManager {

    private static final String PREF_NAME = "training_reminder_prefs";

    // ── 旧版训练提醒（@Deprecated，保留用于 BootReceiver 兼容） ──
    private static final String KEY_ENABLED = "reminder_enabled";
    private static final String KEY_HOUR = "reminder_hour";
    private static final String KEY_MINUTE = "reminder_minute";
    private static final String KEY_AUTOSTART_GUIDED = "autostart_guided";

    // ── 智能推送总开关 ──
    private static final String KEY_SMART_REMINDER_ENABLED = "smart_reminder_enabled";

    // ── 早晨概要 ──
    private static final String KEY_MORNING_ENABLED = "smart_morning_enabled";
    private static final String KEY_MORNING_HOUR    = "smart_morning_hour";
    private static final String KEY_MORNING_MINUTE  = "smart_morning_minute";

    // ── 晚间提醒 ──
    private static final String KEY_EVENING_ENABLED = "smart_evening_enabled";
    private static final String KEY_EVENING_HOUR    = "smart_evening_hour";
    private static final String KEY_EVENING_MINUTE  = "smart_evening_minute";

    // ── 健康周报 ──
    private static final String KEY_WEEKLY_ENABLED     = "smart_weekly_enabled";
    private static final String KEY_WEEKLY_DAY_OF_WEEK = "smart_weekly_day";
    private static final String KEY_WEEKLY_HOUR        = "smart_weekly_hour";
    private static final String KEY_WEEKLY_MINUTE      = "smart_weekly_minute";

    // ── 不活跃挽留 ──
    private static final String KEY_INACTIVITY_ENABLED = "smart_inactivity_enabled";
    private static final String KEY_INACTIVITY_HOUR    = "smart_inactivity_hour";
    private static final String KEY_INACTIVITY_MINUTE  = "smart_inactivity_minute";

    // ── Action 常量 ──
    public static final String ACTION_REMINDER        = "com.cz.fitnessdiary.ACTION_TRAINING_REMINDER";
    public static final String ACTION_RECORD_REMINDER = "com.cz.fitnessdiary.ACTION_RECORD_REMINDER";
    public static final String ACTION_MORNING_SUMMARY = "com.cz.fitnessdiary.ACTION_MORNING_SUMMARY";
    public static final String ACTION_EVENING_REMINDER= "com.cz.fitnessdiary.ACTION_EVENING_REMINDER";
    public static final String ACTION_INACTIVITY_NUDGE= "com.cz.fitnessdiary.ACTION_INACTIVITY_NUDGE";
    public static final String ACTION_WEEKLY_REPORT   = "com.cz.fitnessdiary.ACTION_WEEKLY_REPORT";
    public static final String ACTION_SMART_WELCOME   = "com.cz.fitnessdiary.ACTION_SMART_WELCOME";

    public static final String EXTRA_SCHEDULE_ID  = "extra_schedule_id";
    public static final String EXTRA_MODULE_TYPE  = "extra_module_type";
    public static final String EXTRA_TITLE        = "extra_title";
    public static final String EXTRA_TARGET_ID    = "extra_target_id";
    public static final String EXTRA_CONTENT      = "extra_content";

    // ────────────────────────────────────────────────
    // 旧版训练提醒（保留兼容，UI 层已不再使用）
    // ────────────────────────────────────────────────

    /** @deprecated 已由智能推送替代 */
    @Deprecated
    public static void setReminder(Context context, int hour, int minute) {
        saveSettings(context, true, hour, minute);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        PendingIntent pendingIntent = buildPendingBroadcast(context, 0, intent);
        scheduleExact(context, pendingIntent, hour, minute);
    }

    /** @deprecated 已由智能推送替代 */
    @Deprecated
    public static void cancelReminder(Context context) {
        saveSettings(context, false, getReminderHour(context), getReminderMinute(context));
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        alarmManager.cancel(buildPendingBroadcast(context, 0, intent));
    }

    /** 开机恢复旧版训练提醒（BootReceiver 调用） */
    public static void restoreReminder(Context context) {
        if (isReminderEnabled(context)) {
            setReminder(context, getReminderHour(context), getReminderMinute(context));
        }
    }

    // ────────────────────────────────────────────────
    // 新版记录模块提醒（按 scheduleId 独立调度）
    // ────────────────────────────────────────────────

    public static void schedule(Context context, ReminderSchedule schedule) {
        if (schedule == null || !schedule.isEnabled()) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_RECORD_REMINDER);
        intent.putExtra(EXTRA_SCHEDULE_ID, schedule.getId());
        intent.putExtra(EXTRA_MODULE_TYPE, schedule.getModuleType());
        intent.putExtra(EXTRA_TITLE, schedule.getTitle());
        intent.putExtra(EXTRA_TARGET_ID, schedule.getTargetId());
        intent.putExtra(EXTRA_CONTENT, schedule.getContent());
        PendingIntent pendingIntent = buildPendingBroadcast(context, (int) schedule.getId(), intent);
        scheduleExact(context, pendingIntent, schedule.getHour(), schedule.getMinute());
    }

    public static void cancel(Context context, long scheduleId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_RECORD_REMINDER);
        alarmManager.cancel(buildPendingBroadcast(context, (int) scheduleId, intent));
    }

    public static void restoreAll(Context context) {
        List<ReminderSchedule> schedules = AppDatabase.getInstance(context)
                .reminderScheduleDao().getEnabledSchedulesSync();
        if (schedules == null) return;
        for (ReminderSchedule s : schedules) schedule(context, s);
    }

    // ────────────────────────────────────────────────
    // 新版多日调度（按 repeatDays 每日独立 PendingIntent）
    // ────────────────────────────────────────────────

    /**
     * Schedule a reminder based on ReminderSchedule entity, with multi-day support
     * based on repeatDays. Creates one PendingIntent per day of the week.
     */
    public static void scheduleReminder(Context context, ReminderSchedule schedule) {
        if (schedule == null || !schedule.isEnabled()) return;

        int[] repeatDays = parseRepeatDays(schedule.getRepeatDays());
        int requestCodeBase = (int) (schedule.getId() * 100);

        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            boolean found = false;
            if (repeatDays != null && repeatDays.length > 0) {
                for (int d : repeatDays) {
                    if (d == dayOffset) { found = true; break; }
                }
            } else {
                found = true;
            }
            if (!found) continue;

            int requestCode = requestCodeBase + dayOffset;
            Intent intent = buildReminderIntent(context, schedule);
            PendingIntent pendingIntent = buildPendingBroadcast(context, requestCode, intent);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_WEEK, dayOffset + 1);
            calendar.set(Calendar.HOUR_OF_DAY, schedule.getHour());
            calendar.set(Calendar.MINUTE, schedule.getMinute());
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    alarmManager.setAlarmClock(
                            new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), pendingIntent),
                            pendingIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                }
            } catch (SecurityException e) {
                Toast.makeText(context, "闹钟设置失败，请检查精确闹钟权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Cancel all day-of-week PendingIntents for a given ReminderSchedule
     */
    public static void cancelReminder(Context context, ReminderSchedule schedule) {
        if (schedule == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int requestCodeBase = (int) (schedule.getId() * 100);
        Intent baseIntent = new Intent(context, ReminderReceiver.class);
        baseIntent.setAction(ACTION_RECORD_REMINDER);
        for (int i = 0; i < 7; i++) {
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCodeBase + i, baseIntent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }

    /**
     * Build an Intent for ReminderReceiver with all schedule extras
     */
    private static Intent buildReminderIntent(Context context, ReminderSchedule schedule) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_RECORD_REMINDER);
        intent.putExtra(EXTRA_SCHEDULE_ID, schedule.getId());
        intent.putExtra(EXTRA_MODULE_TYPE, schedule.getModuleType());
        intent.putExtra(EXTRA_TITLE, schedule.getTitle());
        intent.putExtra(EXTRA_CONTENT, schedule.getContent());
        intent.putExtra(EXTRA_TARGET_ID, schedule.getTargetId());
        return intent;
    }

    /**
     * Parse comma-separated repeatDays string into int array (0=Sunday .. 6=Saturday)
     */
    private static int[] parseRepeatDays(String repeatDaysStr) {
        if (repeatDaysStr == null || repeatDaysStr.isEmpty()) return new int[0];
        String[] parts = repeatDaysStr.split(",");
        int[] days = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                days[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                days[i] = 0;
            }
        }
        return days;
    }

    /**
     * Restore all enabled ReminderSchedules using the multi-day scheduleReminder method.
     * Runs on a background thread via Executors.
     */
    public static void restoreAllReminders(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            List<ReminderSchedule> schedules = db.reminderScheduleDao().getEnabledSchedulesSync();
            for (ReminderSchedule s : schedules) {
                scheduleReminder(context, s);
            }
        });
        executor.shutdown();
    }

    // ────────────────────────────────────────────────
    // 智能推送：总开关
    // ────────────────────────────────────────────────

    public static boolean isSmartReminderEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SMART_REMINDER_ENABLED, false);
    }

    public static void setSmartReminderEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SMART_REMINDER_ENABLED, enabled).apply();
        if (enabled) {
            restoreSmartReminders(context);
        } else {
            cancelSmartReminders(context);
        }
    }

    /** 重启后 / 触发后重建所有启用的智能提醒 */
    public static void restoreSmartReminders(Context context) {
        if (isMorningEnabled(context))    scheduleMorningSummary(context);
        if (isEveningEnabled(context))    scheduleEveningReminder(context);
        if (isInactivityEnabled(context)) scheduleInactivityNudge(context);
        if (isWeeklyEnabled(context))     scheduleWeeklyReport(context);
    }

    /** 取消全部智能提醒 */
    public static void cancelSmartReminders(Context context) {
        cancelSmartAction(context, ACTION_MORNING_SUMMARY,  100);
        cancelSmartAction(context, ACTION_EVENING_REMINDER, 101);
        cancelSmartAction(context, ACTION_INACTIVITY_NUDGE, 102);
        cancelSmartAction(context, ACTION_WEEKLY_REPORT,    103);
    }

    // ────────────────────────────────────────────────
    // 智能推送：早晨概要
    // ────────────────────────────────────────────────

    public static boolean isMorningEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MORNING_ENABLED, true);
    }

    public static void setMorningEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MORNING_ENABLED, enabled).apply();
        if (isSmartReminderEnabled(context)) {
            if (enabled) scheduleMorningSummary(context);
            else cancelSmartAction(context, ACTION_MORNING_SUMMARY, 100);
        }
    }

    public static int getMorningHour(Context context) {
        return prefs(context).getInt(KEY_MORNING_HOUR, 8);
    }

    public static int getMorningMinute(Context context) {
        return prefs(context).getInt(KEY_MORNING_MINUTE, 0);
    }

    public static void setMorningTime(Context context, int hour, int minute) {
        prefs(context).edit()
                .putInt(KEY_MORNING_HOUR, hour)
                .putInt(KEY_MORNING_MINUTE, minute)
                .apply();
        if (isSmartReminderEnabled(context) && isMorningEnabled(context)) {
            scheduleMorningSummary(context);
        }
    }

    public static void scheduleMorningSummary(Context context) {
        scheduleSmartAction(context, ACTION_MORNING_SUMMARY, 100,
                getMorningHour(context), getMorningMinute(context));
    }

    // ────────────────────────────────────────────────
    // 智能推送：晚间提醒
    // ────────────────────────────────────────────────

    public static boolean isEveningEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EVENING_ENABLED, true);
    }

    public static void setEveningEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EVENING_ENABLED, enabled).apply();
        if (isSmartReminderEnabled(context)) {
            if (enabled) scheduleEveningReminder(context);
            else cancelSmartAction(context, ACTION_EVENING_REMINDER, 101);
        }
    }

    public static int getEveningHour(Context context) {
        return prefs(context).getInt(KEY_EVENING_HOUR, 20);
    }

    public static int getEveningMinute(Context context) {
        return prefs(context).getInt(KEY_EVENING_MINUTE, 0);
    }

    public static void setEveningTime(Context context, int hour, int minute) {
        prefs(context).edit()
                .putInt(KEY_EVENING_HOUR, hour)
                .putInt(KEY_EVENING_MINUTE, minute)
                .apply();
        if (isSmartReminderEnabled(context) && isEveningEnabled(context)) {
            scheduleEveningReminder(context);
        }
    }

    public static void scheduleEveningReminder(Context context) {
        scheduleSmartAction(context, ACTION_EVENING_REMINDER, 101,
                getEveningHour(context), getEveningMinute(context));
    }

    // ────────────────────────────────────────────────
    // 智能推送：不活跃挽留
    // ────────────────────────────────────────────────

    public static boolean isInactivityEnabled(Context context) {
        return prefs(context).getBoolean(KEY_INACTIVITY_ENABLED, true);
    }

    public static void setInactivityEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_INACTIVITY_ENABLED, enabled).apply();
        if (isSmartReminderEnabled(context)) {
            if (enabled) scheduleInactivityNudge(context);
            else cancelSmartAction(context, ACTION_INACTIVITY_NUDGE, 102);
        }
    }

    public static int getInactivityHour(Context context) {
        return prefs(context).getInt(KEY_INACTIVITY_HOUR, 18);
    }

    public static int getInactivityMinute(Context context) {
        return prefs(context).getInt(KEY_INACTIVITY_MINUTE, 0);
    }

    public static void setInactivityTime(Context context, int hour, int minute) {
        prefs(context).edit()
                .putInt(KEY_INACTIVITY_HOUR, hour)
                .putInt(KEY_INACTIVITY_MINUTE, minute)
                .apply();
        if (isSmartReminderEnabled(context) && isInactivityEnabled(context)) {
            scheduleInactivityNudge(context);
        }
    }

    public static void scheduleInactivityNudge(Context context) {
        scheduleSmartAction(context, ACTION_INACTIVITY_NUDGE, 102,
                getInactivityHour(context), getInactivityMinute(context));
    }

    // ────────────────────────────────────────────────
    // 智能推送：健康周报
    // ────────────────────────────────────────────────

    public static boolean isWeeklyEnabled(Context context) {
        return prefs(context).getBoolean(KEY_WEEKLY_ENABLED, true);
    }

    public static void setWeeklyEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_WEEKLY_ENABLED, enabled).apply();
        if (isSmartReminderEnabled(context)) {
            if (enabled) scheduleWeeklyReport(context);
            else cancelSmartAction(context, ACTION_WEEKLY_REPORT, 103);
        }
    }

    public static int getWeeklyDayOfWeek(Context context) {
        return prefs(context).getInt(KEY_WEEKLY_DAY_OF_WEEK, Calendar.MONDAY);
    }

    public static int getWeeklyHour(Context context) {
        return prefs(context).getInt(KEY_WEEKLY_HOUR, 9);
    }

    public static int getWeeklyMinute(Context context) {
        return prefs(context).getInt(KEY_WEEKLY_MINUTE, 0);
    }

    public static void setWeeklyTime(Context context, int dayOfWeek, int hour, int minute) {
        prefs(context).edit()
                .putInt(KEY_WEEKLY_DAY_OF_WEEK, dayOfWeek)
                .putInt(KEY_WEEKLY_HOUR, hour)
                .putInt(KEY_WEEKLY_MINUTE, minute)
                .apply();
        if (isSmartReminderEnabled(context) && isWeeklyEnabled(context)) {
            scheduleWeeklyReport(context);
        }
    }

    public static void scheduleWeeklyReport(Context context) {
        scheduleSmartActionWeekly(context, ACTION_WEEKLY_REPORT, 103,
                getWeeklyDayOfWeek(context), getWeeklyHour(context), getWeeklyMinute(context));
    }

    // ────────────────────────────────────────────────
    // 智能推送：欢迎广播
    // ────────────────────────────────────────────────

    public static void sendSmartReminderWelcomeNotification(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_SMART_WELCOME);
        context.sendBroadcast(intent);
    }

    // ────────────────────────────────────────────────
    // 内部工具方法
    // ────────────────────────────────────────────────

    private static void scheduleSmartAction(Context context, String action, int requestCode,
                                             int hour, int minute) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(action);
        PendingIntent pendingIntent = buildPendingBroadcast(context, requestCode, intent);
        scheduleExact(context, pendingIntent, hour, minute);
    }

    private static void scheduleSmartActionWeekly(Context context, String action, int requestCode,
                                                   int dayOfWeek, int hour, int minute) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(action);
        PendingIntent pendingIntent = buildPendingBroadcast(context, requestCode, intent);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(
                        new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), pendingIntent),
                        pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        } catch (SecurityException ignored) {}
    }

    private static void cancelSmartAction(Context context, String action, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(action);
        alarmManager.cancel(buildPendingBroadcast(context, requestCode, intent));
    }

    private static void scheduleExact(Context context, PendingIntent pendingIntent, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

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
                alarmManager.setAlarmClock(
                        new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), pendingIntent),
                        pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "闹钟设置失败，请检查精确闹钟权限", Toast.LENGTH_LONG).show();
        }
    }

    private static PendingIntent buildPendingBroadcast(Context context, int requestCode, Intent intent) {
        return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    private static void saveSettings(Context context, boolean enabled, int hour, int minute) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── 旧版 getter（保留兼容） ──

    public static boolean isReminderEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static int getReminderHour(Context context) {
        return prefs(context).getInt(KEY_HOUR, 19);
    }

    public static int getReminderMinute(Context context) {
        return prefs(context).getInt(KEY_MINUTE, 0);
    }

    public static String getFormattedTime(Context context) {
        return String.format("%02d:%02d", getReminderHour(context), getReminderMinute(context));
    }

    public static boolean isAutoStartGuided(Context context) {
        return prefs(context).getBoolean(KEY_AUTOSTART_GUIDED, false);
    }

    public static void setAutoStartGuided(Context context, boolean guided) {
        prefs(context).edit().putBoolean(KEY_AUTOSTART_GUIDED, guided).apply();
    }
}
