package com.cz.fitnessdiary.model;

/**
 * 成就数据模型 - Plan 10
 * 用于个人中心的动态成就墙
 */
public class Achievement {
    private String id; // 成就 ID (如 "first_day")
    private String title; // 标题 (如 "初出茅庐")
    private String description; // 描述 (如 "完成第一次训练")
    private boolean isUnlocked; // 是否解锁
    private int iconRes; // 图标资源 ID

    public Achievement(String id, String title, String description, int iconRes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.iconRes = iconRes;
        this.isUnlocked = false;
    }

    public Achievement(String id, String title, String description, int iconRes, boolean isUnlocked) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.iconRes = iconRes;
        this.isUnlocked = isUnlocked;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isUnlocked() {
        return isUnlocked;
    }

    public void setUnlocked(boolean unlocked) {
        isUnlocked = unlocked;
    }

    public int getIconRes() {
        return iconRes;
    }

    public void setIconRes(int iconRes) {
        this.iconRes = iconRes;
    }
}
