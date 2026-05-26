package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.util.List;

public class HealthScoreCalculator {

    private static final String PREF_NAME = "health_score_prefs";
    private static final String KEY_WEEKLY_SCORES = "weekly_scores";
    private static final String KEY_MONTHLY_SCORES = "monthly_scores";

    public static int calculateToday(Context context) {
        long today = DateUtils.getTodayStartTimestamp();
        long dayEnd = today + 86400000L;
        AppDatabase db = AppDatabase.getInstance(context);

        int sportScore = calcSport(db, today);
        int dietScore = calcDiet(db, context, today, dayEnd);
        int sleepScore = calcSleep(db, today, dayEnd);
        int waterScore = calcWater(db, today, dayEnd);
        int habitScore = calcHabit(db, today);
        int weightScore = calcWeight(db, context);

        int total = sportScore + dietScore + sleepScore + waterScore + habitScore + weightScore;
        return Math.min(total, 100);
    }

    private static int calcSport(AppDatabase db, long today) {
        int total = db.dailyLogDao().getTodayPlanCountSync(today);
        int completed = db.dailyLogDao().getTodayCompletedCountSync(today);
        if (total == 0) return 25; // no plans = perfect score
        return Math.round(25f * completed / total);
    }

    private static int calcDiet(AppDatabase db, Context context, long today, long dayEnd) {
        int targetCal = 2000;
        User user = db.userDao().getUserSync();
        if (user != null && user.getDailyCalorieTarget() > 0) targetCal = user.getDailyCalorieTarget();

        int consumed = 0;
        List<com.cz.fitnessdiary.database.entity.FoodRecord> foods =
                db.foodRecordDao().getByDateRangeSync(today, dayEnd);
        if (foods != null) {
            for (com.cz.fitnessdiary.database.entity.FoodRecord f : foods) consumed += f.getCalories();
        }
        if (consumed == 0) return 0;
        float ratio = (float) consumed / targetCal;
        if (ratio >= 0.9f && ratio <= 1.1f) return 25;
        if (ratio >= 0.8f && ratio <= 1.2f) return 15;
        return 5;
    }

    private static int calcSleep(AppDatabase db, long today, long dayEnd) {
        List<SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(today, dayEnd);
        if (sleeps == null || sleeps.isEmpty()) {
            sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(today - 86400000L, today);
        }
        if (sleeps == null || sleeps.isEmpty()) return 0;
        float totalH = 0;
        for (SleepRecord s : sleeps) totalH += s.getDuration() / 3600f;
        if (totalH >= 7 && totalH <= 9) return 20;
        if (totalH >= 6 && totalH < 7) return 15;
        return 5;
    }

    private static int calcWater(AppDatabase db, long today, long dayEnd) {
        int ml = db.waterRecordDao().getTodayTotalSync(today, dayEnd);
        if (ml >= 2000) return 15;
        if (ml >= 1000) return 10;
        if (ml > 0) return 5;
        return 0;
    }

    private static int calcHabit(AppDatabase db, long today) {
        List<HabitItem> habits = db.habitItemDao().getEnabledSync();
        if (habits == null || habits.isEmpty()) return 0;
        int done = 0;
        for (HabitItem h : habits) {
            HabitRecord r = db.habitRecordDao().getByHabitAndDateSync(h.getId(), today);
            if (r != null && r.isCompleted()) done++;
        }
        return Math.round(10f * done / habits.size());
    }

    private static int calcWeight(AppDatabase db, Context context) {
        User user = db.userDao().getUserSync();
        if (user == null || user.getWeight() <= 0) return 0;
        List<WeightRecord> records = db.weightRecordDao().getRecentRecordsSync(7);
        if (records == null || records.size() < 2) return 3;
        float recent = records.get(0).getWeight();
        float prev = records.get(records.size() - 1).getWeight();
        float diff = recent - prev;
        int goalType = user.getGoalType();
        if (goalType == 0) { // loss
            return diff <= 0 ? 5 : 0;
        } else if (goalType == 1) { // muscle
            return diff >= 0 ? 5 : 0;
        } else { // maintain
            return Math.abs(diff) < 1.0f ? 5 : 3;
        }
    }

    public static String getWeeklyAverage(Context context) {
        return getStoredScoreInfo(context, KEY_WEEKLY_SCORES, "本周均分");
    }

    public static String getMonthlyAverage(Context context) {
        return getStoredScoreInfo(context, KEY_MONTHLY_SCORES, "本月均分");
    }

    public static void saveTodayScore(Context context, int score) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long today = DateUtils.getTodayStartTimestamp();
        String key = "score_" + today;
        sp.edit().putInt(key, score).apply();

        // Update weekly/monthly averages
        updateAverage(sp, KEY_WEEKLY_SCORES, today, 7);
        updateAverage(sp, KEY_MONTHLY_SCORES, today, 30);
    }

    private static void updateAverage(SharedPreferences sp, String avgKey, long today, int days) {
        float sum = 0;
        int count = 0;
        for (int i = 0; i < days; i++) {
            int s = sp.getInt("score_" + (today - i * 86400000L), -1);
            if (s >= 0) { sum += s; count++; }
        }
        if (count > 0) {
            sp.edit().putString(avgKey, String.valueOf(Math.round(sum / count))).apply();
        }
    }

    private static String getStoredScoreInfo(Context context, String key, String label) {
        String val = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(key, null);
        return val != null ? label + ": " + val : "";
    }
}
