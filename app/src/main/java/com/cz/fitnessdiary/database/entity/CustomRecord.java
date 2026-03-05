package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "custom_record", foreignKeys = @ForeignKey(entity = CustomTracker.class, parentColumns = "id", childColumns = "tracker_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index("tracker_id") })
public class CustomRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "tracker_id")
    private long trackerId;

    @ColumnInfo(name = "numeric_value")
    private Double numericValue;

    @ColumnInfo(name = "text_value")
    private String textValue;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    public CustomRecord(long trackerId, Double numericValue, String textValue, long timestamp) {
        this.trackerId = trackerId;
        this.numericValue = numericValue;
        this.textValue = textValue;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTrackerId() {
        return trackerId;
    }

    public void setTrackerId(long trackerId) {
        this.trackerId = trackerId;
    }

    public Double getNumericValue() {
        return numericValue;
    }

    public void setNumericValue(Double numericValue) {
        this.numericValue = numericValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
