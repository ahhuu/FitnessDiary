package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.model.MealSection;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.utils.CalorieCalculatorUtils;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 饮食记录 ViewModel - 2.0 智能化版本
 * 核心功能：食物库联想、自动热量计算、智能反馈
 */
public class DietViewModel extends AndroidViewModel {

    private FoodRecordRepository foodRecordRepository;
    private FoodLibraryRepository foodLibraryRepository;
    private UserRepository userRepository;
    private ExecutorService executorService;

    // LiveData
    private MutableLiveData<Long> selectedDate = new MutableLiveData<>();
    private LiveData<Integer> todayTotalCalories;
    private LiveData<List<FoodRecord>> todayFoodRecords;
    private LiveData<Double> todayTotalProtein; // 今日总蛋白质 (g)
    private LiveData<Double> todayTotalCarbs; // 今日总碳水 (g)
    private LiveData<User> currentUser;
    private MutableLiveData<String> smartFeedback = new MutableLiveData<>("");
    private MutableLiveData<Integer> progressColor = new MutableLiveData<>();
    private LiveData<java.util.Set<Long>> recordedDates; // 有记录的日期集合 (0点时间戳)

    // Plan 10 新增字段
    private LiveData<List<MealSection>> mealSections; // 固定4个餐段

    public DietViewModel(@NonNull Application application) {
        super(application);
        foodRecordRepository = new FoodRecordRepository(application);
        foodLibraryRepository = new FoodLibraryRepository(application);
        userRepository = new UserRepository(application);
        executorService = Executors.newSingleThreadExecutor();

        // 初始化为今天
        selectedDate.setValue(DateUtils.getTodayStartTimestamp());

        // 核心：基于 selectedDate 动态切换数据源
        todayTotalCalories = androidx.lifecycle.Transformations.switchMap(selectedDate, date -> {
            long dayEnd = date + 24 * 60 * 60 * 1000L;
            return foodRecordRepository.getTotalCaloriesByDateRange(date, dayEnd);
        });

        todayFoodRecords = androidx.lifecycle.Transformations.switchMap(selectedDate, date -> {
            long dayEnd = date + 24 * 60 * 60 * 1000L;
            return androidx.lifecycle.Transformations.map(
                    foodRecordRepository.getRecordsByDateRange(date, dayEnd),
                    records -> {
                        if (records != null) {
                            java.util.Collections.sort(records, (r1, r2) -> {
                                if (r1.getMealType() != r2.getMealType()) {
                                    return Integer.compare(r1.getMealType(), r2.getMealType());
                                }
                                return Long.compare(r2.getRecordDate(), r1.getRecordDate());
                            });
                        }
                        return records;
                    });
        });

        todayTotalProtein = androidx.lifecycle.Transformations.map(todayFoodRecords, records -> {
            double total = 0;
            if (records != null) {
                for (FoodRecord record : records) {
                    total += record.getProtein();
                }
            }
            return total;
        });

        todayTotalCarbs = androidx.lifecycle.Transformations.map(todayFoodRecords, records -> {
            double total = 0;
            if (records != null) {
                for (FoodRecord record : records) {
                    total += record.getCarbs();
                }
            }
            return total;
        });

        // 获取用户信息
        currentUser = userRepository.getUser();

        // 餐段数据响应 (Plan 10)
        mealSections = androidx.lifecycle.Transformations.map(todayFoodRecords, records -> {
            List<MealSection> sections = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                sections.add(new MealSection(i));
            }
            if (records != null) {
                for (FoodRecord record : records) {
                    int mealType = record.getMealType();
                    if (mealType >= 0 && mealType < 4) {
                        sections.get(mealType).addFoodRecord(record);
                    }
                }
            }
            return sections;
        });
        // 初始化记录日期集合 (Plan 13: 高亮日历)
        recordedDates = androidx.lifecycle.Transformations.map(
                foodRecordRepository.getAllRecordTimestamps(),
                timestamps -> {
                    java.util.Set<Long> dates = new java.util.HashSet<>();
                    if (timestamps != null) {
                        for (Long ts : timestamps) {
                            dates.add(DateUtils.getUtcDayStartTimestamp(ts));
                        }
                    }
                    return dates;
                });
    }

    /**
     * 获取所有有记录日期的时间戳集合 (0点)
     */
    public LiveData<java.util.Set<Long>> getRecordedDates() {
        return recordedDates;
    }

    /**
     * 获取当前选中的日期 (0点时间戳)
     */
    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }

    /**
     * 设置选中日期
     */
    public void setSelectedDate(long timestamp) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(timestamp));
    }

    /**
     * 切换到前一天
     */
    public void toPreviousDay() {
        Long current = selectedDate.getValue();
        if (current != null) {
            selectedDate.setValue(current - 24 * 60 * 60 * 1000L);
        }
    }

    /**
     * 切换到后一天
     */
    public void toNextDay() {
        Long current = selectedDate.getValue();
        if (current != null) {
            selectedDate.setValue(current + 24 * 60 * 60 * 1000L);
        }
    }

    /**
     * 获取选定日期的总热量
     */
    public LiveData<Integer> getTodayTotalCalories() {
        return todayTotalCalories;
    }

    public LiveData<Double> getTodayTotalProtein() {
        return todayTotalProtein;
    }

    public LiveData<Double> getTodayTotalCarbs() {
        return todayTotalCarbs;
    }

    /**
     * 获取今日食物记录列表
     */
    public LiveData<List<FoodRecord>> getTodayFoodRecords() {
        return todayFoodRecords;
    }

    // Plan 10: 获取餐段数据
    public LiveData<List<MealSection>> getMealSections() {
        return mealSections;
    }

    /**
     * 获取当前用户信息（包含目标热量）
     */
    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    /**
     * 获取智能反馈消息
     */
    public LiveData<String> getSmartFeedback() {
        return smartFeedback;
    }

    /**
     * 获取进度条颜色（根据目标类型变化）
     */
    public LiveData<Integer> getProgressColor() {
        return progressColor;
    }

    /**
     * 添加食物记录（智能版 - Plan 9）
     * 支持按份数和餐点类型记录
     *
     * @param foodName 食物名称
     * @param servings 份数
     * @param mealType 餐点类型 (0=早餐, 1=午餐, 2=晚餐, 3=加餐)
     */
    public void addFoodRecordSmart(String foodName, float servings, int mealType) {
        executorService.execute(() -> {
            // 1. 从食物库查询食物
            FoodLibrary food = foodLibraryRepository.getFoodByName(foodName);

            int calories = 0;
            double protein = 0;
            double carbs = 0;

            // 默认每份重量 (如果没有查到食物库，默认100g便于计算)
            int weightPerUnit = 100;

            if (food != null) {
                weightPerUnit = food.getWeightPerUnit(); // 获取单份重量 (如 1碗=150g)

                // 2. 根据份数自动计算热量和宏量
                // 公式：总热量 = 份数 * 单份重量 * (每100g热量 / 100)
                double totalWeight = servings * weightPerUnit;
                double ratio = totalWeight / 100.0;

                calories = (int) (food.getCaloriesPer100g() * ratio);
                protein = food.getProteinPer100g() * ratio;
                carbs = food.getCarbsPer100g() * ratio;
            }

            // 3. 创建并保存记录
            long baseDate = selectedDate.getValue() != null ? selectedDate.getValue()
                    : DateUtils.getTodayStartTimestamp();
            long finalRecordDate;
            if (DateUtils.isToday(baseDate)) {
                finalRecordDate = System.currentTimeMillis();
            } else {
                finalRecordDate = baseDate; // 历史日期默认存于 0 点
            }

            FoodRecord record = new FoodRecord(foodName, calories, finalRecordDate);
            record.setProtein(protein);
            record.setCarbs(carbs);
            record.setMealType(mealType);
            record.setServings(servings);
            if (food != null) {
                record.setServingUnit(food.getServingUnit());
            } else {
                record.setServingUnit("g"); // 默认单位
            }

            foodRecordRepository.insert(record);

            // 4. 更新智能反馈
            updateSmartFeedback();
        });
    }

    /**
     * 保留旧方法兼容 (按克数)
     */
    public void addFoodRecordSmart(String foodName, int weightGrams) {
        // 估算：按 100g 为一份 (或者直接调用新逻辑，这里如果不涉及 mealType 就默认加餐)
        addFoodRecordSmart(foodName, weightGrams / 100.0f, 3);
    }

    /**
     * 删除食物记录
     */
    public void deleteFoodRecord(FoodRecord record) {
        foodRecordRepository.delete(record);
        updateSmartFeedback();
    }

    /**
     * 更新智能反馈消息和进度条颜色
     */
    private void updateSmartFeedback() {
        executorService.execute(() -> {
            // 获取用户目标信息
            User user = userRepository.getUserSync();
            if (user == null) {
                smartFeedback.postValue("暂无用户信息");
                return;
            }

            int targetCalories = user.getDailyCalorieTarget();
            if (targetCalories <= 0) {
                targetCalories = 2000; // 默认值
            }

            // 计算已摄入热量
            Integer consumed = todayTotalCalories.getValue();
            if (consumed == null)
                consumed = 0;

            // 生成智能反馈消息
            int goalType = user.getGoalType();
            String message = CalorieCalculatorUtils.getCalorieDifferenceMessage(
                    consumed, targetCalories, goalType);
            smartFeedback.postValue(message);

            // 确定进度条颜色
            int color;
            float progress = CalorieCalculatorUtils.calculateProgress(consumed, targetCalories);

            if (goalType == CalorieCalculatorUtils.GOAL_LOSE_FAT) {
                // 减脂模式：超过目标变红
                color = (progress > 100) ? android.R.color.holo_red_light : android.R.color.holo_green_light;
            } else if (goalType == CalorieCalculatorUtils.GOAL_GAIN_MUSCLE) {
                // 增肌模式：未达标变黄，达标变绿
                color = (progress < 100) ? android.R.color.holo_orange_light : android.R.color.holo_green_light;
            } else {
                // 保持模式：绿色
                color = android.R.color.holo_green_light;
            }

            progressColor.postValue(color);
        });
    }

    /**
     * 搜索食物（用于 AutoCompleteTextView）
     */
    public List<FoodLibrary> searchFoods(String keyword) {
        return foodLibraryRepository.searchFoods(keyword);
    }

    public List<FoodLibrary> getAllFoodsSync() {
        return foodLibraryRepository.getAllFoodsSync();
    }

    /**
     * Plan 32: 添加自定义食物到食物库
     */
    public void insertFood(FoodLibrary food) {
        foodLibraryRepository.insert(food);
    }
}
