package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.WaterRecord;

import java.util.List;

@Dao
public interface WaterRecordDao {

    @Insert
    void insert(WaterRecord record);

    @Update
    void update(WaterRecord record);

    @Delete
    void delete(WaterRecord record);

    @Query("SELECT * FROM water_record WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    LiveData<List<WaterRecord>> getRecordsByDateRange(long startTs, long endTs);

    @Query("SELECT COALESCE(SUM(amount_ml), 0) FROM water_record WHERE timestamp >= :startTs AND timestamp < :endTs")
    LiveData<Integer> getTotalAmountByDateRange(long startTs, long endTs);

    @Query("SELECT * FROM water_record ORDER BY timestamp DESC LIMIT 1")
    LiveData<WaterRecord> getLatestRecord();

    @Query("SELECT * FROM water_record ORDER BY timestamp DESC LIMIT :limit")
    List<WaterRecord> getRecentRecordsSync(int limit);

    @Query("SELECT * FROM water_record WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    List<WaterRecord> getRecordsByDateRangeSync(long startTs, long endTs);
}
