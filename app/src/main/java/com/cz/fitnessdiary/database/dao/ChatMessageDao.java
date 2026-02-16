package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.ChatMessageEntity;

import java.util.List;

@Dao
public interface ChatMessageDao {

    @Insert
    long insert(ChatMessageEntity message);

    @Update
    void update(ChatMessageEntity message);

    @Delete
    void delete(ChatMessageEntity message);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    LiveData<List<ChatMessageEntity>> getMessagesBySessionLive(long sessionId);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    List<ChatMessageEntity> getMessagesBySessionSync(long sessionId);

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    LiveData<List<ChatMessageEntity>> getAllMessagesLive();

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    List<ChatMessageEntity> getAllMessagesSync();

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    void deleteBySessionId(long sessionId);

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    void deleteMessages(List<Long> ids);

    @Query("DELETE FROM chat_messages")
    void deleteAll();
}
