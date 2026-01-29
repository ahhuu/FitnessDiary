package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.model.PlanGroup;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练计划 ViewModel - 3.0 版本 (Plan 10)
 * 新增计划库概览和分组展示功能
 */
public class PlanViewModel extends AndroidViewModel {

    private TrainingPlanRepository repository;
    private LiveData<List<TrainingPlan>> allPlans;

    // Plan 10 新增字段
    private MutableLiveData<String> filterMode = new MutableLiveData<>("基础");
    private LiveData<Integer> totalPlanCount;
    private LiveData<String> coveredCategories;
    private LiveData<List<PlanGroup>> groupedPlans;
    private LiveData<List<String>> uniqueCategories;

    public PlanViewModel(@NonNull Application application) {
        super(application);
        repository = new TrainingPlanRepository(application);
        allPlans = repository.getAllPlans();

        // [v1.2] 从持久化存储读取模式，默认为基础
        android.content.SharedPreferences sp = application.getSharedPreferences("fitness_diary_prefs",
                android.content.Context.MODE_PRIVATE);
        String savedMode = sp.getString("current_plan_mode", "基础");
        filterMode.setValue(savedMode);

        // [v1.2] 执行迁移：将无前缀的分类归入基础
        repository.migrateToBasic();

        // 过滤后的计划列表 (根据当前模式前缀)
        LiveData<List<TrainingPlan>> filteredPlans = Transformations.switchMap(filterMode, mode -> {
            return Transformations.map(allPlans, plans -> {
                List<TrainingPlan> filtered = new ArrayList<>();
                if (plans != null) {
                    for (TrainingPlan plan : plans) {
                        String cat = plan.getCategory();
                        if (cat != null && cat.startsWith(mode + "-")) {
                            filtered.add(plan);
                        }
                    }
                }
                return filtered;
            });
        });

        // 计算总计划数 (基于过滤后)
        totalPlanCount = Transformations.map(filteredPlans, plans -> {
            return plans == null ? 0 : plans.size();
        });

        // 提取覆盖部位 (移除前缀显示更美白)
        coveredCategories = Transformations.map(filteredPlans, plans -> {
            if (plans == null || plans.isEmpty()) {
                return "暂无";
            }

            java.util.Set<String> categories = new java.util.LinkedHashSet<>();
            for (TrainingPlan plan : plans) {
                String category = plan.getCategory();
                if (category != null && category.contains("-")) {
                    category = category.split("-")[1]; // 去掉前缀
                }
                if (category != null && !category.trim().isEmpty() && !category.equals("未分类")) {
                    categories.add(category.trim());
                }
            }

            if (categories.isEmpty()) {
                return "未分类";
            }

            StringBuilder sb = new StringBuilder();
            for (String cat : categories) {
                if (sb.length() > 0)
                    sb.append("、");
                sb.append(cat);
            }
            return sb.toString();
        });

        // 分组计划列表 (展示时去掉前缀)
        groupedPlans = Transformations.map(filteredPlans, plans -> {
            List<PlanGroup> groups = new ArrayList<>();
            if (plans == null || plans.isEmpty()) {
                return groups;
            }

            Map<String, PlanGroup> groupMap = new LinkedHashMap<>();
            for (TrainingPlan plan : plans) {
                String fullCategory = plan.getCategory();
                String displayCategory = fullCategory;
                if (fullCategory != null && fullCategory.contains("-")) {
                    displayCategory = fullCategory.split("-")[1];
                }

                if (displayCategory == null || displayCategory.trim().isEmpty()) {
                    displayCategory = "未分类";
                }

                if (!groupMap.containsKey(displayCategory)) {
                    groupMap.put(displayCategory, new PlanGroup(displayCategory));
                }
                groupMap.get(displayCategory).addPlan(plan);
            }

            groups.addAll(groupMap.values());
            return groups;
        });

        // 提取唯一分类列表 (用于联想，根据当前模式自动带上前缀)
        uniqueCategories = Transformations.map(allPlans, plans -> {
            java.util.Set<String> categories = new java.util.LinkedHashSet<>();
            if (plans != null) {
                for (TrainingPlan plan : plans) {
                    String category = plan.getCategory();
                    if (category != null && category.contains("-")) {
                        category = category.split("-")[1];
                    }
                    if (category != null && !category.trim().isEmpty() && !category.equals("无分类")) {
                        categories.add(category.trim());
                    }
                }
            }
            return new ArrayList<>(categories);
        });
    }

