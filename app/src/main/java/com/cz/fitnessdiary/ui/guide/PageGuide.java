package com.cz.fitnessdiary.ui.guide;

import java.util.List;

public class PageGuide {
    public String pageKey;
    public List<GuideStep> steps;

    public PageGuide(String pageKey, List<GuideStep> steps) {
        this.pageKey = pageKey;
        this.steps = steps;
    }
}
