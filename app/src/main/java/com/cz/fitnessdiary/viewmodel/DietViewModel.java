package com.cz.fitnessdiary.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.model.FoodScanFlowState;
import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;
import com.cz.fitnessdiary.model.MealSection;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.service.FoodImageAnalyzer;
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

    private final FoodRecordRepository foodRecordRepository;
    private final FoodLibraryRepository foodLibraryRepository;
    private final UserRepository userRepository;
    private final ExecutorService executorService;

    // LiveData
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>();
    private final LiveData<Integer> todayTotalCalories;
    private final LiveData<List<FoodRecord>> todayFoodRecords;
    private final LiveData<Double> todayTotalProtein; // 今日总蛋白质 (g)
    private final LiveData<Double> todayTotalCarbs; // 今日总碳水 (g)
    private final LiveData<User> currentUser;
    private final MediatorLiveData<String> smartFeedback = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> progressColor = new MediatorLiveData<>();
    private final LiveData<java.util.Set<Long>> recordedDates; // 有记录的日期集合 (0点时间戳)

    // Plan 10 新增字段
    private final LiveData<List<MealSection>> mealSections; // 固定4个餐段

    // 图片识别流程状态
    private final MutableLiveData<FoodScanFlowState> foodScanState = new MutableLiveData<>(
            new FoodScanFlowState(FoodScanFlowState.Stage.IDLE, 0, "", ""));
    private final MutableLiveData<ImageMealDraft> foodScanDraft = new MutableLiveData<>();
    private final FoodImageAnalyzer foodImageAnalyzer = new FoodImageAnalyzer();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int foodAnalyzeToken = 0;

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

        // 智能反馈与颜色联动 (MediatorLiveData 自动监听)
        smartFeedback.addSource(todayTotalCalories, calories -> updateSmartFeedbackInternal());
        smartFeedback.addSource(currentUser, user -> updateSmartFeedbackInternal());

        progressColor.addSource(todayTotalCalories, calories -> updateSmartFeedbackInternal());
        progressColor.addSource(currentUser, user -> updateSmartFeedbackInternal());
    }

    private void updateSmartFeedbackInternal() {
        Integer consumed = todayTotalCalories.getValue();
        User user = currentUser.getValue();
        if (user == null) {
            smartFeedback.setValue("暂无用户信息");
            return;
        }

        int targetCalories = user.getDailyCalorieTarget();
        if (targetCalories <= 0) {
            targetCalories = 2000;
        }

        int consumedVal = (consumed != null) ? consumed : 0;
        // 1. 更新消息
        smartFeedback.setValue(CalorieCalculatorUtils.getCalorieDifferenceMessage(
                consumedVal, targetCalories, user.getGoalType()));

        // 2. 更新颜色
        float progress = CalorieCalculatorUtils.calculateProgress(consumedVal, targetCalories);
        int color;
        if (progress > 100f) {
            color = R.color.diet_state_over;
        } else if (progress < 75f) {
            color = R.color.diet_state_low;
        } else {
            color = R.color.diet_state_ok;
        }
        progressColor.setValue(color);
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

    public LiveData<FoodScanFlowState> getFoodScanState() {
        return foodScanState;
    }

    public LiveData<ImageMealDraft> getFoodScanDraft() {
        return foodScanDraft;
    }

    public void resetFoodScanState() {
        foodAnalyzeToken++;
        foodScanState.setValue(new FoodScanFlowState(FoodScanFlowState.Stage.IDLE, 0, "", ""));
    }


    public void clearFoodScanDraft() {
        foodScanDraft.setValue(null);
    }

    public void analyzeMealImage(Bitmap image) {
        int token = ++foodAnalyzeToken;
        foodScanDraft.setValue(null);
        postStage(token, new FoodScanFlowState(FoodScanFlowState.Stage.UPLOAD, 15, "上传图片中", "正在准备识别图片，请稍候..."), 260);
        postStage(token, new FoodScanFlowState(FoodScanFlowState.Stage.RECOGNIZE, 42, "AI识别食物中", "正在识别食物名称与分量..."), 900);

        foodImageAnalyzer.analyze(image, new FoodImageAnalyzer.AnalyzeCallback() {
            @Override
            public void onSuccess(ImageMealDraft draft, String rawResponse, String reasoning) {
                if (token != foodAnalyzeToken) {
                    return;
                }
                mainHandler.postDelayed(() -> {
                    if (token != foodAnalyzeToken) {
                        return;
                    }
                    ImageMealDraft finalDraft = draft;
                    if (finalDraft == null) {
                        finalDraft = new ImageMealDraft();
                        finalDraft.getItems().add(new ImageFoodItemDraft("请手动补充食物", 0, 0, 0, "份", "其他"));
                        finalDraft.setSuggestion("未能可靠识别，请手动编辑后保存。");
                    }
                    finalDraft.recomputeTotals();
                    ImageMealDraft finalDraftResult = finalDraft;
                    foodScanState.setValue(new FoodScanFlowState(
                            FoodScanFlowState.Stage.NUTRITION,
                            72,
                            "营养估算中",
                            "正在计算热量、蛋白质与碳水..."));
                    mainHandler.postDelayed(() -> {
                        if (token != foodAnalyzeToken) {
                            return;
                        }
                        foodScanState.setValue(new FoodScanFlowState(
                                FoodScanFlowState.Stage.SUGGESTION,
                                90,
                                "生成可执行建议",
                                "正在整理建议与记录草稿..."));
                    }, 1200);
                    mainHandler.postDelayed(() -> {
                        if (token != foodAnalyzeToken) {
                            return;
                        }
                        foodScanState.setValue(new FoodScanFlowState(
                                FoodScanFlowState.Stage.SUCCESS,
                                100,
                                "识别完成",
                                "请确认识别结果后记录本餐"));
                    }, 2400);
                    mainHandler.postDelayed(() -> {
                        if (token != foodAnalyzeToken) {
                            return;
                        }
                        foodScanDraft.setValue(finalDraftResult);
                    }, 3800);
                }, 700);
            }

            @Override
            public void onError(String error) {
                if (token != foodAnalyzeToken) {
                    return;
                }
                String finalError = (error == null || error.trim().isEmpty()) ? "识别失败，请稍后重试" : error;
                foodScanState.setValue(FoodScanFlowState.error(finalError, true));
            }
        });
    }
    private void postStage(int token, FoodScanFlowState stage, long delayMs) {
        if (delayMs <= 0) {
            if (token == foodAnalyzeToken) {
                foodScanState.setValue(stage);
            }
            return;
        }
        mainHandler.postDelayed(() -> {
            if (token == foodAnalyzeToken) {
                foodScanState.setValue(stage);
            }
        }, delayMs);
    }

    public void saveImageMealDraft(ImageMealDraft draft, boolean syncToLibrary) {
        if (draft == null) {
            return;
        }
        executorService.execute(() -> {
            draft.recomputeTotals();
            long baseDate = selectedDate.getValue() != null ? selectedDate.getValue() : DateUtils.getTodayStartTimestamp();
            long finalRecordDate = DateUtils.isToday(baseDate) ? System.currentTimeMillis() : baseDate;

            String mealName = draft.getMealName();
            if (mealName == null || mealName.trim().isEmpty()) {
                mealName = "识别餐";
            }

            FoodRecord record = new FoodRecord(mealName.trim(), Math.max(0, draft.getTotalCalories()), finalRecordDate);
            record.setProtein(Math.max(0d, draft.getTotalProtein()));
            record.setCarbs(Math.max(0d, draft.getTotalCarbs()));
            record.setMealType(Math.max(0, Math.min(3, draft.getMealType())));
            record.setServings(draft.getServings() <= 0 ? 1f : draft.getServings());
            String unit = draft.getServingUnit();
            record.setServingUnit((unit == null || unit.trim().isEmpty()) ? "份" : unit.trim());
            foodRecordRepository.insert(record);

            if (!syncToLibrary || draft.getItems() == null) {
                return;
            }
            for (ImageFoodItemDraft item : draft.getItems()) {
                if (item == null || item.getName() == null || item.getName().trim().isEmpty()) {
                    continue;
                }
                String itemName = item.getName().trim();
                FoodLibrary existing = foodLibraryRepository.getFoodByName(itemName);
                if (existing != null) {
                    continue;
                }
                int caloriesPer100g = item.getCalories() > 0 ? item.getCalories() : 100;
                FoodLibrary food = new FoodLibrary(
                        itemName,
                        caloriesPer100g,
                        Math.max(0d, item.getProtein()),
                        Math.max(0d, item.getCarbs()),
                        (item.getUnit() == null || item.getUnit().trim().isEmpty()) ? "份" : item.getUnit().trim(),
                        100,
                        (item.getCategory() == null || item.getCategory().trim().isEmpty()) ? "其他" : item.getCategory().trim());
                foodLibraryRepository.insert(food);
            }
        });
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

            // 4. 反馈由 MediatorLiveData 自动更新，无需手动调用
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
    }

    /**
     * @deprecated 已废弃，请使用 MediatorLiveData 自动更新逻辑
     */
    @Deprecated
    private void updateSmartFeedback() {
        updateSmartFeedbackInternal();
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

    /**
     * 更新食物库中的食物
     */
    public void updateFood(FoodLibrary food) {
        foodLibraryRepository.update(food);
    }

    /**
     * 从食物库中删除食物
     */
    public void deleteFoodFromLibrary(FoodLibrary food) {
        foodLibraryRepository.delete(food);
    }
}


