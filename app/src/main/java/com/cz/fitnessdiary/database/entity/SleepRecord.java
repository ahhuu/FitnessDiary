package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 睡眠记录实体类
 */
@Entity(tableName = "sleep_record")
public class SleepRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "start_time")
    private long startTime; // 开始睡眠时间戳

    @ColumnInfo(name = "end_time")
    private long endTime; // 结束睡眠时间戳

    @ColumnInfo(name = "duration")
    private long duration; // 总时长（秒）

    @ColumnInfo(name = "quality")
    private int quality; // 睡眠质量 (1-5)

    @ColumnInfo(name = "notes")
    private String notes; // 备注

    public SleepRecord(long startTime, long endTime, int quality, String notes) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = (endTime - startTime) / 1000; // 计算秒数
        this.quality = quality;
        this.notes = notes;
    }

    // Getter 和 Setter
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
