package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.BodyMeasurement;

import java.util.List;

@Dao
public interface BodyMeasurementDao {

    @Insert
    void insert(BodyMeasurement record);

    @Update
    void update(BodyMeasurement record);

    @Delete
    void delete(BodyMeasurement record);

    @Query("SELECT * FROM body_measurement WHERE measurement_type = :type ORDER BY timestamp DESC LIMIT 1")
    LiveData<BodyMeasurement> getLatestByType(String type);

    @Query("SELECT * FROM body_measurement WHERE measurement_type = :type ORDER BY timestamp DESC LIMIT 1")
    BodyMeasurement getLatestByTypeSync(String type);

    @Query("SELECT * FROM body_measurement WHERE measurement_type = :type AND timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp ASC")
    List<BodyMeasurement> getByTypeAndDateRangeSync(String type, long startTs, long endTs);

    @Query("SELECT * FROM body_measurement WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    LiveData<List<BodyMeasurement>> getByDateRange(long startTs, long endTs);

    @Query("SELECT * FROM body_measurement WHERE measurement_type = :type ORDER BY timestamp DESC")
    LiveData<List<BodyMeasurement>> getAllByType(String type);

    @Query("SELECT * FROM body_measurement ORDER BY timestamp DESC")
    LiveData<List<BodyMeasurement>> getAllRecords();

    @Query("SELECT * FROM body_measurement WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp ASC")
    List<BodyMeasurement> getByDateRangeSync(long startTs, long endTs);

    @Query("SELECT * FROM body_measurement ORDER BY timestamp DESC LIMIT 1")
    BodyMeasurement getLatestSync(); // 增加获取全表最近一条围度记录的方法

    @Query("SELECT DISTINCT measurement_type FROM body_measurement")
    LiveData<List<String>> getDistinctTypes();
}
