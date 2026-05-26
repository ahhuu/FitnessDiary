package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "menstrual_cycle",
        indices = {@Index(name = "index_menstrual_cycle_start", value = {"start_date"}, orders = {Index.Order.DESC})})
public class MenstrualCycle {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "start_date")
    private long startDate;

    @ColumnInfo(name = "end_date")
    private Long endDate; // nullable = ongoing

    @ColumnInfo(name = "flow_intensity")
    private String flowIntensity; // LIGHT, MEDIUM, HEAVY

    private String symptoms; // comma-separated

    private String mood; // comma-separated

    private String notes;

    private long timestamp;

    public MenstrualCycle(long startDate, Long endDate, String flowIntensity,
                          String symptoms, String mood, String notes, long timestamp) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.flowIntensity = flowIntensity;
        this.symptoms = symptoms;
        this.mood = mood;
        this.notes = notes;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getStartDate() { return startDate; }
    public void setStartDate(long startDate) { this.startDate = startDate; }
    public Long getEndDate() { return endDate; }
    public void setEndDate(Long endDate) { this.endDate = endDate; }
    public String getFlowIntensity() { return flowIntensity; }
    public void setFlowIntensity(String flowIntensity) { this.flowIntensity = flowIntensity; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
