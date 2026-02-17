package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 聊天消息实体类 (持久化存储)
 */
@Entity(tableName = "chat_messages")
public class ChatMessageEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "content")
    private String content;

    @ColumnInfo(name = "reasoning")
    private String reasoning;

    @ColumnInfo(name = "is_user")
    private boolean isUser;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "session_id")
    private long sessionId; // 关联的对话序号

    @ColumnInfo(name = "media_path")
    private String mediaPath; // 多媒体路径 (图片等)

    @androidx.room.Ignore
    public ChatMessageEntity(String content, boolean isUser, long timestamp) {
        this(content, null, isUser, timestamp, 1);
    }

    @androidx.room.Ignore
    public ChatMessageEntity(String content, boolean isUser, long timestamp, long sessionId) {
        this(content, null, isUser, timestamp, sessionId);
    }

    @androidx.room.Ignore
    public ChatMessageEntity(String content, String reasoning, boolean isUser, long timestamp) {
        this(content, reasoning, isUser, timestamp, 1);
    }

    @androidx.room.Ignore
    public ChatMessageEntity(String content, String reasoning, boolean isUser, long timestamp, long sessionId) {
        this(content, reasoning, isUser, timestamp, sessionId, null);
    }

    public ChatMessageEntity(String content, String reasoning, boolean isUser, long timestamp, long sessionId,
            String mediaPath) {
        this.content = content;
        this.reasoning = reasoning;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
        this.mediaPath = mediaPath;
    }

    // Getter and Setter
    public long getId() {
        return id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean user) {
        isUser = user;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public void setMediaPath(String mediaPath) {
        this.mediaPath = mediaPath;
    }
}
