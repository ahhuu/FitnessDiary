package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 用户实体类
 * 存储用户的基本信息，包括昵称、身高、体重和注册状态
 */
@Entity(tableName = "user")
public class User {

    @PrimaryKey(autoGenerate = true)
    private int uid;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "height")
    private float height; // 身高，单位：cm

    @ColumnInfo(name = "weight")
    private float weight; // 体重，单位：kg

    @ColumnInfo(name = "is_registered")
    private boolean isRegistered; // 是否已注册

    // === 2.0 新增字段 ===
    @ColumnInfo(name = "gender")
    private int gender; // 性别：0=女, 1=男

    @ColumnInfo(name = "goal_type")
    private int goalType; // 目标类型：0=减脂, 1=增肌, 2=保持

    @ColumnInfo(name = "activity_level")
    private float activityLevel; // 活动系数：1.2-1.9

    @ColumnInfo(name = "daily_calorie_target")
    private int dailyCalorieTarget; // 每日卡路里目标（自动计算）

    @ColumnInfo(name = "age")
    private int age; // 年龄（用于BMR计算）

    @ColumnInfo(name = "nickname", defaultValue = "健身达人")
    private String nickname; // 昵称

    @ColumnInfo(name = "goal", defaultValue = "减脂")
    private String goal; // 目标（减脂/增肌/保持）

    @ColumnInfo(name = "avatar_uri")
    private String avatarUri; // 头像 URI（用于图库选择的图片）

    // 构造函数（Room 使用此构造函数）
    public User(String name, float height, float weight, boolean isRegistered) {
        this.name = name;
        this.height = height;
        this.weight = weight;
        this.isRegistered = isRegistered;
        // 新字段使用默认值
        this.gender = 0;
        this.goalType = 0;
        this.activityLevel = 1.2f;
        this.dailyCalorieTarget = 0;
        this.age = 25; // 默认年龄
    }

    // 完整构造函数（用于 2.0，标记为 @Ignore）
    @Ignore
    public User(String name, float height, float weight, boolean isRegistered,
            int gender, int goalType, float activityLevel, int dailyCalorieTarget, int age) {
        this.name = name;
        this.height = height;
        this.weight = weight;
        this.isRegistered = isRegistered;
        this.gender = gender;
        this.goalType = goalType;
        this.activityLevel = activityLevel;
        this.dailyCalorieTarget = dailyCalorieTarget;
        this.age = age;
    }

    // Getter 和 Setter 方法
    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public void setRegistered(boolean registered) {
        isRegistered = registered;
    }

    // === 2.0 新增字段的 Getter 和 Setter ===
    public int getGender() {
        return gender;
    }

    public void setGender(int gender) {
        this.gender = gender;
    }

    public int getGoalType() {
        return goalType;
    }

    public void setGoalType(int goalType) {
        this.goalType = goalType;
    }

    public float getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(float activityLevel) {
        this.activityLevel = activityLevel;
    }

    public int getDailyCalorieTarget() {
        return dailyCalorieTarget;
    }

    public void setDailyCalorieTarget(int dailyCalorieTarget) {
        this.dailyCalorieTarget = dailyCalorieTarget;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    // === 便捷方法别名 ===
    /**
     * targetCalories 的别名，实际指向 dailyCalorieTarget
     */
    public int getTargetCalories() {
        return dailyCalorieTarget;
    }

    public void setTargetCalories(int targetCalories) {
        this.dailyCalorieTarget = targetCalories;
    }

    public String getAvatarUri() {
        return avatarUri;
    }

    public void setAvatarUri(String avatarUri) {
        this.avatarUri = avatarUri;
    }

    // === 2.1 新增字段 (Plan 8) ===
    @ColumnInfo(name = "target_protein")
    private int targetProtein; // 每日目标蛋白质 (g)

    @ColumnInfo(name = "target_carbs")
    private int targetCarbs; // 每日目标碳水 (g)

    public int getTargetProtein() {
        return targetProtein;
    }

    public void setTargetProtein(int targetProtein) {
        this.targetProtein = targetProtein;
    }

    public int getTargetCarbs() {
        return targetCarbs;
    }

    public void setTargetCarbs(int targetCarbs) {
        this.targetCarbs = targetCarbs;
    }
}
