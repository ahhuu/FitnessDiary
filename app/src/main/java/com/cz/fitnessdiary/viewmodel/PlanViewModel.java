package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
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
    private LiveData<Integer> totalPlanCount; // 总计划数
    private LiveData<String> coveredCategories; // 覆盖部位（逗号分隔）
    private LiveData<List<PlanGroup>> groupedPlans; // 分组后的计划列表

    public PlanViewModel(@NonNull Application application) {
        super(application);
        repository = new TrainingPlanRepository(application);
        allPlans = repository.getAllPlans();

        // 计算总计划数
        totalPlanCount = Transformations.map(allPlans, plans -> {
            return plans == null ? 0 : plans.size();
        });

        // 提取覆盖部位
        coveredCategories = Transformations.map(allPlans, plans -> {
            if (plans == null || plans.isEmpty()) {
                return "暂无";
            }

            java.util.Set<String> categories = new java.util.LinkedHashSet<>();
            for (TrainingPlan plan : plans) {
                String category = plan.getCategory();
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

        // 分组计划列表
        groupedPlans = Transformations.map(allPlans, plans -> {
            List<PlanGroup> groups = new ArrayList<>();
            if (plans == null || plans.isEmpty()) {
                return groups;
            }

            // 按 category 分组
            Map<String, PlanGroup> groupMap = new LinkedHashMap<>();
            for (TrainingPlan plan : plans) {
                String category = plan.getCategory();
                if (category == null || category.trim().isEmpty()) {
                    category = "未分类";
                }

                if (!groupMap.containsKey(category)) {
                    groupMap.put(category, new PlanGroup(category));
                }
                groupMap.get(category).addPlan(plan);
            }

            groups.addAll(groupMap.values());
            return groups;
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
}
