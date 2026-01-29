package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 食物记录实体类
 * 存储每日的饮食记录，包括食物名称和热量
 */
@Entity(tableName = "food_record")
public class FoodRecord {

    @PrimaryKey(autoGenerate = true)
    private int foodId;

    @ColumnInfo(name = "food_name")
    private String foodName; // 食物名称

    @ColumnInfo(name = "calories")
    private int calories; // 热量，单位：千卡

    @ColumnInfo(name = "record_date")
    private long recordDate; // 记录日期时间戳

    // 构造函数
    public FoodRecord(String foodName, int calories, long recordDate) {
        this.foodName = foodName;
        this.calories = calories;
        this.recordDate = recordDate;
    }

    // Getter 和 Setter 方法
    public int getFoodId() {
        return foodId;
    }

    public void setFoodId(int foodId) {
        this.foodId = foodId;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public int getCalories() {
        return calories;
    }

    public void setCalories(int calories) {
        this.calories = calories;
    }

    public long getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(long recordDate) {
        this.recordDate = recordDate;
    }

    // === 2.1 新增字段 (Plan 8) ===
    @ColumnInfo(name = "protein")
    private double protein; // 蛋白质含量 (g)

    @ColumnInfo(name = "carbs")
    private double carbs; // 碳水含量 (g)

    // === Plan 9 新增字段 ===
    @ColumnInfo(name = "meal_type")
    private int mealType; // 0=早餐, 1=午餐, 2=晚餐, 3=加餐

    @ColumnInfo(name = "servings")
    private float servings; // 摄入份数

    @ColumnInfo(name = "serving_unit")
    private String servingUnit; // 份数单位 (如 "碗", "个")

    public double getProtein() {
        return protein;
    }

    public void setProtein(double protein) {
        this.protein = protein;
    }

    public double getCarbs() {
        return carbs;
    }

    public void setCarbs(double carbs) {
        this.carbs = carbs;
    }

    public int getMealType() {
        return mealType;
    }

    public void setMealType(int mealType) {
        this.mealType = mealType;
    }

    public float getServings() {
        return servings;
    }

    public void setServings(float servings) {
        this.servings = servings;
    }

    public String getServingUnit() {
        return servingUnit;
    }

    public void setServingUnit(String servingUnit) {
        this.servingUnit = servingUnit;
    }
}
