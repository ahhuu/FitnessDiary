package com.cz.fitnessdiary.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.HomeDashboardRepository;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 快速录入 ViewModel - v3.0
 * 聚合饮食、训练、习惯三种快速录入逻辑
 */
public class QuickEntryViewModel extends AndroidViewModel {

    private final FoodRecordRepository foodRecordRepository;
    private final FoodLibraryRepository foodLibraryRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final HomeDashboardRepository homeDashboardRepository;
    private final ExecutorService executor;

    public QuickEntryViewModel(@NonNull Application application) {
        super(application);
        this.foodRecordRepository = new FoodRecordRepository(application);
        this.foodLibraryRepository = new FoodLibraryRepository(application);
        this.trainingPlanRepository = new TrainingPlanRepository(application);
        this.homeDashboardRepository = new HomeDashboardRepository(application);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 快速添加饮食记录
     *
     * @param foodId   食物库 ID
     * @param servings 份数
     * @param mealType 餐点类型 (0=早餐, 1=午餐, 2=晚餐, 3=加餐)
     * @param date     记录日期 (0 点时间戳)
     */
    public void quickAddFood(long foodId, float servings, int mealType, long date) {
        executor.execute(() -> {
            FoodLibrary food = null;
            if (foodId > 0) {
                java.util.List<FoodLibrary> allFoods = foodLibraryRepository.getAllFoodsSync();
                if (allFoods != null) {
                    for (FoodLibrary f : allFoods) {
                        if (f.getId() == foodId) {
                            food = f;
                            break;
                        }
                    }
                }
            }

            int calories = 0;
            double protein = 0;
            double carbs = 0;
            double fat = 0;
            int weightPerUnit = 100;
            String servingUnit = "份";

            if (food != null) {
                weightPerUnit = food.getWeightPerUnit();
                servingUnit = food.getServingUnit() != null ? food.getServingUnit() : "份";
                double totalWeight = servings * weightPerUnit;
                double ratio = totalWeight / 100.0;
                calories = (int) (food.getCaloriesPer100g() * ratio);
                protein = food.getProteinPer100g() * ratio;
                carbs = food.getCarbsPer100g() * ratio;
                fat = food.getFatPer100g() * ratio;
            }

            long recordDate;
            if (DateUtils.isToday(date)) {
                recordDate = System.currentTimeMillis();
            } else {
                recordDate = date;
            }

            String foodName = food != null ? food.getName() : "未知食物";

            FoodRecord record = new FoodRecord(foodName, calories, recordDate);
            record.setProtein(protein);
            record.setCarbs(carbs);
            record.setFat(fat);
            record.setMealType(mealType);
            record.setServings(servings);
            record.setServingUnit(servingUnit);

            foodRecordRepository.insert(record);
        });
    }

    /**
     * 快速完成训练计划（使用计划默认值）
     *
     * @param planId 训练计划 ID
     * @param date   完成日期 (0 点时间戳)
     */
    public void quickCompletePlan(int planId, long date) {
        quickCompletePlan(planId, date, 0, 0, 0);
    }

    /**
     * 快速完成训练计划（指定实际完成值）
     *
     * @param planId     训练计划 ID
     * @param date       完成日期 (0 点时间戳)
     * @param sets       实际完成组数（0=使用计划默认值）
     * @param reps       实际完成次数（0=使用计划默认值）
     * @param durationSeconds 实际时长秒数（0=使用计划默认值）
     */
    public void quickCompletePlan(int planId, long date, int sets, int reps, int durationSeconds) {
        executor.execute(() -> {
            TrainingPlan plan = trainingPlanRepository.getPlanById(planId);
            if (plan == null) {
                return;
            }
            DailyLog log = new DailyLog(planId, date, true);
            if (durationSeconds > 0) {
                log.setDuration(durationSeconds);
            } else {
                log.setDuration(plan.getDuration() > 0 ? plan.getDuration() : 600);
            }
            AppDatabase db = AppDatabase.getInstance(getApplication());
            db.dailyLogDao().insert(log);
        });
    }

    /**
     * 快速记录习惯
     *
     * @param cardKey 卡片标识: "water", "step", "mood", "medication", "bowel", "weight"
     * @param value   对应的值 (类型因 cardKey 而异)
     * @param date    记录日期 (0 点时间戳)
     */
    public void quickRecordHabit(String cardKey, Object value, long date) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            long todayStart = DateUtils.getTodayStartTimestamp();
            boolean isToday = DateUtils.isToday(date);
            long timestamp = isToday ? now : date + 12L * 60L * 60L * 1000L;

            switch (cardKey) {
                case "water": {
                    int amountMl = value instanceof Number ? ((Number) value).intValue() : 200;
                    homeDashboardRepository.addWater(new WaterRecord(amountMl, timestamp, null));
                    break;
                }
                case "step": {
                    int steps = value instanceof Number ? ((Number) value).intValue() : 8000;
                    StepRecord existing = homeDashboardRepository.getStepByDateSync(todayStart);
                    if (existing != null) {
                        existing.setSteps(existing.getSteps() + steps);
                        existing.setSource(1);
                        existing.setCreateTime(now);
                        homeDashboardRepository.insertOrUpdateStep(existing);
                    } else {
                        homeDashboardRepository.insertOrUpdateStep(
                                new StepRecord(todayStart, steps, 1, now));
                    }
                    break;
                }
                case "mood": {
                    String moodCode = value != null ? value.toString() : "NEUTRAL";
                    homeDashboardRepository.insertOrUpdateMood(
                            new MoodRecord(todayStart, moodCode, null, now));
                    break;
                }
                case "medication": {
                    boolean taken = value instanceof Boolean && (Boolean) value;
                    homeDashboardRepository.addMedication(
                            new MedicationRecord("默认药物", "1粒", taken, timestamp, null));
                    break;
                }
                case "bowel": {
                    int bristolType = value instanceof Number ? ((Number) value).intValue() : 3;
                    homeDashboardRepository.addBowelMovement(
                            new BowelMovement(bristolType, "BROWN", "MEDIUM",
                                    "NORMAL", "NORMAL", 120, timestamp, null));
                    break;
                }
                case "weight": {
                    float weight = value instanceof Number ? ((Number) value).floatValue() : 70f;
                    homeDashboardRepository.addWeight(new WeightRecord(weight, timestamp, null));
                    break;
                }
                default:
                    break;
            }
        });
    }
}
