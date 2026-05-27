package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.cz.fitnessdiary.database.AppDatabase;

public class ChallengeManager {

    private static final String PREF = "challenge_prefs";
    private static final String KEY_TYPE = "type";
    private static final String KEY_START = "start";
    private static final String KEY_STATUS = "status"; // ACTIVE, COMPLETED, FAILED
    private static final String KEY_FAILS = "fails";
    private static final String KEY_LAST_CHECK = "last_check";

    public static final String TYPE_FAT_LOSS = "FAT_LOSS";
    public static final String TYPE_MUSCLE_GAIN = "MUSCLE_GAIN";
    public static final String TYPE_EARLY_SLEEP = "EARLY_SLEEP";
    public static final String TYPE_WATER_MASTER = "WATER_MASTER";

    public static String getTypeName(String type) {
        switch (type) {
            case TYPE_FAT_LOSS: return "减脂冲刺";
            case TYPE_MUSCLE_GAIN: return "健身达人";
            case TYPE_EARLY_SLEEP: return "早睡挑战";
            case TYPE_WATER_MASTER: return "饮水达人";
            default: return "";
        }
    }

    public static String getTypeDesc(String type) {
        switch (type) {
            case TYPE_FAT_LOSS: return "21天每日热量不超标 · 累计3天超标则失败";
            case TYPE_MUSCLE_GAIN: return "21天运动不间断 · 连续2天中断则失败";
            case TYPE_EARLY_SLEEP: return "21天23:00前入睡 · 累计3天晚睡则失败";
            case TYPE_WATER_MASTER: return "21天饮水≥2000ml · 累计3天不达标则失败";
            default: return "";
        }
    }

    public static String getTypeEmoji(String type) {
        switch (type) {
            case TYPE_FAT_LOSS: return "🔥";
            case TYPE_MUSCLE_GAIN: return "💪";
            case TYPE_EARLY_SLEEP: return "🌙";
            case TYPE_WATER_MASTER: return "💧";
            default: return "";
        }
    }

    public static String getStatus(Context context) {
        return getPref(context).getString(KEY_STATUS, null);
    }

    public static String getActiveType(Context context) {
        return getPref(context).getString(KEY_TYPE, null);
    }

    public static long getStartDate(Context context) {
        return getPref(context).getLong(KEY_START, 0);
    }

    public static int getFailDays(Context context) {
        return getPref(context).getInt(KEY_FAILS, 0);
    }

    public static int getProgressDays(Context context) {
        long start = getStartDate(context);
        if (start == 0) return 0;
        return (int) ((DateUtils.getTodayStartTimestamp() - start) / 86400000L) + 1;
    }

    public static void start(Context context, String type) {
        getPref(context).edit()
                .putString(KEY_TYPE, type)
                .putLong(KEY_START, DateUtils.getTodayStartTimestamp())
                .putString(KEY_STATUS, "ACTIVE")
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_LAST_CHECK, 0)
                .apply();
    }

    public static void reset(Context context) {
        getPref(context).edit().clear().apply();
    }

    public static void checkToday(Context context) {
        SharedPreferences sp = getPref(context);
        String status = sp.getString(KEY_STATUS, null);
        if (!"ACTIVE".equals(status)) return;

        long today = DateUtils.getTodayStartTimestamp();
        long lastCheck = sp.getLong(KEY_LAST_CHECK, 0);
        if (lastCheck == today) return;

        String type = sp.getString(KEY_TYPE, null);
        long start = sp.getLong(KEY_START, 0);
        if (type == null || start == 0) return;

        // Check 21 days completed
        int daysPassed = (int) ((today - start) / 86400000L) + 1;
        if (daysPassed > 21) {
            sp.edit().putString(KEY_STATUS, "COMPLETED").putLong(KEY_LAST_CHECK, today).apply();
            return;
        }

        // Check today failed
        long dayEnd = today + 86400000L;
        AppDatabase db = AppDatabase.getInstance(context);
        boolean failed = false;

        switch (type) {
            case TYPE_FAT_LOSS: {
                int targetCal = 2000;
                com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                if (user != null && user.getDailyCalorieTarget() > 0) targetCal = user.getDailyCalorieTarget();
                int consumed = 0;
                java.util.List<com.cz.fitnessdiary.database.entity.FoodRecord> foods = db.foodRecordDao().getByDateRangeSync(today, dayEnd);
                if (foods != null) for (com.cz.fitnessdiary.database.entity.FoodRecord f : foods) consumed += f.getCalories();
                failed = consumed > targetCal;
                break;
            }
            case TYPE_MUSCLE_GAIN: {
                int done = db.dailyLogDao().getTodayCompletedCountSync(today);
                int tot = db.dailyLogDao().getTodayPlanCountSync(today);
                failed = tot > 0 && done == 0;
                break;
            }
            case TYPE_EARLY_SLEEP: {
                java.util.List<com.cz.fitnessdiary.database.entity.SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(today, dayEnd);
                if (sleeps != null && !sleeps.isEmpty()) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(sleeps.get(0).getStartTime());
                    failed = cal.get(java.util.Calendar.HOUR_OF_DAY) >= 23;
                }
                break;
            }
            case TYPE_WATER_MASTER: {
                int ml = db.waterRecordDao().getTodayTotalSync(today, dayEnd);
                com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                int waterTarget = (user != null && user.getDailyWaterTarget() > 0) ? user.getDailyWaterTarget() : 2000;
                failed = ml < waterTarget;
                break;
            }
        }

        SharedPreferences.Editor e = sp.edit().putLong(KEY_LAST_CHECK, today);
        if (failed) {
            int fails = sp.getInt(KEY_FAILS, 0) + 1;
            e.putInt(KEY_FAILS, fails);
            int maxFails = TYPE_MUSCLE_GAIN.equals(type) ? 2 : 3;
            if (fails >= maxFails) e.putString(KEY_STATUS, "FAILED");
        }
        e.apply();
    }

    private static SharedPreferences getPref(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
