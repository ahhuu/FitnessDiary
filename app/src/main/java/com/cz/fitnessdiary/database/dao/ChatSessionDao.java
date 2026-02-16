package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.ChatSessionEntity;

import java.util.List;

@Dao
public interface ChatSessionDao {

    @Insert
    long insert(ChatSessionEntity session);

    @Update
    void update(ChatSessionEntity session);

    @Delete
    void delete(ChatSessionEntity session);

    @Query("SELECT * FROM chat_sessions ORDER BY last_updated DESC")
    LiveData<List<ChatSessionEntity>> getAllSessionsLive();

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    ChatSessionEntity getSessionById(long sessionId);

    @Query("DELETE FROM chat_sessions")
    void deleteAll();
}
