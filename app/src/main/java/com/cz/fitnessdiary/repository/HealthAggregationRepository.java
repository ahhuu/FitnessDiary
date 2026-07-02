package com.cz.fitnessdiary.repository;

import android.app.Application;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.model.HealthScoreBreakdown;
import com.cz.fitnessdiary.model.WeeklyTrend;
import com.cz.fitnessdiary.utils.CalorieCalculatorUtils;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.HealthScoreCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 健康数据聚合仓库 - v3.0
 * 聚合每日运动、饮食、身体指标等核心健康数据，并提供健康评分与周度趋势
 */
public class HealthAggregationRepository {

    private final AppDatabase db;
    private final ExecutorService executor;

    public HealthAggregationRepository(Application application) {
        this.db = AppDatabase.getInstance(application);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 获取今天的健康数据快照
     */
    public DailyHealthSnapshot getTodaySnapshot() {
        return getDateSnapshot(DateUtils.getTodayStartTimestamp());
    }

    /**
     * 获取指定日期的健康数据快照
     *
     * @param dateTs 指定日期的 0 点时间戳
     * @return 填充完整的 DailyHealthSnapshot
     */
    public DailyHealthSnapshot getDateSnapshot(long dateTs) {
        DailyHealthSnapshot s = new DailyHealthSnapshot();
        long dayEnd = dateTs + 86400000L;

        // 1. Diet calories (饮食摄入)
        Integer dietCalories = db.foodRecordDao().getTotalCaloriesByDateRangeSync(dateTs, dayEnd);
        s.dietCalories = dietCalories != null ? dietCalories : 0;

        // 1b. Protein and Carbs from food records (营养素)
        List<com.cz.fitnessdiary.database.entity.FoodRecord> foodRecords = db.foodRecordDao().getByDateRangeSync(dateTs, dayEnd);
        if (foodRecords != null) {
            int totalProtein = 0;
            int totalCarbs = 0;
            for (com.cz.fitnessdiary.database.entity.FoodRecord f : foodRecords) {
                totalProtein += (int) f.getProtein();
                totalCarbs += (int) f.getCarbs();
            }
            s.todayProtein = totalProtein;
            s.todayCarbs = totalCarbs;
        }

        // 2. Training data (训练计划完成情况)
        s.completedPlans = db.dailyLogDao().getTodayCompletedCountSync(dateTs);
        s.totalPlans = db.dailyLogDao().getTodayPlanCountSync(dateTs);

        List<TrainingPlan> allPlans = db.trainingPlanDao().getAllPlansList();
        if (allPlans != null && !allPlans.isEmpty()) {
            s.currentPlanName = allPlans.get(0).getName();
        }

        // 3. Steps and step calories (步数与步数消耗)
        StepRecord stepRecord = db.stepRecordDao().getByDateSync(dateTs);
        s.steps = stepRecord != null ? stepRecord.getSteps() : 0;
        s.stepCalories = (int) (s.steps * 0.04f);

        // 4. Sleep (睡眠)
        List<SleepRecord> sleepRecords = db.sleepRecordDao().getSleepRecordsByDateRangeSync(dateTs, dayEnd);
        if (sleepRecords != null && !sleepRecords.isEmpty()) {
            long totalSeconds = 0;
            int qualitySum = 0;
            for (SleepRecord r : sleepRecords) {
                totalSeconds += r.getDuration();
                qualitySum += r.getQuality();
            }
            s.sleepHours = totalSeconds / 3600.0;
            s.sleepQuality = qualitySum / sleepRecords.size();
        }

        // 5. Water (饮水)
        s.waterMl = db.waterRecordDao().getTodayTotalSync(dateTs, dayEnd);

        // 6. Medication (用药)
        List<MedicationRecord> medRecords = db.medicationRecordDao().getRecordsByDateRangeSync(dateTs, dayEnd);
        if (medRecords != null) {
            int taken = 0;
            int total = 0;
            for (MedicationRecord m : medRecords) {
                if (m.isTaken()) {
                    taken++;
                }
                total += m.getDailyTotal();
            }
            s.medicationTaken = taken;
            s.medicationTotal = total;
        }

        // 7. Bowel movement (排便)
        List<BowelMovement> bowelRecords = db.bowelMovementDao().getByDateRangeSync(dateTs, dayEnd);
        s.bowelCount = bowelRecords != null ? bowelRecords.size() : 0;

        // 8. Mood (心情)
        MoodRecord moodRecord = db.moodRecordDao().getByDateSync(dateTs);
        if (moodRecord != null) {
            s.moodLevel = moodCodeToLevel(moodRecord.getMoodCode());
        }

        // 9. Weight and trend (体重与趋势)
        WeightRecord latestWeight = db.weightRecordDao().getLatestRecordSync();
        if (latestWeight != null) {
            s.weightKg = latestWeight.getWeight();
            long weekAgo = dateTs - 7 * 86400000L;
            List<WeightRecord> weightRecords = db.weightRecordDao().getRecordsByDateRangeSync(weekAgo, dayEnd);
            if (weightRecords != null && weightRecords.size() >= 2) {
                float first = weightRecords.get(0).getWeight();
                float last = weightRecords.get(weightRecords.size() - 1).getWeight();
                // Positive = weight lost, negative = weight gained
                s.weightTrend = first - last;
            }
        }

        // 10. User profile, BMR, consecutive days and exercise calories
        User user = db.userDao().getUserSync();
        if (user != null) {
            s.bmr = CalorieCalculatorUtils.calculateBMR(
                    user.getGender(), user.getWeight(), user.getHeight(), user.getAge());

            // Exercise calories from completed training plan durations
            if (user.getWeight() > 0 && s.completedPlans > 0) {
                List<DailyLog> dailyLogs = db.dailyLogDao().getLogsByDateSync(dateTs);
                if (dailyLogs != null) {
                    int totalCompletedDuration = 0;
                    for (DailyLog log : dailyLogs) {
                        if (log.isCompleted()) {
                            totalCompletedDuration += log.getDuration();
                        }
                    }
                    if (totalCompletedDuration > 0) {
                        // Average MET of 5 for mixed exercise
                        // Formula: MET x 3.5 x weight(kg) x duration(s) / 200 / 60
                        s.exerciseCalories = (int) (5 * 3.5 * user.getWeight()
                                * totalCompletedDuration / 200.0 / 60.0);
                    }
                }
            }
        }

        // Consecutive days from all daily log records
        List<Long> checkedDates = getCheckedDateTimestamps();
        s.consecutiveDays = DateUtils.calculateConsecutiveDays(checkedDates);

        // 11. Compute energy balance
        s.computeEnergyBalance();

        return s;
    }

    /**
     * 获取本周趋势数据（训练消耗、饮食摄入、睡眠时长、体重）
     *
     * @return 四项趋势指标列表
     */
    public List<WeeklyTrend> getWeeklyTrends() {
        long today = DateUtils.getTodayStartTimestamp();
        List<Float> exerciseValues = new ArrayList<>();
        List<Float> dietValues = new ArrayList<>();
        List<Float> sleepValues = new ArrayList<>();
        List<Float> weightValues = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            long dayTs = today - i * 86400000L;
            DailyHealthSnapshot snapshot = getDateSnapshot(dayTs);

            // 训练消耗（运动消耗 + 步数消耗）
            float exerciseCal = (float) (snapshot.exerciseCalories + snapshot.stepCalories);
            exerciseValues.add(exerciseCal);

            // 饮食摄入
            dietValues.add((float) snapshot.dietCalories);

            // 睡眠时长
            sleepValues.add((float) snapshot.sleepHours);

            // 体重
            weightValues.add(snapshot.weightKg > 0 ? snapshot.weightKg : 0f);
        }

        List<WeeklyTrend> trends = new ArrayList<>();
        trends.add(new WeeklyTrend("训练消耗", "kcal", exerciseValues));
        trends.add(new WeeklyTrend("饮食摄入", "kcal", dietValues));
        trends.add(new WeeklyTrend("睡眠时长", "小时", sleepValues));
        trends.add(new WeeklyTrend("体重", "kg", weightValues));

        // Set direction hints: 1 = increase is good, -1 = decrease is good, 0 = neutral
        trends.get(0).direction = 1;  // More exercise is better
        trends.get(1).direction = 0;  // Diet depends on goal
        trends.get(2).direction = 0;  // Sleep near optimal range is best
        trends.get(3).direction = -1; // Weight loss is typically positive

        return trends;
    }

