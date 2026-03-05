package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.CustomRecord;

import java.util.List;

@Dao
public interface CustomRecordDao {

    @Insert
    void insert(CustomRecord record);

    @Update
    void update(CustomRecord record);

    @Delete
    void delete(CustomRecord record);

    @Query("SELECT * FROM custom_record WHERE tracker_id = :trackerId AND timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    LiveData<List<CustomRecord>> getRecordsByTrackerAndDateRange(long trackerId, long startTs, long endTs);

    @Query("SELECT COALESCE(SUM(numeric_value), 0) FROM custom_record WHERE tracker_id = :trackerId AND timestamp >= :startTs AND timestamp < :endTs")
    LiveData<Double> getSumValueByTrackerAndDateRange(long trackerId, long startTs, long endTs);

    @Query("SELECT * FROM custom_record WHERE tracker_id = :trackerId ORDER BY timestamp DESC LIMIT 1")
    LiveData<CustomRecord> getLatestRecordByTracker(long trackerId);

    @Query("SELECT COUNT(*) FROM custom_record WHERE timestamp >= :startTs AND timestamp < :endTs")
    LiveData<Integer> getRecordCountByDateRange(long startTs, long endTs);

    @Query("SELECT COUNT(*) FROM custom_record WHERE tracker_id = :trackerId AND timestamp >= :startTs AND timestamp < :endTs")
    LiveData<Integer> getRecordCountByTrackerAndDateRange(long trackerId, long startTs, long endTs);

    @Query("SELECT * FROM custom_record WHERE tracker_id = :trackerId ORDER BY timestamp DESC LIMIT :limit")
    List<CustomRecord> getRecentRecordsByTrackerSync(long trackerId, int limit);

    @Query("SELECT * FROM custom_record WHERE tracker_id = :trackerId AND timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp ASC")
    List<CustomRecord> getRecordsByTrackerAndDateRangeSync(long trackerId, long startTs, long endTs);

    @Query("SELECT MAX(timestamp) FROM custom_record WHERE tracker_id = :trackerId")
    Long getLatestTimestampByTrackerSync(long trackerId);
}
