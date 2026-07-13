package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 每日打卡记录实体类
 * 记录每天的训练计划完成情况
 */
@Entity(tableName = "daily_log", foreignKeys = @ForeignKey(entity = TrainingPlan.class, parentColumns = "planId", childColumns = "plan_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index("plan_id") })
public class DailyLog {

    @PrimaryKey(autoGenerate = true)
    private int logId;

    @ColumnInfo(name = "plan_id")
    private int planId; // 关联的训练计划ID

    @ColumnInfo(name = "date")
    private long date; // 日期（当天0点的时间戳）

    @ColumnInfo(name = "is_completed")
    private boolean isCompleted; // 是否完成

    @ColumnInfo(name = "actual_sets", defaultValue = "0")
    private int actualSets;

    @ColumnInfo(name = "actual_reps", defaultValue = "0")
    private int actualReps;

    @ColumnInfo(name = "actual_weight", defaultValue = "0")
    private float actualWeight;

    @ColumnInfo(name = "duration", defaultValue = "0")
    private int duration; // 实际完成时长 (单位：秒)

    // 构造函数
    public DailyLog(int planId, long date, boolean isCompleted) {
        this.planId = planId;
        this.date = date;
        this.isCompleted = isCompleted;
        this.duration = 0;
        this.actualSets = 0;
        this.actualReps = 0;
        this.actualWeight = 0;
    }

    // Getter 和 Setter 方法
    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public int getPlanId() {
        return planId;
    }

    public void setPlanId(int planId) {
        this.planId = planId;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getActualSets() {
        return actualSets;
    }

    public void setActualSets(int actualSets) {
        this.actualSets = actualSets;
    }

    public int getActualReps() {
        return actualReps;
    }

    public void setActualReps(int actualReps) {
        this.actualReps = actualReps;
    }

    public float getActualWeight() {
        return actualWeight;
    }

    public void setActualWeight(float actualWeight) {
        this.actualWeight = actualWeight;
    }
}
