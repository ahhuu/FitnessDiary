package com.cz.fitnessdiary.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.ChatMessageDao;
import com.cz.fitnessdiary.database.entity.ChatMessageEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatRepository {
    public com.cz.fitnessdiary.database.dao.ChatSessionDao sessionDao;
    private ChatMessageDao chatMessageDao;
    private ExecutorService executorService;

    public ChatRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        chatMessageDao = db.chatMessageDao();
        sessionDao = db.chatSessionDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    // --- Session Methods ---

    public LiveData<List<com.cz.fitnessdiary.database.entity.ChatSessionEntity>> getAllSessionsLive() {
        return sessionDao.getAllSessionsLive();
    }

    public void insertSession(com.cz.fitnessdiary.database.entity.ChatSessionEntity session,
            OnSessionInsertedListener listener) {
        executorService.execute(() -> {
            long id = sessionDao.insert(session);
            if (listener != null)
                listener.onInserted(id);
        });
    }

    public void updateSession(com.cz.fitnessdiary.database.entity.ChatSessionEntity session) {
        executorService.execute(() -> sessionDao.update(session));
    }

    public void deleteSession(com.cz.fitnessdiary.database.entity.ChatSessionEntity session) {
        executorService.execute(() -> {
            chatMessageDao.deleteBySessionId(session.getId());
            sessionDao.delete(session);
        });
    }

    public void renameSession(long sessionId, String newTitle) {
        executorService.execute(() -> {
            com.cz.fitnessdiary.database.entity.ChatSessionEntity session = sessionDao.getSessionById(sessionId);
            if (session != null) {
                session.setTitle(newTitle);
                sessionDao.update(session);
            }
        });
    }

    public void updateSessionFolder(long sessionId, String folderName) {
        executorService.execute(() -> {
            com.cz.fitnessdiary.database.entity.ChatSessionEntity session = sessionDao.getSessionById(sessionId);
            if (session != null) {
                session.setFolderName(folderName);
                sessionDao.update(session);
            }
        });
    }

    public void deleteAllSessions() {
        executorService.execute(() -> {
            sessionDao.deleteAll();
            chatMessageDao.deleteAll();
        });
    }

    public interface OnSessionInsertedListener {
        void onInserted(long sessionId);
    }

    // --- Message Methods ---

    public LiveData<List<ChatMessageEntity>> getMessagesBySessionLive(long sessionId) {
        return chatMessageDao.getMessagesBySessionLive(sessionId);
    }

    public void insert(ChatMessageEntity message) {
        executorService.execute(() -> chatMessageDao.insert(message));
    }

    public void update(ChatMessageEntity message) {
        executorService.execute(() -> chatMessageDao.update(message));
    }

    public void delete(ChatMessageEntity message) {
        executorService.execute(() -> chatMessageDao.delete(message));
    }

    public void deleteMessages(List<Long> ids) {
        executorService.execute(() -> chatMessageDao.deleteMessages(ids));
    }

    public void deleteAll() {
        executorService.execute(() -> chatMessageDao.deleteAll());
    }
}
