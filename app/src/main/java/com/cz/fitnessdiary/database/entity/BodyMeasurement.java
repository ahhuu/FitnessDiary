package com.cz.fitnessdiary.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "body_measurement",
        indices = {@Index(name = "index_body_measurement_type_time",
                value = {"measurement_type", "timestamp"}, orders = {Index.Order.ASC, Index.Order.DESC})})
public class BodyMeasurement {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "measurement_type")
    private String measurementType; // BODY_FAT, CHEST, WAIST, HIP, ARM, THIGH, CALF

    private float value;

    @NonNull
    private String unit; // "%" or "cm"

    private long timestamp;

    private String note;

    public BodyMeasurement(@NonNull String measurementType, float value, @NonNull String unit, long timestamp, String note) {
        this.measurementType = measurementType;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
        this.note = note;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    @NonNull
    public String getMeasurementType() { return measurementType; }
    public void setMeasurementType(@NonNull String measurementType) { this.measurementType = measurementType; }
    public float getValue() { return value; }
    public void setValue(float value) { this.value = value; }
    @NonNull
    public String getUnit() { return unit; }
    public void setUnit(@NonNull String unit) { this.unit = unit; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
