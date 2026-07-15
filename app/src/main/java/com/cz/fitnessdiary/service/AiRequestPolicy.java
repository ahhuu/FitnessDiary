package com.cz.fitnessdiary.service;

/** Central limits for AI requests. Keeping these values in one place prevents accidental token growth. */
public final class AiRequestPolicy {
    public static final int IMAGE_MAX_SIDE_PX = 768;
    public static final int IMAGE_JPEG_QUALITY = 75;
    public static final int FOOD_IMAGE_MAX_COMPLETION_TOKENS = 512;
    public static final int IMAGE_CHAT_MAX_COMPLETION_TOKENS = 384;
    public static final int CHAT_MAX_HISTORY_MESSAGES = 6;
    public static final int CHAT_MAX_HISTORY_CHARS = 3200;
    public static final int ADVICE_MAX_COMPLETION_TOKENS = 384;
    public static final int DIET_MAX_COMPLETION_TOKENS = 512;
    public static final int PLAN_MAX_COMPLETION_TOKENS = 768;
    /** Reasoning tokens and the final answer share max_tokens on DeepSeek thinking requests. */
    public static final int DEEP_THINKING_MAX_COMPLETION_TOKENS = 1536;

    private AiRequestPolicy() {
    }
}
