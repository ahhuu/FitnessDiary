package com.cz.fitnessdiary.model;

import com.cz.fitnessdiary.database.entity.FoodLibrary;
import java.util.List;

/**
 * 食物分组模型 (Plan 30)
 */
public class FoodGroup {
    private String category;
    private List<FoodLibrary> foods;
    private boolean isExpanded = true; // 默认展开

    public FoodGroup(String category, List<FoodLibrary> foods) {
        this.category = category;
        this.foods = foods;
    }

    public String getCategory() {
        return category;
    }

    public List<FoodLibrary> getFoods() {
        return foods;
    }

    public int getFoodCount() {
        return foods != null ? foods.size() : 0;
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
}
