package com.cz.fitnessdiary.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.ChallengeRecordEntity;

import java.util.List;

@Dao
public interface ChallengeRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ChallengeRecordEntity record);

    @Update
    void update(ChallengeRecordEntity record);

    @Query("SELECT * FROM challenge_record WHERE challenge_id = :challengeId ORDER BY record_date ASC")
    List<ChallengeRecordEntity> getRecordsByChallengeIdSync(int challengeId);

    @Query("SELECT * FROM challenge_record WHERE challenge_id = :challengeId AND record_date = :recordDate LIMIT 1")
    ChallengeRecordEntity getRecordSync(int challengeId, long recordDate);

    @Query("DELETE FROM challenge_record WHERE challenge_id = :challengeId")
    void deleteByChallengeId(int challengeId);
}
