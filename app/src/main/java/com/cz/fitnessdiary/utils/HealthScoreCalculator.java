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
import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.model.HealthScoreBreakdown;

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

    // ================================================================
    // v3.0 新增：UserProfile 内部类与基于 DailyHealthSnapshot 的评分方法
    // ================================================================

    /**
     * 用户健康档案配置 - v3.0
     * 用于健康评分计算的个性化参数
     */
    public static class UserProfile {
        public int dailyCalorieTarget = 2000;
        public int waterTargetMl = 2000;
        public String goalType = "maintain"; // "maintain", "lose", "gain"
        public float weightKg;
        public float heightCm;
        public int age;
        public String gender = "male";

        // 运动目标
        public int targetExerciseMinutes; // 0 = auto (使用默认卡路里阈值 300 kcal)

        // 营养素目标
        public int targetProteinGrams;  // 0 = auto (1.2g/kg)
        public int targetCarbsGrams;    // 0 = auto (55% of dietCalories)
        public int targetFatGrams;      // 0 = auto (25% of dietCalories)
    }

    /**
     * 基于 DailyHealthSnapshot 计算五维度健康评分明细
     *
     * @param data    每日健康数据快照
     * @param profile 用户健康档案
     * @return 健康评分明细（含总分）
     */
    public static HealthScoreBreakdown calculateBreakdown(DailyHealthSnapshot data, UserProfile profile) {
        HealthScoreBreakdown b = new HealthScoreBreakdown();
        b.exerciseScore = calcExerciseScore(data, profile);
        b.dietScore = calcDietScore(data, profile);
        b.habitsScore = calcHabitsScore(data, profile);
        b.bodyMetricsScore = calcBodyMetricsScore(data, profile);
        b.consistencyScore = calcConsistencyScore(data);
        b.computeTotal();
        return b;
    }

    /**
     * 便捷方法：基于 DailyHealthSnapshot 计算健康总分
     *
     * @param data    每日健康数据快照
     * @param profile 用户健康档案
     * @return 健康总分 (0-100)
     */
    public static int calculateFromSnapshot(DailyHealthSnapshot data, UserProfile profile) {
        return calculateBreakdown(data, profile).totalScore;
    }

    /**
     * 计算运动评分 (0-25)
     * 逻辑：有完成计划得15分，全部完成再加5分，热量消耗超过阈值再加5分
     * 阈值优先使用用户设定的目标运动时长（换算为卡路里），否则使用默认 300 kcal
     */
    private static int calcExerciseScore(DailyHealthSnapshot data, UserProfile profile) {
        int score = 0;
        if (data.completedPlans > 0) {
            score += 15;
        }
        if (data.totalPlans > 0 && data.completedPlans >= data.totalPlans) {
            score += 5;
        }
        int totalCalories = data.exerciseCalories + data.stepCalories;

        int threshold = 300; // 默认自动估算阈值
        if (profile.targetExerciseMinutes > 0 && profile.weightKg > 0) {
            // 根据目标时长和体重计算卡路里阈值：MET 5 * 3.5 * weight * targetMin * 60 / 200 / 60
            threshold = (int) (5 * 3.5 * profile.weightKg * profile.targetExerciseMinutes / 200.0);
        }

        if (totalCalories > threshold) {
            score += 5;
        }
        return Math.min(score, 25);
    }

    /**
     * 计算饮食评分 (0-25)
     * 多因子公式：
     * - 热量在目标 ±10% 范围：+18分
     * - 蛋白质达标（>1.2g/每kg体重）：+4分
     * - 碳水不超标（<总热量的60%）：+3分
     * 若蛋白质或碳水未追踪（值为0），不扣分（直接加满该项分值）
     */
    private static int calcDietScore(DailyHealthSnapshot data, UserProfile profile) {
        int target = profile.dailyCalorieTarget > 0 ? profile.dailyCalorieTarget : 2000;
        if (data.dietCalories <= 0) {
            return 0;
        }

        int score = 0;
        float ratio = (float) data.dietCalories / target;

        // Calories component (0-18)
        if (ratio >= 0.9f && ratio <= 1.1f) {
            score += 18;
        } else if (ratio >= 0.8f && ratio <= 1.2f) {
            score += 10;
        } else if (ratio < 0.5f || ratio > 1.5f) {
            score += 3;
        } else {
            score += 6;
        }

        // Protein component (0-4): 目标蛋白质 ±20% 内得满分
        if (data.todayProtein > 0) {
            float proteinTarget;
            if (profile.targetProteinGrams > 0) {
                proteinTarget = profile.targetProteinGrams;
            } else if (profile.weightKg > 0) {
                proteinTarget = profile.weightKg * 1.2f; // 自动估算：1.2g/kg
            } else {
                proteinTarget = 60f; // 兜底默认值
            }
            float proteinRatio = (float) data.todayProtein / proteinTarget;
            if (proteinRatio >= 0.8f && proteinRatio <= 1.2f) {
                score += 4;
            }
        } else {
            // No penalty for not tracking protein
            score += 4;
        }

        // Carbs component (0-3): 目标碳水 ±20% 内得满分
        if (data.todayCarbs > 0) {
            int carbsTarget;
            if (profile.targetCarbsGrams > 0) {
                carbsTarget = profile.targetCarbsGrams;
            } else {
                // 自动估算：55% of diet calories / 4 kcal per gram
                carbsTarget = (int) (data.dietCalories * 0.55f / 4f);
            }
            float carbsRatio = (float) data.todayCarbs / carbsTarget;
            if (carbsRatio >= 0.8f && carbsRatio <= 1.2f) {
                score += 3;
            }
        } else {
            // No penalty for not tracking carbs
            score += 3;
        }

        return Math.min(score, 25);
    }

    /**
     * 计算生活习惯评分 (0-20)
     * 分项：睡眠(4) + 饮水(4) + 步数(3) + 用药(3) + 排便(3) + 心情(3)
     */
    private static int calcHabitsScore(DailyHealthSnapshot data, UserProfile profile) {
        int score = 0;

        // Sleep (0-4)
        if (data.sleepHours >= 7 && data.sleepHours <= 9) {
            score += 4;
        } else if (data.sleepHours >= 6) {
            score += 2;
        }

        // Water (0-4)
        int waterTarget = profile.waterTargetMl > 0 ? profile.waterTargetMl : 2000;
        if (data.waterMl >= waterTarget) {
            score += 4;
        } else if (data.waterMl >= waterTarget / 2) {
            score += 2;
        } else if (data.waterMl > 0) {
            score += 1;
        }

        // Steps (0-3)
        if (data.steps >= 8000) {
            score += 3;
        } else if (data.steps >= 5000) {
            score += 2;
        } else if (data.steps >= 3000) {
            score += 1;
        }

        // Medication (0-3)
        if (data.medicationTotal > 0) {
            if (data.medicationTaken >= data.medicationTotal) {
                score += 3;
            } else if (data.medicationTaken > 0) {
                score += 1;
            }
        }

        // Bowel (0-3)
        if (data.bowelCount >= 1) {
            score += 3;
        }

        // Mood (0-3)
        if (data.moodLevel >= 4) {
            score += 3;
        } else if (data.moodLevel >= 3) {
            score += 2;
        } else if (data.moodLevel >= 2) {
            score += 1;
        }

        return Math.min(score, 20);
    }

    /**
     * 计算身体指标评分 (0-15)
     * 基于目标体重与当前体重的距离，以及体重趋势是否朝向目标方向
     */
    private static int calcBodyMetricsScore(DailyHealthSnapshot data, UserProfile profile) {
        if (data.weightKg <= 0) return 5;
        int score = 10;
        float targetWeight = computeTargetWeight(profile);
        float currentWeight = data.weightKg;
        float distanceFromTarget = Math.abs(currentWeight - targetWeight);

        // Weight close to target: high score
        if (distanceFromTarget < 1.0f) {
            score += 5;
        } else if (distanceFromTarget < 3.0f) {
            score += 3;
        }

        // Trend toward target: high score
        // weightTrend > 0 means weight lost, < 0 means weight gained
        boolean movingTowardTarget = ("lose".equals(profile.goalType) && data.weightTrend > 0) ||
            ("gain".equals(profile.goalType) && data.weightTrend < 0) ||
            ("maintain".equals(profile.goalType) && Math.abs(data.weightTrend) < 0.5f);

        if (movingTowardTarget) {
            score += 5;
        } else {
            score += 1;
        }

        return Math.min(score, 15);
    }

    /**
     * 根据用户目标计算科学目标体重
     * 使用 BMI 健康范围 (18.5-24.9) 作为上下限
     * - 减脂：当前体重 × 0.90（减去10%），但不低于健康最低体重
     * - 增肌：当前体重 × 1.06（增加6%），但不高于健康最高体重
     * - 保持：当前体重
     */
    public static float computeTargetWeight(UserProfile profile) {
        if (profile.weightKg <= 0 || profile.heightCm <= 0) return profile.weightKg;

        // Calculate healthy weight range using BMI 18.5-24.9
        float heightM = profile.heightCm / 100f;
        float minHealthyWeight = 18.5f * heightM * heightM;
        float maxHealthyWeight = 24.9f * heightM * heightM;

        if ("lose".equals(profile.goalType)) {
            // Target: lose 5-10% of current weight, but not below min healthy
            float target = profile.weightKg * 0.90f; // 10% loss
            target = Math.max(target, minHealthyWeight);
            return Math.round(target * 10f) / 10f;
        } else if ("gain".equals(profile.goalType)) {
            // Target: gain 5-8% of current weight, but not above max healthy
            float target = profile.weightKg * 1.06f; // 6% gain
            target = Math.min(target, maxHealthyWeight);
            return Math.round(target * 10f) / 10f;
        } else {
            // Maintain: stay at current weight
            return profile.weightKg;
        }
    }

    /**
     * 计算坚持度评分 (0-15)
     * 根据连续打卡天数映射得分
     */
    private static int calcConsistencyScore(DailyHealthSnapshot data) {
        if (data.consecutiveDays >= 30) {
            return 15;
        } else if (data.consecutiveDays >= 21) {
            return 13;
        } else if (data.consecutiveDays >= 14) {
            return 11;
        } else if (data.consecutiveDays >= 7) {
            return 9;
        } else if (data.consecutiveDays >= 3) {
            return 6;
        } else if (data.consecutiveDays >= 1) {
            return 3;
        }
        return 0;
    }
}
