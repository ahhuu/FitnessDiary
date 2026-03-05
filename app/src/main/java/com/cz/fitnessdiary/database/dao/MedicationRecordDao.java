package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.MedicationRecord;

import java.util.List;

@Dao
public interface MedicationRecordDao {

    @Insert
    void insert(MedicationRecord record);

    @Update
    void update(MedicationRecord record);

    @Delete
    void delete(MedicationRecord record);

    @Query("SELECT * FROM medication_record WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    LiveData<List<MedicationRecord>> getRecordsByDateRange(long startTs, long endTs);

    @Query("SELECT COUNT(*) FROM medication_record WHERE timestamp >= :startTs AND timestamp < :endTs AND is_taken = 1")
    LiveData<Integer> getTakenCountByDateRange(long startTs, long endTs);

    @Query("SELECT COUNT(*) FROM medication_record WHERE timestamp >= :startTs AND timestamp < :endTs AND is_taken = 0")
    LiveData<Integer> getUntakenCountByDateRange(long startTs, long endTs);

    @Query("SELECT * FROM medication_record ORDER BY timestamp DESC LIMIT 1")
    LiveData<MedicationRecord> getLatestRecord();

    @Query("SELECT * FROM medication_record ORDER BY timestamp DESC LIMIT :limit")
    List<MedicationRecord> getRecentRecordsSync(int limit);

    @Query("SELECT * FROM medication_record WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    List<MedicationRecord> getRecordsByDateRangeSync(long startTs, long endTs);
}
