package com.cz.fitnessdiary.model;

import com.cz.fitnessdiary.database.entity.TrainingPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * 训练计划分组数据模型 - Plan 10
 * 用于在 UI 层展示分类折叠列表
 */
public class PlanGroup {
    private String category; // 分类名称 (如 "胸部训练")
    private List<TrainingPlan> plans; // 该分类下的计划列表
    private boolean isExpanded; // 是否展开

    public PlanGroup(String category) {
        this.category = category;
        this.plans = new ArrayList<>();
        this.isExpanded = true; // 默认展开
    }

    public PlanGroup(String category, List<TrainingPlan> plans) {
        this.category = category;
        this.plans = plans;
        this.isExpanded = true;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<TrainingPlan> getPlans() {
        return plans;
    }

    public void setPlans(List<TrainingPlan> plans) {
        this.plans = plans;
    }

    public void addPlan(TrainingPlan plan) {
        this.plans.add(plan);
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public void toggleExpanded() {
        isExpanded = !isExpanded;
    }

    public int getPlanCount() {
        return plans == null ? 0 : plans.size();
    }
}
