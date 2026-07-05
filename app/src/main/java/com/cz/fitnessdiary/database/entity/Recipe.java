package com.cz.fitnessdiary.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recipe")
public class Recipe {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    @NonNull
    @ColumnInfo(name = "foods_json")
    private String foodsJson;

    @ColumnInfo(name = "total_calories", defaultValue = "0")
    private float totalCalories;

    @ColumnInfo(name = "meal_type", defaultValue = "-1")
    private int mealType; // 0=早餐,1=午餐,2=晚餐,3=加餐,-1=通用

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    private boolean isFavorite;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public Recipe(String name, String foodsJson, float totalCalories, int mealType,
                  boolean isFavorite, long createdAt, long updatedAt) {
        this.name = name;
        this.foodsJson = foodsJson;
        this.totalCalories = totalCalories;
        this.mealType = mealType;
        this.isFavorite = isFavorite;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -- Getters --
    public long getId() { return id; }
    public String getName() { return name; }
    public String getFoodsJson() { return foodsJson; }
    public float getTotalCalories() { return totalCalories; }
    public int getMealType() { return mealType; }
    public boolean isFavorite() { return isFavorite; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    // -- Setters --
    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setFoodsJson(String foodsJson) { this.foodsJson = foodsJson; }
    public void setTotalCalories(float totalCalories) { this.totalCalories = totalCalories; }
    public void setMealType(int mealType) { this.mealType = mealType; }
    public void setIsFavorite(boolean isFavorite) { this.isFavorite = isFavorite; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
