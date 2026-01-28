package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 训练计划实体类
 * 存储用户创建的训练计划信息
 */
@Entity(tableName = "training_plan")
public class TrainingPlan implements java.io.Serializable {

    @PrimaryKey(autoGenerate = true)
    private int planId;

    @ColumnInfo(name = "name")
    private String name; // 计划名称

    @ColumnInfo(name = "description")
    private String description; // 计划描述

    @ColumnInfo(name = "create_time")
    private long createTime; // 创建时间戳

    // === 2.0 新增字段 ===
    @ColumnInfo(name = "sets")
    private int sets; // 组数

    @ColumnInfo(name = "reps")
    private int reps; // 每组次数/时长

    @ColumnInfo(name = "media_uri")
    private String mediaUri; // 图片/视频本地路径

    // 构造函数（Room 使用此构造函数）
    public TrainingPlan(String name, String description, long createTime) {
        this.name = name;
        this.description = description;
        this.createTime = createTime;
        // 新字段使用默认值
        this.sets = 0;
        this.reps = 0;
        this.mediaUri = null;
    }

    // 完整构造函数（用于 2.0，标记为 @Ignore）
    @Ignore
    public TrainingPlan(String name, String description, long createTime,
            int sets, int reps, String mediaUri) {
        this.name = name;
        this.description = description;
        this.createTime = createTime;
        this.sets = sets;
        this.reps = reps;
        this.mediaUri = mediaUri;
    }

    // Getter 和 Setter 方法
    public int getPlanId() {
        return planId;
    }

    public void setPlanId(int planId) {
        this.planId = planId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    // === 2.0 新增字段的 Getter 和 Setter ===
    public int getSets() {
        return sets;
    }

    public void setSets(int sets) {
        this.sets = sets;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public String getMediaUri() {
        return mediaUri;
    }

    public void setMediaUri(String mediaUri) {
        this.mediaUri = mediaUri;
    }

    // === 2.1 新增字段 (Plan 8) ===
    @ColumnInfo(name = "category")
    private String category; // 训练部位/分类 (如 "胸部", "有氧")

    @ColumnInfo(name = "scheduled_days")
    private String scheduledDays; // 计划执行日 ("1,3,5" 或 "0")

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getScheduledDays() {
        return scheduledDays;
    }

    public void setScheduledDays(String scheduledDays) {
        this.scheduledDays = scheduledDays;
    }
}
