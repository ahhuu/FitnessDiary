package com.cz.fitnessdiary.model;

/**
 * 聊天消息实体类 (UI 模型)
 */
public class ChatMessage {
    private long id; // 消息在数据库中的 ID
    private String content; // 消息内容
    private String reasoning; // 思考过程 (针对 DeepSeek R1)
    private boolean isUser; // 是否是用户发送 (true=用户, false=AI)
    private long timestamp; // 时间戳

    public ChatMessage(String content, boolean isUser) {
        this(0, content, null, isUser, System.currentTimeMillis());
    }

    public ChatMessage(String content, String reasoning, boolean isUser) {
        this(0, content, reasoning, isUser, System.currentTimeMillis());
    }

    public ChatMessage(long id, String content, String reasoning, boolean isUser, long timestamp) {
        this.id = id;
        this.content = content;
        this.reasoning = reasoning;
        this.isUser = isUser;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
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
}