    public LiveData<List<TrainingPlan>> getAllPlans() {
        return allPlans;
    }

    public LiveData<Integer> getTotalPlanCount() {
        return totalPlanCount;
    }

    public LiveData<String> getCoveredCategories() {
        return coveredCategories;
    }

    public LiveData<List<PlanGroup>> getGroupedPlans() {
        return groupedPlans;
    }

    public void insert(TrainingPlan plan) {
        repository.insert(plan);
    }

    public void update(TrainingPlan plan) {
        repository.update(plan);
    }

    public void delete(TrainingPlan plan) {
        repository.delete(plan);
    }

    // 2.0 方法别名
    public void addPlan(TrainingPlan plan) {
        insert(plan);
    }

    public void deletePlan(TrainingPlan plan) {
        delete(plan);
    }

    // Plan 10 新增方法
    public void updatePlan(TrainingPlan plan) {
        update(plan);
    }

    // Plan 28: 批量更新分类名称
    public void updateCategory(String oldCategory, String newCategory) {
        repository.updateCategory(oldCategory, newCategory);
    }

    public LiveData<List<String>> getUniqueCategories() {
        return uniqueCategories;
    }

    // [v1.2] 模式切换控制
    public LiveData<String> getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(String mode) {
        filterMode.setValue(mode);
        // [v1.2] 持久化选择
        android.content.SharedPreferences sp = getApplication().getSharedPreferences("fitness_diary_prefs",
                android.content.Context.MODE_PRIVATE);
        sp.edit().putString("current_plan_mode", mode).apply();
    }

    /**
     * [v1.2] 批量注入进阶计划 (仅当进阶计划为空时调用)
     */
    public void seedAdvancedPlans() {
        new Thread(() -> {
            List<TrainingPlan> all = repository.getAllPlansSync();
            boolean hasAdvanced = false;
            if (all != null) {
                for (TrainingPlan p : all) {
                    if (p.getCategory() != null && p.getCategory().startsWith("进阶-")) {
                        hasAdvanced = true;
                        break;
                    }
                }
            }

            if (!hasAdvanced) {
                List<TrainingPlan> advancedList = new ArrayList<>();
                long now = System.currentTimeMillis();

                advancedList.add(createAdvancedPlan("支架俯卧撑", "胸", "基础推力进阶", 4, 15, 0, "1,3,5", now));
                advancedList.add(createAdvancedPlan("哑铃卧推", "胸", "增加肌肉维度", 4, 12, 0, "1,3,5", now));
                advancedList.add(createAdvancedPlan("窄距支架臂屈伸", "肱三", "手臂孤立训练", 4, 10, 0, "1,3,5", now));

                advancedList.add(createAdvancedPlan("引体向上", "背", "顶级拉力训练", 4, 8, 0, "2,4,6", now));
                advancedList.add(createAdvancedPlan("杠铃划船", "背", "背部核心训练", 4, 12, 0, "2,4,6", now));
                advancedList.add(createAdvancedPlan("阿诺德推举", "肩", "肩部全方位雕刻", 4, 12, 0, "2,4,6", now));

                advancedList.add(createAdvancedPlan("动态平板支撑", "腹", "动态核心精雕", 3, 1, 60, "1,2,3,4,5,6,7", now));
                advancedList.add(createAdvancedPlan("悬垂举腿", "腹", "极致下腹轰炸", 4, 15, 0, "2,4,6", now));
                advancedList.add(createAdvancedPlan("龙旗", "腹", "李小龙同款核心王牌", 3, 8, 0, "2,4,6", now));

                repository.insertAll(advancedList);
            }
        }).start();
    }

    private TrainingPlan createAdvancedPlan(String name, String category, String desc, int sets, int reps, int duration,
            String days, long now) {
        TrainingPlan plan = new TrainingPlan(name, desc, now, sets, reps, null);
        plan.setCategory("进阶-" + category);
        plan.setDuration(duration);
        plan.setScheduledDays(days);
        return plan;
    }
}
