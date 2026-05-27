package com.cz.fitnessdiary.utils;

import android.content.Context;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.DailyLogDao;
import com.cz.fitnessdiary.database.dao.FoodRecordDao;
import com.cz.fitnessdiary.database.dao.WaterRecordDao;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.User;

import java.util.List;

/**
 * 智能提醒文案构建与条件判断
 */
public class SmartReminderHelper {

    // ── 早晨概要 ──

    public static String getMorningTitle() {
        return "早上好！今日健康目标";
    }

    public static String getMorningContent(Context context) {
        User user = getUser(context);
        int calTarget = user != null && user.getDailyCalorieTarget() > 0 ? user.getDailyCalorieTarget() : 2000;
        int waterTarget = user != null && user.getDailyWaterTarget() > 0 ? user.getDailyWaterTarget() : 2000;

        long today = DateUtils.getTodayStartTimestamp();
        AppDatabase db = AppDatabase.getInstance(context);

        // Count today's plans
        int planCount = db.dailyLogDao().getTodayPlanCountSync(today);

        return "今日目标：饮水" + waterTarget + "ml · 训练" + planCount + "项 · 热量" + calTarget + "千卡";
    }

    // ── 晚间提醒 ──

    public static String getEveningTitle(Context context) {
        int pending = countPendingItems(context);
        return pending > 0 ? "今日还有" + pending + "项未完成" : "今日任务已全部完成！";
    }

    public static String getEveningContent(Context context) {
        int pending = countPendingItems(context);
        if (pending <= 0) {
            return "今天表现很棒，好好休息，明天继续加油！";
        }
        User user = getUser(context);
        StringBuilder sb = new StringBuilder();
        long today = DateUtils.getTodayStartTimestamp();
        AppDatabase db = AppDatabase.getInstance(context);

        // Check training
        int completed = db.dailyLogDao().getTodayCompletedCountSync(today);
        int total = db.dailyLogDao().getTodayPlanCountSync(today);
        if (completed < total) {
            sb.append("运动打卡差").append(total - completed).append("项 · ");
        }

        // Check water (basic: if < 1000ml)
        int waterMl = db.waterRecordDao().getTodayTotalSync(today, today + 86400000L);
        if (waterMl < 1000) {
            int waterTarget = user != null && user.getDailyWaterTarget() > 0 ? user.getDailyWaterTarget() : 2000;
            int remaining = waterTarget - waterMl;
            if (remaining > 0) sb.append("饮水还差").append(remaining).append("ml · ");
        }

        // Check habits
        List<HabitItem> habits = db.habitItemDao().getEnabledSync();
        if (habits != null) {
            int habitPending = 0;
            for (HabitItem h : habits) {
                HabitRecord r = db.habitRecordDao().getByHabitAndDateSync(h.getId(), today);
                if (r == null || !r.isCompleted()) habitPending++;
            }
            if (habitPending > 0) sb.append("习惯").append(habitPending).append("项待完成");
        }

        String result = sb.toString().trim();
        if (result.endsWith("·")) result = result.substring(0, result.length() - 1).trim();
        return result.isEmpty() ? "还有几项任务等待完成" : result;
    }

    public static boolean shouldSendEveningReminder(Context context) {
        return countPendingItems(context) > 0;
    }

    // ── 不活跃挽留 ──

    public static String getInactivityTitle() {
        return "好久不见！你的健康数据在等你";
    }

    public static String getInactivityContent() {
        return "过去3天没有运动记录了，今天花10分钟恢复一下吧";
    }

    public static boolean shouldSendInactivityNudge(Context context) {
        long today = DateUtils.getTodayStartTimestamp();
        AppDatabase db = AppDatabase.getInstance(context);

        // Check last 3 days for any completed training log
        for (int i = 1; i <= 3; i++) {
            long dayStart = today - i * 86400000L;
            int count = db.dailyLogDao().getTodayCompletedCountSync(dayStart);
            if (count > 0) return false; // User was active
        }
        return true; // No activity for 3 days
    }

    // ── Helpers ──

    private static int countPendingItems(Context context) {
        int pending = 0;
        long today = DateUtils.getTodayStartTimestamp();
        AppDatabase db = AppDatabase.getInstance(context);

        int completed = db.dailyLogDao().getTodayCompletedCountSync(today);
        int total = db.dailyLogDao().getTodayPlanCountSync(today);
        if (completed < total) pending++;

        int waterMl = db.waterRecordDao().getTodayTotalSync(today, today + 86400000L);
        if (waterMl < 500) pending++;

        List<HabitItem> habits = db.habitItemDao().getEnabledSync();
        if (habits != null) {
            for (HabitItem h : habits) {
                HabitRecord r = db.habitRecordDao().getByHabitAndDateSync(h.getId(), today);
                if (r == null || !r.isCompleted()) { pending++; break; }
            }
        }

        return pending;
    }

    private static User getUser(Context context) {
        return AppDatabase.getInstance(context).userDao().getUserSync();
    }
}
