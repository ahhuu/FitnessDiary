package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.SleepRecord;

import java.util.List;

@Dao
public interface SleepRecordDao {

    @Insert
    void insert(SleepRecord sleepRecord);

    @Update
    void update(SleepRecord sleepRecord);

    @Delete
    void delete(SleepRecord sleepRecord);

    @Query("SELECT * FROM sleep_record WHERE start_time >= :startDate AND start_time < :endDate ORDER BY start_time DESC")
    LiveData<List<SleepRecord>> getSleepRecordsByDateRange(long startDate, long endDate);

    @Query("SELECT * FROM sleep_record ORDER BY start_time DESC")
    LiveData<List<SleepRecord>> getAllSleepRecords();

    @Query("SELECT SUM(duration) FROM sleep_record WHERE start_time >= :startDate AND start_time < :endDate")
    LiveData<Long> getTotalSleepDurationByDateRange(long startDate, long endDate);

    @Query("SELECT * FROM sleep_record WHERE start_time >= :startDate AND start_time < :endDate ORDER BY start_time DESC")
    List<SleepRecord> getSleepRecordsByDateRangeSync(long startDate, long endDate);
}
