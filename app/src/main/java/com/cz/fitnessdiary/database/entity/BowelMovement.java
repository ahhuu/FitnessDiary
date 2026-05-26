package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "bowel_movement",
        indices = {@Index(name = "index_bowel_movement_time", value = {"timestamp"}, orders = {Index.Order.DESC})})
public class BowelMovement {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "bristol_type")
    private int bristolType; // 1-7 Bristol Stool Scale

    private String color;    // BROWN, GREEN, YELLOW, RED, BLACK, WHITE, GREY

    private String volume;   // SMALL, MEDIUM, LARGE

    private String smell;    // NORMAL, STRONG, SOUR, PUNGENT

    @ColumnInfo(name = "process_feeling")
    private String processFeeling; // NORMAL, DIFFICULT, INCOMPLETE, URGENT

    @ColumnInfo(name = "duration_seconds")
    private int durationSeconds;

    private long timestamp;

    private String note;

    public BowelMovement(int bristolType, String color, String volume, String smell,
                         String processFeeling, int durationSeconds, long timestamp, String note) {
        this.bristolType = bristolType;
        this.color = color;
        this.volume = volume;
        this.smell = smell;
        this.processFeeling = processFeeling;
        this.durationSeconds = durationSeconds;
        this.timestamp = timestamp;
        this.note = note;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getBristolType() { return bristolType; }
    public void setBristolType(int bristolType) { this.bristolType = bristolType; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getVolume() { return volume; }
    public void setVolume(String volume) { this.volume = volume; }
    public String getSmell() { return smell; }
    public void setSmell(String smell) { this.smell = smell; }
    public String getProcessFeeling() { return processFeeling; }
    public void setProcessFeeling(String processFeeling) { this.processFeeling = processFeeling; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
