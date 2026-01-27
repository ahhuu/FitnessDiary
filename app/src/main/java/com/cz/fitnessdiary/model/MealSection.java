package com.cz.fitnessdiary.model;

import com.cz.fitnessdiary.database.entity.FoodRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * 餐段数据模型 - Plan 10
 * 用于在 UI 层展示固定的 4 个餐段卡片
 */
public class MealSection {
    private int mealType; // 0=早餐, 1=午餐, 2=晚餐, 3=加餐
    private List<FoodRecord> foodRecords; // 该餐段的食物记录
    private int totalCalories; // 该餐总热量
    private boolean isExpanded; // 是否展开

    public MealSection(int mealType) {
        this.mealType = mealType;
        this.foodRecords = new ArrayList<>();
        this.totalCalories = 0;
        this.isExpanded = true; // 默认展开
    }

    public MealSection(int mealType, List<FoodRecord> foodRecords) {
        this.mealType = mealType;
        this.foodRecords = foodRecords;
        this.isExpanded = true;
        calculateTotalCalories();
    }

    public int getMealType() {
        return mealType;
    }

    public void setMealType(int mealType) {
        this.mealType = mealType;
    }

    public List<FoodRecord> getFoodRecords() {
        return foodRecords;
    }

    public void setFoodRecords(List<FoodRecord> foodRecords) {
        this.foodRecords = foodRecords;
        calculateTotalCalories();
    }

    public void addFoodRecord(FoodRecord record) {
        this.foodRecords.add(record);
        this.totalCalories += record.getCalories();
    }

    public int getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(int totalCalories) {
        this.totalCalories = totalCalories;
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

    public boolean isEmpty() {
        return foodRecords == null || foodRecords.isEmpty();
    }

    /**
     * 获取餐点名称
     */
    public String getMealName() {
        switch (mealType) {
            case 0:
                return "早餐";
            case 1:
                return "午餐";
            case 2:
                return "晚餐";
            case 3:
                return "加餐";
            default:
                return "未知";
        }
    }

    /**
     * 计算该餐总热量
     */
    private void calculateTotalCalories() {
        this.totalCalories = 0;
        if (foodRecords != null) {
            for (FoodRecord record : foodRecords) {
                this.totalCalories += record.getCalories();
            }
        }
    }
}
