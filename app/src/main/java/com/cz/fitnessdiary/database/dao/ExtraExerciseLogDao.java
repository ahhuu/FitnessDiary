package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.ExtraExerciseLog;

import java.util.List;

@Dao
public interface ExtraExerciseLogDao {

    @Insert
    long insert(ExtraExerciseLog log);

    @Update
    void update(ExtraExerciseLog log);

    @Delete
    void delete(ExtraExerciseLog log);

    @Query("SELECT * FROM extra_exercise_log WHERE date >= :startDate AND date < :endDate ORDER BY created_at ASC, id ASC")
    LiveData<List<ExtraExerciseLog>> getLogsByDateRange(long startDate, long endDate);

    @Query("SELECT * FROM extra_exercise_log ORDER BY date DESC, created_at DESC, id DESC")
    LiveData<List<ExtraExerciseLog>> getAllLogs();

    @Query("SELECT * FROM extra_exercise_log WHERE date >= :startDate AND date < :endDate ORDER BY created_at ASC, id ASC")
    List<ExtraExerciseLog> getLogsByDateRangeSync(long startDate, long endDate);

    @Query("SELECT * FROM extra_exercise_log ORDER BY date DESC, created_at DESC, id DESC")
    List<ExtraExerciseLog> getAllLogsSync();

    @Query("SELECT DISTINCT date FROM extra_exercise_log")
    List<Long> getAllRecordDatesSync();
}
