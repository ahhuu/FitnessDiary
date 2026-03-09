package com.cz.fitnessdiary.model;

import java.io.Serializable;

public class ImageFoodItemDraft implements Serializable {
    private String name;
    private int calories;
    private double protein;
    private double carbs;
    private String unit;
    private String category;

    public ImageFoodItemDraft() {
        this("", 0, 0d, 0d, "份", "其他");
    }

    public ImageFoodItemDraft(String name, int calories, double protein, double carbs, String unit, String category) {
        this.name = name;
        this.calories = Math.max(0, calories);
        this.protein = Math.max(0d, protein);
        this.carbs = Math.max(0d, carbs);
        this.unit = unit == null ? "份" : unit;
        this.category = category == null ? "其他" : category;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getCalories() { return calories; }
    public void setCalories(int calories) { this.calories = Math.max(0, calories); }
    public double getProtein() { return protein; }
    public void setProtein(double protein) { this.protein = Math.max(0d, protein); }
    public double getCarbs() { return carbs; }
    public void setCarbs(double carbs) { this.carbs = Math.max(0d, carbs); }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