    /**
     * 获取今日健康评分明细
     *
     * @param profile 用户健康档案配置
     * @return 五维度健康评分明细
     */
    public HealthScoreBreakdown getTodayScoreBreakdown(HealthScoreCalculator.UserProfile profile) {
        DailyHealthSnapshot todaySnapshot = getTodaySnapshot();
        return HealthScoreCalculator.calculateBreakdown(todaySnapshot, profile);
    }

    /**
     * 将心情代码映射为等级 (1-5)
     */
    private static int moodCodeToLevel(String moodCode) {
        if (moodCode == null) {
            return 0;
        }
        switch (moodCode) {
            case "HAPPY":
                return 5;
            case "NEUTRAL":
                return 3;
            case "SAD":
                return 1;
            case "IRRITABLE":
                return 2;
            case "ANXIOUS":
                return 2;
            default:
                return 0;
        }
    }

    /**
     * 获取所有有打卡记录的日期时间戳，按降序排列
     */
    private List<Long> getCheckedDateTimestamps() {
        List<DailyLog> allLogs = db.dailyLogDao().getAllLogsSync();
        if (allLogs == null || allLogs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uniqueDates = new HashSet<>();
        for (DailyLog log : allLogs) {
            uniqueDates.add(DateUtils.getDayStartTimestamp(log.getDate()));
        }
        List<Long> sorted = new ArrayList<>(uniqueDates);
        Collections.sort(sorted, Collections.reverseOrder());
        return sorted;
    }
}
