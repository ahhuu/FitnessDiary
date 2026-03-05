package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "weight_record")
public class WeightRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "weight")
    private float weight;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "note")
    private String note;

    public WeightRecord(float weight, long timestamp, String note) {
        this.weight = weight;
        this.timestamp = timestamp;
        this.note = note;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
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
}
