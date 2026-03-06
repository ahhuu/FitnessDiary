package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "medication_record")
public class MedicationRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "dosage")
    private String dosage;

    @ColumnInfo(name = "is_taken")
    private boolean isTaken;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "note")
    private String note;

    @ColumnInfo(name = "daily_total")
    private int dailyTotal;

    public MedicationRecord(String name, String dosage, boolean isTaken, long timestamp, String note) {
        this.name = name;
        this.dosage = dosage;
        this.isTaken = isTaken;
        this.timestamp = timestamp;
        this.note = note;
        this.dailyTotal = 1;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public boolean isTaken() {
        return isTaken;
    }

    public void setTaken(boolean taken) {
        isTaken = taken;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getDailyTotal() {
        return dailyTotal <= 0 ? 1 : dailyTotal;
    }

    public void setDailyTotal(int dailyTotal) {
        this.dailyTotal = dailyTotal;
    }
}
