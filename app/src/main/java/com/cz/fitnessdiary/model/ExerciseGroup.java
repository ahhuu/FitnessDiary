package com.cz.fitnessdiary.model;

import com.cz.fitnessdiary.database.entity.ExerciseLibrary;

import java.util.List;

public class ExerciseGroup {
    private String bodyPart;
    private List<ExerciseLibrary> exercises;
    private boolean expanded = false;

    public ExerciseGroup(String bodyPart, List<ExerciseLibrary> exercises) {
        this.bodyPart = bodyPart;
        this.exercises = exercises;
    }

    public String getBodyPart() { return bodyPart; }
    public List<ExerciseLibrary> getExercises() { return exercises; }
    public boolean isExpanded() { return expanded; }
    public void toggleExpanded() { expanded = !expanded; }
}
