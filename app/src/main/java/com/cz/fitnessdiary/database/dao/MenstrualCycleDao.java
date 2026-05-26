package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.MenstrualCycle;

import java.util.List;

@Dao
public interface MenstrualCycleDao {

    @Insert
    void insert(MenstrualCycle record);

    @Update
    void update(MenstrualCycle record);

    @Delete
    void delete(MenstrualCycle record);

    @Query("SELECT * FROM menstrual_cycle ORDER BY start_date DESC")
    LiveData<List<MenstrualCycle>> getAllRecords();

    @Query("SELECT * FROM menstrual_cycle ORDER BY start_date DESC")
    List<MenstrualCycle> getAllRecordsSync();

    @Query("SELECT * FROM menstrual_cycle WHERE start_date >= :startTs AND start_date < :endTs ORDER BY start_date DESC")
    LiveData<List<MenstrualCycle>> getByDateRange(long startTs, long endTs);

    @Query("SELECT * FROM menstrual_cycle WHERE end_date IS NULL ORDER BY start_date DESC LIMIT 1")
    MenstrualCycle getLatestOngoingSync();

    @Query("SELECT * FROM menstrual_cycle ORDER BY start_date DESC LIMIT 1")
    MenstrualCycle getLatestSync();

    @Query("SELECT * FROM menstrual_cycle WHERE start_date <= :date AND (end_date IS NULL OR end_date >= :date) ORDER BY start_date DESC LIMIT 1")
    MenstrualCycle getCycleForDateSync(long date);
}
