package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.util.List;

@Dao
public interface WeightRecordDao {

    @Insert
    void insert(WeightRecord record);

    @Update
    void update(WeightRecord record);

    @Delete
    void delete(WeightRecord record);

    @Query("SELECT * FROM weight_record ORDER BY timestamp DESC LIMIT 1")
    LiveData<WeightRecord> getLatestRecord();

    @Query("SELECT * FROM weight_record ORDER BY timestamp DESC LIMIT 1")
    WeightRecord getLatestRecordSync();

    @Query("SELECT * FROM weight_record WHERE timestamp < :timestamp ORDER BY timestamp DESC LIMIT 1")
    WeightRecord getLatestRecordBeforeSync(long timestamp);

    @Query("SELECT * FROM weight_record WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    LiveData<List<WeightRecord>> getRecordsByDateRange(long startTs, long endTs);

    @Query("SELECT * FROM weight_record ORDER BY timestamp DESC")
    LiveData<List<WeightRecord>> getAllRecords();

    @Query("SELECT * FROM weight_record ORDER BY timestamp DESC LIMIT :limit")
    List<WeightRecord> getRecentRecordsSync(int limit);

    @Query("SELECT * FROM weight_record WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp ASC")
    List<WeightRecord> getRecordsByDateRangeSync(long startTs, long endTs);
}
