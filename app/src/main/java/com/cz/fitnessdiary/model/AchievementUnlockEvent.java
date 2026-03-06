package com.cz.fitnessdiary.model;

public class AchievementUnlockEvent {
    public static final int TYPE_ACHIEVEMENT = 1;
    public static final int TYPE_MISSION = 2;

    private final int type;
    private final String achievementId;
    private final String emoji;
    private final String title;
    private final String description;

    public AchievementUnlockEvent(int type, String achievementId, String emoji, String title, String description) {
        this.type = type;
        this.achievementId = achievementId;
        this.emoji = emoji;
        this.title = title;
        this.description = description;
    }

    public int getType() {
        return type;
    }

    public String getAchievementId() {
        return achievementId;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}

