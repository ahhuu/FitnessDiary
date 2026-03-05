package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "habit_record", foreignKeys = @ForeignKey(entity = HabitItem.class, parentColumns = "id", childColumns = "habit_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = { "habit_id", "record_date" }, unique = true), @Index("habit_id") })
public class HabitRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "habit_id")
    private long habitId;

    @ColumnInfo(name = "record_date")
    private long recordDate;

    @ColumnInfo(name = "is_completed")
    private boolean completed;

    @ColumnInfo(name = "source")
    private String source;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    public HabitRecord(long habitId, long recordDate, boolean completed, String source, long timestamp) {
        this.habitId = habitId;
        this.recordDate = recordDate;
        this.completed = completed;
        this.source = source;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getHabitId() { return habitId; }
    public void setHabitId(long habitId) { this.habitId = habitId; }
    public long getRecordDate() { return recordDate; }
    public void setRecordDate(long recordDate) { this.recordDate = recordDate; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}