package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "step_record", indices = {@Index(value = "date", unique = true)})
public class StepRecord {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "date")
    private long date; // 当天0点时间戳

    @ColumnInfo(name = "steps")
    private int steps;

    @ColumnInfo(name = "source")
    private int source; // 0=sensor, 1=manual, 2=hybrid

    @ColumnInfo(name = "create_time")
    private long createTime;

    public StepRecord(long date, int steps, int source, long createTime) {
        this.date = date;
        this.steps = steps;
        this.source = source;
        this.createTime = createTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }
    public int getSource() { return source; }
    public void setSource(int source) { this.source = source; }
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
