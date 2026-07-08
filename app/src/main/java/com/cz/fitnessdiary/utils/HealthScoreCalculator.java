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
        public float customTargetWeight = -1f;

        // 运动目标
        public int targetExerciseMinutes; // 0 = auto (使用默认卡路里阈值 300 kcal)
        public int stepTarget = 8000;     // 每日步数目标，默认8000

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
     * 阶梯制：完成率≥50%→10, ≥80%→15, 100%→20, 热量阈值+5
     */
    private static int calcExerciseScore(DailyHealthSnapshot data, UserProfile profile) {
        // 当天没有安排训练计划时，自动给满分（休息日本就该休息）
        if (data.totalPlans == 0) {
            return 25;
        }

        int score = 0;
        if (data.completedPlans > 0) {
            float ratio = (float) data.completedPlans / data.totalPlans;
            if (ratio >= 1.0f) {
                score += 20;
            } else if (ratio >= 0.8f) {
                score += 15;
            } else if (ratio >= 0.5f) {
                score += 10;
            } else {
                score += 5;
            }
        } else if (data.completedPlans > 0) {
            score += 5; // totalPlans=0但有完成记录，兜底
        }

        int totalCalories = data.exerciseCalories + data.stepCalories;
        int threshold = 300;
        if (profile.targetExerciseMinutes > 0 && profile.weightKg > 0) {
            threshold = (int) (5.0 * profile.weightKg * (profile.targetExerciseMinutes / 60.0));
        }
        if (totalCalories > threshold) {
            score += 5;
        }
        return Math.min(score, 25);
    }

    /**
     * 计算饮食评分 (0-25)
     * 热量12 + 蛋白质8 + 碳水5。不跟踪=0分，鼓励记录。
     * 热量评分依据用户健身目标动态调整：
     * - 增肌：多吃少罚（盈余利于增肌），少吃严罚（亏空影响合成）
     * - 减脂：多吃严罚（超标阻碍减脂），少吃轻罚（赤字是目标）
     * - 保持：对称评分，偏离目标均扣分
     */
    private static int calcDietScore(DailyHealthSnapshot data, UserProfile profile) {
        int target = profile.dailyCalorieTarget > 0 ? profile.dailyCalorieTarget : 2000;
        if (data.dietCalories <= 0) {
            return 0;
        }

        int score = 0;
        float ratio = (float) data.dietCalories / target;

        // Calories component (0-12) — 目标感知动态评分
        String goal = profile.goalType != null ? profile.goalType : "maintain";
        score += calcCalorieScoreByGoal(ratio, goal);

        // Protein component (0-8): 不跟踪=0，鼓励记录
        if (data.todayProtein > 0) {
            float proteinTarget;
            if (profile.targetProteinGrams > 0) {
                proteinTarget = profile.targetProteinGrams;
            } else if (profile.weightKg > 0) {
                proteinTarget = profile.weightKg * 1.2f;
            } else {
                proteinTarget = 60f;
            }
            float proteinRatio = (float) data.todayProtein / proteinTarget;
            if (proteinRatio >= 0.9f && proteinRatio <= 1.1f) {
                score += 8;
            } else if (proteinRatio >= 0.7f && proteinRatio <= 1.3f) {
                score += 5;
            } else if (proteinRatio >= 0.5f) {
                score += 2;
            }
        }

        // Carbs component (0-5): 不跟踪=0，鼓励记录
        if (data.todayCarbs > 0) {
            int carbsTarget;
            if (profile.targetCarbsGrams > 0) {
                carbsTarget = profile.targetCarbsGrams;
            } else {
                carbsTarget = (int) (data.dietCalories * 0.55f / 4f);
            }
            float carbsRatio = (float) data.todayCarbs / carbsTarget;
            if (carbsRatio >= 0.9f && carbsRatio <= 1.1f) {
                score += 5;
            } else if (carbsRatio >= 0.7f && carbsRatio <= 1.3f) {
                score += 3;
            } else if (carbsRatio >= 0.5f) {
                score += 1;
            }
        }

        return Math.min(score, 25);
    }

    /**
     * 根据健身目标计算热量子评分 (0-12)
     *
     * 增肌 (gain)：盈余 OK，亏空扣分重
     *   完美 12: 0.9~1.3 (吃到目标或适度盈余)
     *   良好 8:  0.8~0.9 或 1.3~1.5
     *   一般 4:  0.6~0.8
     *   差 2:    <0.6 或 >1.5
     *
     * 减脂 (lose)：赤字 OK，超标扣分重
     *   完美 12: 0.7~1.0 (适度赤字到刚好达标)
     *   良好 8:  0.5~0.7 或 1.0~1.15
     *   一般 4:  1.15~1.3
     *   差 2:    <0.5 或 >1.3
     *
     * 保持 (maintain)：对称评分
     *   完美 12: 0.9~1.1
     *   良好 8:  0.8~1.2
     *   一般 4:  0.5~1.5 (不含两端)
     *   差 2:    <0.5 或 >1.5
     */
    private static int calcCalorieScoreByGoal(float ratio, String goal) {
        switch (goal) {
            case "gain":
                if (ratio >= 0.9f && ratio <= 1.3f) {
                    return 12;  // 吃到目标或适度盈余，增肌最佳区间
                } else if ((ratio >= 0.8f && ratio < 0.9f) || (ratio > 1.3f && ratio <= 1.5f)) {
                    return 8;   // 轻微亏空或盈余偏多
                } else if (ratio >= 0.6f && ratio < 0.8f) {
                    return 4;   // 明显吃不够，影响合成
                } else {
                    return 2;   // 严重偏离：极度节食或暴食
                }

            case "lose":
                if (ratio >= 0.7f && ratio <= 1.0f) {
                    return 12;  // 适度赤字到刚好达标，减脂最佳区间
                } else if ((ratio >= 0.5f && ratio < 0.7f) || (ratio > 1.0f && ratio <= 1.15f)) {
                    return 8;   // 激进赤字或轻微超标
                } else if (ratio > 1.15f && ratio <= 1.3f) {
                    return 4;   // 超标明显，影响减脂
                } else {
                    return 2;   // 严重偏离：极度节食或暴食
                }

            default: // "maintain"
                if (ratio >= 0.9f && ratio <= 1.1f) {
                    return 12;
                } else if (ratio >= 0.8f && ratio <= 1.2f) {
                    return 8;
                } else if (ratio >= 0.5f && ratio <= 1.5f) {
                    return 4;
                } else {
                    return 2;
                }
        }
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

        // Steps (0-3) — 使用用户设定的步数目标
        int stepTarget = profile.stepTarget > 0 ? profile.stepTarget : 8000;
        if (data.steps >= stepTarget) {
            score += 3;
        } else if (data.steps >= stepTarget * 0.625f) {  // ≥62.5% 目标
            score += 2;
        } else if (data.steps >= stepTarget * 0.375f) {  // ≥37.5% 目标
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
     * 基础分3 + 离目标近+7 + 趋势正确+5
     */
    private static int calcBodyMetricsScore(DailyHealthSnapshot data, UserProfile profile) {
        if (data.weightKg <= 0) return 0;
        int score = 3; // 有体重记录得3分基础分
        float targetWeight = computeTargetWeight(profile);
        float currentWeight = data.weightKg;
        float distanceFromTarget = Math.abs(currentWeight - targetWeight);

        // Weight close to target: +7/+4
        if (distanceFromTarget < 1.0f) {
            score += 7;
        } else if (distanceFromTarget < 3.0f) {
            score += 4;
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
        if (profile.customTargetWeight > 0) {
            return profile.customTargetWeight;
        }
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
     * 过去7天活跃天数：任一健康记录（运动/饮食/睡眠/饮水/步数）即算活跃
     */
    private static int calcConsistencyScore(DailyHealthSnapshot data) {
        if (data.activeDays7 >= 7) {
            return 15;
        } else if (data.activeDays7 >= 6) {
            return 13;
        } else if (data.activeDays7 >= 5) {
            return 10;
        } else if (data.activeDays7 >= 4) {
            return 7;
        } else if (data.activeDays7 >= 2) {
            return 4;
        } else if (data.activeDays7 >= 1) {
            return 2;
        }
        return 0;
    }
}
