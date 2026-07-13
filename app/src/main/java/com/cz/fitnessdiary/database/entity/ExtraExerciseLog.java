package com.cz.fitnessdiary.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A one-day exercise entry that is intentionally independent from TrainingPlan. */
@Entity(tableName = "extra_exercise_log", indices = {@Index("date")})
public class ExtraExerciseLog implements java.io.Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "date")
    private long date;

    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "body_part")
    private String bodyPart;

    @ColumnInfo(name = "category")
    private String category;

    @ColumnInfo(name = "library_id", defaultValue = "0")
    private long libraryId;

    @ColumnInfo(name = "sets", defaultValue = "0")
    private int sets;

    @ColumnInfo(name = "reps", defaultValue = "0")
    private int reps;

    @ColumnInfo(name = "weight", defaultValue = "0")
    private float weight;

    @ColumnInfo(name = "duration", defaultValue = "0")
    private int duration;

    @ColumnInfo(name = "is_completed", defaultValue = "0")
    private boolean isCompleted;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public ExtraExerciseLog(long date, @NonNull String name, String bodyPart, String category,
            long libraryId, int sets, int reps, float weight, int duration,
            boolean isCompleted, long createdAt) {
        this.date = date;
        this.name = name;
        this.bodyPart = bodyPart;
        this.category = category;
        this.libraryId = libraryId;
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
        this.duration = duration;
        this.isCompleted = isCompleted;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public void setBodyPart(String bodyPart) {
        this.bodyPart = bodyPart;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(long libraryId) {
        this.libraryId = libraryId;
    }

    public int getSets() {
        return sets;
    }

    public void setSets(int sets) {
        this.sets = sets;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
