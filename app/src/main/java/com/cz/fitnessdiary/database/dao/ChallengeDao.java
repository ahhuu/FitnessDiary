package com.cz.fitnessdiary.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.ChallengeEntity;

import java.util.List;

@Dao
public interface ChallengeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ChallengeEntity challenge);

    @Update
    void update(ChallengeEntity challenge);

    @Query("SELECT * FROM challenge_instance WHERE status = 'ACTIVE' LIMIT 1")
    ChallengeEntity getActiveChallengeSync();

    @Query("SELECT * FROM challenge_instance WHERE status = 'ACTIVE'")
    List<ChallengeEntity> getActiveChallengesSync();

    @Query("SELECT * FROM challenge_instance WHERE status = 'TEMPLATE'")
    List<ChallengeEntity> getCustomTemplatesSync();

    @Query("SELECT * FROM challenge_instance WHERE template_id = :templateId AND status = 'TEMPLATE' LIMIT 1")
    ChallengeEntity getTemplateByTemplateIdSync(String templateId);

    @Query("SELECT * FROM challenge_instance WHERE status = 'COMPLETED' ORDER BY start_time DESC")
    List<ChallengeEntity> getCompletedChallengesSync();

    @Query("SELECT * FROM challenge_instance WHERE id = :id LIMIT 1")
    ChallengeEntity getByIdSync(int id);

    @Query("DELETE FROM challenge_instance WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM challenge_instance WHERE template_id = :templateId AND status = 'TEMPLATE'")
    void deleteTemplateByTemplateId(String templateId);
}
