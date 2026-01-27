package com.cz.fitnessdiary.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 食物库实体类
 * 预置常见食物的热量数据，用于饮食记录的智能联想
 */
@Entity(tableName = "food_library")
public class FoodLibrary {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "name")
    private String name; // 食物名称（主键）

    @ColumnInfo(name = "calories_per_100g")
    private int caloriesPer100g; // 每100克的热量（千卡）

    // === 2.0 新增字段 ===
    @ColumnInfo(name = "protein_per_100g")
    private double proteinPer100g; // 每100克蛋白质含量 (g)

    @ColumnInfo(name = "carbs_per_100g")
    private double carbsPer100g; // 每100克碳水含量 (g)

    @ColumnInfo(name = "serving_unit")
    private String servingUnit; // 常用单位 (如 "个", "碗")

    @ColumnInfo(name = "weight_per_unit")
    private int weightPerUnit; // 常用单位对应的克数 (如 1碗=150g)

    @ColumnInfo(name = "category")
    private String category; // 分类 (如 "主食", "家常菜")

    // 构造函数
    public FoodLibrary(String name, int caloriesPer100g, double proteinPer100g, double carbsPer100g, String servingUnit,
            int weightPerUnit, String category) {
        this.name = name;
        this.caloriesPer100g = caloriesPer100g;
        this.proteinPer100g = proteinPer100g;
        this.carbsPer100g = carbsPer100g;
        this.servingUnit = servingUnit;
        this.weightPerUnit = weightPerUnit;
        this.category = category;
    }

    // 兼容旧构造函数 (Plan 30 更新)
    @Ignore
    public FoodLibrary(String name, int caloriesPer100g, double proteinPer100g, double carbsPer100g, String servingUnit,
            int weightPerUnit) {
        this(name, caloriesPer100g, proteinPer100g, carbsPer100g, servingUnit, weightPerUnit, "其他");
    }

    // 兼容旧构造函数（可选，如果要保留旧数据的兼容性，或者使用默认值）
    @Ignore
    public FoodLibrary(String name, int caloriesPer100g) {
        this(name, caloriesPer100g, 0, 0, "克", 100);
    }

    // Getter 和 Setter 方法
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCaloriesPer100g() {
        return caloriesPer100g;
    }

    public void setCaloriesPer100g(int caloriesPer100g) {
        this.caloriesPer100g = caloriesPer100g;
    }

    public double getProteinPer100g() {
        return proteinPer100g;
    }

    public void setProteinPer100g(double proteinPer100g) {
        this.proteinPer100g = proteinPer100g;
    }

    public double getCarbsPer100g() {
        return carbsPer100g;
    }

    public void setCarbsPer100g(double carbsPer100g) {
        this.carbsPer100g = carbsPer100g;
    }

    public String getServingUnit() {
        return servingUnit;
    }

    public void setServingUnit(String servingUnit) {
        this.servingUnit = servingUnit;
    }

    public int getWeightPerUnit() {
        return weightPerUnit;
    }

    public void setWeightPerUnit(int weightPerUnit) {
        this.weightPerUnit = weightPerUnit;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
