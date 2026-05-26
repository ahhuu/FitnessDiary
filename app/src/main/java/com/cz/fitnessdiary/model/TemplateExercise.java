package com.cz.fitnessdiary.model;

import java.io.Serializable;

public class TemplateExercise implements Serializable {
    private String name;
    private int sets;
    private int reps;
    private int duration;
    private String scheduledDays;
    private String category;
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSets() { return sets; }
    public void setSets(int sets) { this.sets = sets; }
    public int getReps() { return reps; }
    public void setReps(int reps) { this.reps = reps; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public String getScheduledDays() { return scheduledDays; }
    public void setScheduledDays(String scheduledDays) { this.scheduledDays = scheduledDays; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
