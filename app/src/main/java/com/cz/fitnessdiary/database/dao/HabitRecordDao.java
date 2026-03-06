package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.HabitRecord;

import java.util.List;

@Dao
public interface HabitRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(HabitRecord record);

    @Query("SELECT * FROM habit_record WHERE record_date = :recordDate ORDER BY habit_id ASC")
    LiveData<List<HabitRecord>> getRecordsByDate(long recordDate);

    @Query("SELECT * FROM habit_record WHERE record_date = :recordDate ORDER BY habit_id ASC")
    List<HabitRecord> getRecordsByDateSync(long recordDate);

    @Query("SELECT * FROM habit_record WHERE habit_id = :habitId ORDER BY record_date DESC LIMIT :limit")
    List<HabitRecord> getRecentByHabitSync(long habitId, int limit);

    @Query("SELECT COUNT(*) FROM habit_record WHERE habit_id = :habitId AND is_completed = 1")
    int getCompletedCountSync(long habitId);

    @Query("SELECT COUNT(*) FROM habit_record WHERE record_date = :recordDate AND is_completed = 1")
    LiveData<Integer> getCompletedCountByDate(long recordDate);

    @Query("SELECT COUNT(*) FROM habit_record WHERE record_date = :recordDate AND is_completed = 1")
    int getCompletedCountByDateSync(long recordDate);

    @Query("SELECT COUNT(*) FROM habit_record WHERE habit_id = :habitId AND record_date >= :startTs AND record_date < :endTs AND is_completed = 1")
    int getCompletedCountByHabitAndDateRangeSync(long habitId, long startTs, long endTs);

    @Query("SELECT COUNT(*) FROM habit_record WHERE habit_id = :habitId AND record_date >= :startTs AND record_date < :endTs")
    int getRecordCountByHabitAndDateRangeSync(long habitId, long startTs, long endTs);
}
