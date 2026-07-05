package com.cz.fitnessdiary.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorite_food")
public class FavoriteFood {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "food_name")
    private String foodName;

    @ColumnInfo(name = "calories", defaultValue = "0")
    private float calories;

    @ColumnInfo(name = "protein", defaultValue = "0")
    private float protein;

    @ColumnInfo(name = "carbs", defaultValue = "0")
    private float carbs;

    @ColumnInfo(name = "fat", defaultValue = "0")
    private float fat;

    @ColumnInfo(name = "food_library_id")
    private Long foodLibraryId; // nullable

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public FavoriteFood(String foodName, float calories, float protein, float carbs, float fat,
                        Long foodLibraryId, long createdAt) {
        this.foodName = foodName;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.foodLibraryId = foodLibraryId;
        this.createdAt = createdAt;
    }

    // -- Getters --
    public long getId() { return id; }
    public String getFoodName() { return foodName; }
    public float getCalories() { return calories; }
    public float getProtein() { return protein; }
    public float getCarbs() { return carbs; }
    public float getFat() { return fat; }
    public Long getFoodLibraryId() { return foodLibraryId; }
    public long getCreatedAt() { return createdAt; }

    // -- Setters --
    public void setId(long id) { this.id = id; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public void setCalories(float calories) { this.calories = calories; }
    public void setProtein(float protein) { this.protein = protein; }
    public void setCarbs(float carbs) { this.carbs = carbs; }
    public void setFat(float fat) { this.fat = fat; }
    public void setFoodLibraryId(Long foodLibraryId) { this.foodLibraryId = foodLibraryId; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
