package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "challenge_record",
    foreignKeys = @ForeignKey(
        entity = ChallengeEntity.class,
        parentColumns = "id",
        childColumns = "challenge_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("challenge_id")}
)
public class ChallengeRecordEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "challenge_id")
    public int challengeId;

    // The start timestamp of the specific day being recorded (e.g., 00:00:00 of that day)
    @ColumnInfo(name = "record_date")
    public long recordDate;

    // 0 = Failed / Pending, 1 = Completed
    @ColumnInfo(name = "is_completed")
    public int isCompleted;

    // 0 = Normal, 1 = Freeze Ticket Used (Exempt from failure)
    @ColumnInfo(name = "is_frozen")
    public int isFrozen;
}
