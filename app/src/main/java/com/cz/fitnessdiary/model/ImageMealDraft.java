package com.cz.fitnessdiary.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImageMealDraft implements Serializable {
    private String mealName;
    private int mealType;
    private float servings;
    private String servingUnit;
    private String suggestion;
    private List<ImageFoodItemDraft> items;
    private int totalCalories;
    private double totalProtein;
    private double totalCarbs;

    public ImageMealDraft() {
        mealName = "识别餐";
        mealType = 1;
        servings = 1f;
        servingUnit = "份";
        suggestion = "";
        items = new ArrayList<>();
    }

    public void recomputeTotals() {
        int calories = 0;
        double protein = 0d;
        double carbs = 0d;
        if (items != null) {
            for (ImageFoodItemDraft item : items) {
                if (item == null) continue;
                calories += Math.max(0, item.getCalories());
                protein += Math.max(0d, item.getProtein());
                carbs += Math.max(0d, item.getCarbs());
            }
        }
        totalCalories = calories;
        totalProtein = protein;
        totalCarbs = carbs;
    }

    public String getMealName() { return mealName; }
    public void setMealName(String mealName) { this.mealName = mealName; }
    public int getMealType() { return mealType; }
    public void setMealType(int mealType) { this.mealType = mealType; }
    public float getServings() { return servings; }
    public void setServings(float servings) { this.servings = servings; }
    public String getServingUnit() { return servingUnit; }
    public void setServingUnit(String servingUnit) { this.servingUnit = servingUnit; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public List<ImageFoodItemDraft> getItems() { return items; }
    public void setItems(List<ImageFoodItemDraft> items) { this.items = items; }
    public int getTotalCalories() { return totalCalories; }
    public void setTotalCalories(int totalCalories) { this.totalCalories = totalCalories; }
    public double getTotalProtein() { return totalProtein; }
    public void setTotalProtein(double totalProtein) { this.totalProtein = totalProtein; }
    public double getTotalCarbs() { return totalCarbs; }
    public void setTotalCarbs(double totalCarbs) { this.totalCarbs = totalCarbs; }
}
