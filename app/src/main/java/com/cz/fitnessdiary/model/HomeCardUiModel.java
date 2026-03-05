package com.cz.fitnessdiary.model;

public class HomeCardUiModel {

    private final String title;
    private final String subtitle;
    private final String primaryValue;
    private final int iconRes;
    private final String summaryLine;
    private final String targetRoute;
    private final long targetId;

    public HomeCardUiModel(String title, String subtitle, String primaryValue) {
        this(title, subtitle, primaryValue, 0, "", "", 0L);
    }

    public HomeCardUiModel(String title, String subtitle, String primaryValue, int iconRes, String summaryLine,
            String targetRoute, long targetId) {
        this.title = title;
        this.subtitle = subtitle;
        this.primaryValue = primaryValue;
        this.iconRes = iconRes;
        this.summaryLine = summaryLine;
        this.targetRoute = targetRoute;
        this.targetId = targetId;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getPrimaryValue() {
        return primaryValue;
    }

    public int getIconRes() {
        return iconRes;
    }

    public String getSummaryLine() {
        return summaryLine;
    }

    public String getTargetRoute() {
        return targetRoute;
    }

    public long getTargetId() {
        return targetId;
    }
}