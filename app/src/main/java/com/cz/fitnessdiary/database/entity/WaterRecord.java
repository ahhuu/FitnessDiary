package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "water_record")
public class WaterRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "amount_ml")
    private int amountMl;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "note")
    private String note;

    public WaterRecord(int amountMl, long timestamp, String note) {
        this.amountMl = amountMl;
        this.timestamp = timestamp;
        this.note = note;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getAmountMl() {
        return amountMl;
    }

    public void setAmountMl(int amountMl) {
        this.amountMl = amountMl;
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
