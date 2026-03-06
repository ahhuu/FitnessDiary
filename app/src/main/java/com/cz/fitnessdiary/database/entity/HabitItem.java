package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "habit_item")
public class HabitItem {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "is_default")
    private boolean defaultItem;

    @ColumnInfo(name = "is_enabled")
    private boolean enabled;

    @ColumnInfo(name = "sort_order")
    private int sortOrder;

    @ColumnInfo(name = "auto_rule")
    private String autoRule;

    @ColumnInfo(name = "description")
    private String description;

    public HabitItem(String name, boolean defaultItem, boolean enabled, int sortOrder, String autoRule) {
        this.name = name;
        this.defaultItem = defaultItem;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.autoRule = autoRule;
        this.description = null;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefaultItem() {
        return defaultItem;
    }

    public void setDefaultItem(boolean defaultItem) {
        this.defaultItem = defaultItem;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getAutoRule() {
        return autoRule;
    }

    public void setAutoRule(String autoRule) {
        this.autoRule = autoRule;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}