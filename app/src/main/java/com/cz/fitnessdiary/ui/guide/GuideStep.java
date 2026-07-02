package com.cz.fitnessdiary.ui.guide;

public class GuideStep {
    public int targetViewId;
    public String title;
    public String description;
    public Anchor anchor;

    public enum Anchor { TOP, BOTTOM, LEFT, RIGHT }

    public GuideStep(int targetViewId, String title, String description, Anchor anchor) {
        this.targetViewId = targetViewId;
        this.title = title;
        this.description = description;
        this.anchor = anchor;
    }
}
