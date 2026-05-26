package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.BowelMovement;

import java.util.List;

@Dao
public interface BowelMovementDao {

    @Insert
    void insert(BowelMovement record);

    @Update
    void update(BowelMovement record);

    @Delete
    void delete(BowelMovement record);

    @Query("SELECT * FROM bowel_movement ORDER BY timestamp DESC")
    LiveData<List<BowelMovement>> getAllRecords();

    @Query("SELECT * FROM bowel_movement WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    LiveData<List<BowelMovement>> getByDateRange(long startTs, long endTs);

    @Query("SELECT * FROM bowel_movement WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp ASC")
    List<BowelMovement> getByDateRangeSync(long startTs, long endTs);

    @Query("SELECT * FROM bowel_movement ORDER BY timestamp DESC LIMIT 1")
    BowelMovement getLatestSync();

    @Query("SELECT * FROM bowel_movement WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC LIMIT 1")
    BowelMovement getLatestByDateSync(long startTs, long endTs);

    @Query("SELECT bristol_type AS bristolType, COUNT(*) AS count FROM bowel_movement WHERE timestamp >= :startTs AND timestamp < :endTs GROUP BY bristol_type ORDER BY bristol_type")
    List<BristolCount> getBristolDistributionSync(long startTs, long endTs);

    @Query("SELECT color, COUNT(*) AS count FROM bowel_movement WHERE color IS NOT NULL AND timestamp >= :startTs AND timestamp < :endTs GROUP BY color ORDER BY count DESC")
    List<ColorCount> getColorDistributionSync(long startTs, long endTs);

    class BristolCount {
        public int bristolType;
        public int count;
    }

    class ColorCount {
        public String color;
        public int count;
    }
}
