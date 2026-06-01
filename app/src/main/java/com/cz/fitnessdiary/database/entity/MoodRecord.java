package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "mood_record", indices = {@Index(value = "date", unique = true)})
public class MoodRecord {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "date")
    private long date; // 当天0点时间戳

    @ColumnInfo(name = "mood_code")
    private String moodCode; // HAPPY, NEUTRAL, SAD, IRRITABLE, ANXIOUS

    @ColumnInfo(name = "note")
    private String note;

    @ColumnInfo(name = "create_time")
    private long createTime;

    public MoodRecord(long date, String moodCode, String note, long createTime) {
        this.date = date;
        this.moodCode = moodCode;
        this.note = note;
        this.createTime = createTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public String getMoodCode() { return moodCode; }
    public void setMoodCode(String moodCode) { this.moodCode = moodCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
