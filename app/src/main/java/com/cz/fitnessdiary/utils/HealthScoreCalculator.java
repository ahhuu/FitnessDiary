package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.util.List;

public class HealthScoreCalculator {

    private static final String PREF_NAME = "health_score_prefs";
    private static final String KEY_WEEKLY_SCORES = "weekly_scores";
    private static final String KEY_MONTHLY_SCORES = "monthly_scores";

    public static int calculateToday(Context context) {
        return calculateForDate(context, DateUtils.getTodayStartTimestamp());
    }

    public static int calculateForDate(Context context, long date) {
        long dayStart = DateUtils.getDayStartTimestamp(date);
        long dayEnd = dayStart + 86400000L;
        AppDatabase db = AppDatabase.getInstance(context);

        int sportScore = calcSport(db, context, dayStart);
        int dietScore = calcDiet(db, context, dayStart, dayEnd);
        int sleepScore = calcSleep(db, dayStart, dayEnd);
        int waterScore = calcWater(db, dayStart, dayEnd);
        int habitScore = calcHabit(db, dayStart);
        int weightScore = calcWeight(db, context, dayStart);

        int total = sportScore + dietScore + sleepScore + waterScore + habitScore + weightScore;
        return Math.min(total, 100);
    }

    private static int calcSport(AppDatabase db, Context context, long date) {
        // Count TrainingPlan definitions scheduled for this date (matching UI logic)
        List<TrainingPlan> allPlans = db.trainingPlanDao().getAllPlansList();
        SharedPreferences sp = context.getSharedPreferences("fitness_diary_prefs", Context.MODE_PRIVATE);
        String mode = sp.getString("current_plan_mode", "基础");

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(date);
        int androidDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        int dayIndex = (androidDayOfWeek == java.util.Calendar.SUNDAY) ? 7 : (androidDayOfWeek - 1);

        int total = 0;
        if (allPlans != null) {
            for (TrainingPlan plan : allPlans) {
                String cat = plan.getCategory();
                if (cat == null || !cat.startsWith(mode + "-")) continue;

                String scheduledDays = plan.getScheduledDays();
                if (scheduledDays == null || scheduledDays.isEmpty() || scheduledDays.contains("0")) {
                    total++;
                } else {
                    for (String day : scheduledDays.split(",")) {
                        if (day.trim().equals(String.valueOf(dayIndex))) {
                            total++;
                            break;
                        }
                    }
                }
            }
        }

        if (total == 0) return 25;
        int completed = db.dailyLogDao().getTodayCompletedCountSync(date);
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
        // 修复 bug：去除回退查询昨天睡眠记录的逻辑。当天如无记录，睡眠得分为 0，防止分数虚高
        List<SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(today, dayEnd);
        if (sleeps == null || sleeps.isEmpty()) return 0;
        float totalH = 0;
        for (SleepRecord s : sleeps) totalH += s.getDuration() / 3600f;
        if (totalH >= 7 && totalH <= 9) return 20;
        if (totalH >= 6 && totalH < 7) return 15;
        return 5;
    }

    private static int calcWater(AppDatabase db, long today, long dayEnd) {
        int ml = db.waterRecordDao().getTodayTotalSync(today, dayEnd);
        com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
        int waterTarget = (user != null && user.getDailyWaterTarget() > 0) ? user.getDailyWaterTarget() : 2000;
        if (ml >= waterTarget) return 15;
        if (ml >= waterTarget / 2) return 10;
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

    private static int calcWeight(AppDatabase db, Context context, long date) {
        User user = db.userDao().getUserSync();
        if (user == null || user.getWeight() <= 0) return 0;
        // Use records within 7 days before the target date for historical accuracy
        long weekAgo = date - 7 * 86400000L;
        long dayEnd = date + 86400000L;
        List<WeightRecord> records = db.weightRecordDao().getRecordsByDateRangeSync(weekAgo, dayEnd);
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
