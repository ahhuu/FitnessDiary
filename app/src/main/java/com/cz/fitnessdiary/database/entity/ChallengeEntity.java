package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "challenge_instance")
public class ChallengeEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // UUID or predefined ID (e.g., "FAT_LOSS", "CUSTOM_xxx")
    @ColumnInfo(name = "template_id")
    public String templateId;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "description")
    public String desc;

    @ColumnInfo(name = "emoji")
    public String emoji;

    @ColumnInfo(name = "category")
    public int category;

    @ColumnInfo(name = "max_fails")
    public int maxFails;

    @ColumnInfo(name = "bind_card")
    public String bindCard;

    @ColumnInfo(name = "start_time")
    public long startTime;

    // "ACTIVE", "COMPLETED", "FAILED", "TEMPLATE" (for custom saved challenges)
    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "fails_count")
    public int failsCount;

    @ColumnInfo(name = "last_check_date")
    public long lastCheckDate;

    // Remaining freeze tickets (请假条剩余数量)
    @ColumnInfo(name = "freeze_tickets")
    public int freezeTickets;

    @ColumnInfo(name = "total_days", defaultValue = "21")
    public int totalDays = 21;

    @ColumnInfo(name = "target_days", defaultValue = "21")
    public int targetDays = 21;

    @ColumnInfo(name = "reminder_hour", defaultValue = "-1")
    public int reminderHour = -1;

    @ColumnInfo(name = "reminder_minute", defaultValue = "-1")
    public int reminderMinute = -1;
}
