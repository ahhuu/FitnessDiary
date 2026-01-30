package com.cz.fitnessdiary.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一级食物分组模型 (大类)
 */
public class FoodMainGroup {
    private String name;
    private String emoji;
    private List<FoodGroup> subGroups = new ArrayList<>();
    private boolean isExpanded = false; // 默认不展开大类

    public FoodMainGroup(String name, String emoji) {
        this.name = name;
        this.emoji = emoji;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    public List<FoodGroup> getSubGroups() {
        return subGroups;
    }

    public void addSubGroup(FoodGroup subGroup) {
        subGroups.add(subGroup);
    }

    public int getTotalFoodCount() {
        int count = 0;
        for (FoodGroup group : subGroups) {
            count += group.getFoodCount();
        }
        return count;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public void toggleExpanded() {
        this.isExpanded = !this.isExpanded;
    }
}
