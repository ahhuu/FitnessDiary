package com.cz.fitnessdiary.model;

public class FoodScanFlowState {
    public enum Stage {
        IDLE,
        UPLOAD,
        RECOGNIZE,
        NUTRITION,
        SUGGESTION,
        SUCCESS,
        ERROR
    }

    private Stage stage;
    private int progress;
    private String title;
    private String subtitle;
    private String error;
    private boolean retryable;

    public FoodScanFlowState(Stage stage, int progress, String title, String subtitle) {
        this.stage = stage;
        this.progress = progress;
        this.title = title;
        this.subtitle = subtitle;
    }

    public static FoodScanFlowState error(String error, boolean retryable) {
        FoodScanFlowState state = new FoodScanFlowState(Stage.ERROR, 0, "识别失败", error);
        state.error = error;
        state.retryable = retryable;
        return state;
    }

    public Stage getStage() { return stage; }
    public int getProgress() { return progress; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getError() { return error; }
    public boolean isRetryable() { return retryable; }
}
