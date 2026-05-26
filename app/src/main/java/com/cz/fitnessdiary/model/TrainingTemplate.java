package com.cz.fitnessdiary.model;

import java.io.Serializable;
import java.util.List;

public class TrainingTemplate implements Serializable {
    private String name;
    private String shortDescription;
    private String description;
    private int difficulty;
    private String goal;
    private int daysPerWeek;
    private List<TemplateExercise> exercises;

    public String getName() { return name; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public int getDifficulty() { return difficulty; }
    public String getGoal() { return goal; }
    public int getDaysPerWeek() { return daysPerWeek; }
    public List<TemplateExercise> getExercises() { return exercises; }
}
