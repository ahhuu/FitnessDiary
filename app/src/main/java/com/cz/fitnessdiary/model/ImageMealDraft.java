package com.cz.fitnessdiary.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImageMealDraft implements Serializable {
    public static final String SOURCE_IMAGE = "IMAGE";
    public static final String SOURCE_TEXT = "TEXT";
    public static final String SOURCE_LIBRARY = "LIBRARY";
    public static final String SOURCE_BARCODE = "BARCODE";

    private String draftId = java.util.UUID.randomUUID().toString();
    private String sourceType = SOURCE_IMAGE;
    private String originalText = "";
    private String unmatchedText = "";
    private String mealName = "识别餐食";
    private int mealType = 1;
    private float servings = 1f;
    private String servingUnit = "份";
    private String suggestion = "";
    private List<ImageFoodItemDraft> items = new ArrayList<>();
    private int totalCalories;
    private double totalProtein;
    private double totalCarbs;
    private double totalFat;

    public void recomputeTotals() {
        int calories = 0;
        double protein = 0d;
        double carbs = 0d;
        double fat = 0d;
        if (items != null) {
            for (ImageFoodItemDraft item : items) {
                if (item == null) continue;
                calories += Math.max(0, item.getCalories());
                protein += Math.max(0d, item.getProtein());
                carbs += Math.max(0d, item.getCarbs());
                fat += Math.max(0d, item.getFat());
            }
        }
        totalCalories = calories;
        totalProtein = protein;
        totalCarbs = carbs;
        totalFat = fat;
    }

    public String getMealName() { return mealName; }
    public void setMealName(String value) { mealName = value == null ? "识别餐食" : value; }
    public int getMealType() { return mealType; }
    public void setMealType(int value) { mealType = value; }
    public float getServings() { return servings; }
    public void setServings(float value) { servings = value > 0 ? value : 1f; }
    public String getServingUnit() { return servingUnit; }
    public void setServingUnit(String value) { servingUnit = value == null ? "份" : value; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String value) { suggestion = value == null ? "" : value; }
    public List<ImageFoodItemDraft> getItems() { return items; }
    public void setItems(List<ImageFoodItemDraft> value) { items = value == null ? new ArrayList<>() : value; }
    public int getTotalCalories() { return totalCalories; }
    public void setTotalCalories(int value) { totalCalories = Math.max(0, value); }
    public double getTotalProtein() { return totalProtein; }
    public void setTotalProtein(double value) { totalProtein = Math.max(0d, value); }
    public double getTotalCarbs() { return totalCarbs; }
    public void setTotalCarbs(double value) { totalCarbs = Math.max(0d, value); }
    public double getTotalFat() { return totalFat; }
    public void setTotalFat(double value) { totalFat = Math.max(0d, value); }
    public String getDraftId() { return draftId; }
    public void setDraftId(String value) {
        draftId = value == null || value.trim().isEmpty()
                ? java.util.UUID.randomUUID().toString() : value.trim();
    }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) {
        sourceType = value == null || value.trim().isEmpty() ? SOURCE_IMAGE : value.trim();
    }
    public boolean isImageSource() { return SOURCE_IMAGE.equals(sourceType); }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String value) { originalText = value == null ? "" : value.trim(); }
    public String getUnmatchedText() { return unmatchedText; }
    public void setUnmatchedText(String value) { unmatchedText = value == null ? "" : value.trim(); }
}
