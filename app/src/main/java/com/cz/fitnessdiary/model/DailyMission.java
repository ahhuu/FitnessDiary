package com.cz.fitnessdiary.model;

public class DailyMission {
    private final String id;
    private final String title;
    private final boolean completed;
    private final boolean custom;

    public DailyMission(String id, String title, boolean completed, boolean custom) {
        this.id = id;
        this.title = title;
        this.completed = completed;
        this.custom = custom;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isCustom() {
        return custom;
    }
}
