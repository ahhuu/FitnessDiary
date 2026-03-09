package com.cz.fitnessdiary.model;

public class SmartSuggestionItem {
    private final String prompt;
    private String response;
    private final long timestamp;
    private boolean executed;
    private String actionLabel;

    public SmartSuggestionItem(String prompt, String response, long timestamp) {
        this(prompt, response, timestamp, false, "未执行");
    }

    public SmartSuggestionItem(String prompt, String response, long timestamp, boolean executed, String actionLabel) {
        this.prompt = prompt;
        this.response = response;
        this.timestamp = timestamp;
        this.executed = executed;
        this.actionLabel = actionLabel == null || actionLabel.trim().isEmpty()
                ? (executed ? "已执行" : "未执行")
                : actionLabel;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }
}
