package com.cz.fitnessdiary.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class TrainingTemplate implements Serializable {
    private String name;
    private String shortDescription;
    private String description;
    private int difficulty;
    private String goal;
    private int daysPerWeek;
    private Map<String, TemplateVersion> versions;

    public String getName() { return name; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public int getDifficulty() { return difficulty; }
    public String getGoal() { return goal; }
    public int getDaysPerWeek() { return daysPerWeek; }
    public Map<String, TemplateVersion> getVersions() { return versions; }

    public List<TemplateExercise> getExercises() {
        if (versions != null) {
            if (versions.containsKey("gym")) {
                return versions.get("gym").getExercises();
            } else if (versions.containsKey("home")) {
                return versions.get("home").getExercises();
            } else if (versions.containsKey("bodyweight")) {
                return versions.get("bodyweight").getExercises();
            }
        }
        return null;
    }

    public List<TemplateExercise> getExercisesForVersion(String versionKey) {
        if (versions != null && versions.containsKey(versionKey)) {
            return versions.get(versionKey).getExercises();
        }
        return getExercises();
    }

    public static class TemplateVersion implements Serializable {
        private String equipment;
        private List<TemplateExercise> exercises;

        public String getEquipment() { return equipment; }
        public List<TemplateExercise> getExercises() { return exercises; }
    }
}
