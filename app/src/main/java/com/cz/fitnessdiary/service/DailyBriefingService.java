package com.cz.fitnessdiary.service;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.cz.fitnessdiary.BuildConfig;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.model.HealthScoreBreakdown;
import com.cz.fitnessdiary.model.WeeklyTrend;
import com.cz.fitnessdiary.repository.HealthAggregationRepository;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.HealthScoreCalculator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 日报服务 - v3.0
 * 负责生成、缓存每日健康简报。
 * 优先使用 DeepSeek AI 生成，失败或未配置时降级为本地规则引擎。
 */
public class DailyBriefingService {

    private static final String PREFS_NAME = "daily_briefing_prefs";
    private static final String KEY_CACHED_BRIEFING = "cached_briefing";
    private static final String KEY_CACHED_DATE = "cached_date";
    private static final String KEY_CACHED_AT = "cached_at";

    private static final long ONE_DAY_MS = 86400000L;

    private final Application application;
    private final HealthAggregationRepository aggregationRepo;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Gson gson;

    /**
     * 每日简报数据类
     */
    public static class DailyBriefing {
        public String greeting;
        public String scoreComment;
        public List<String> highlights;
        public String suggestion;
        public String motivation;
        public boolean isLocal;
        public long generatedAt;

        public DailyBriefing() {
            this.highlights = new ArrayList<>();
        }
    }

    /**
     * 简报生成回调接口
     */
    public interface DailyBriefingCallback {
        void onBriefingReady(DailyBriefing briefing);
    }

    public DailyBriefingService(Application application) {
        this.application = application;
        this.aggregationRepo = new HealthAggregationRepository(application);
        this.prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    // ================================================================
    // 公共 API
    // ================================================================

    /**
     * 获取缓存的简报（仅在缓存日期为今日时有效）
     *
     * @return 缓存的 DailyBriefing，若无缓存或已过期则返回 null
     */
    public DailyBriefing getCachedBriefing() {
        long cachedDate = prefs.getLong(KEY_CACHED_DATE, 0);
        long today = DateUtils.getTodayStartTimestamp();
        if (cachedDate != today) {
            return null;
        }
        String json = prefs.getString(KEY_CACHED_BRIEFING, null);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(json, DailyBriefing.class);
        } catch (JsonSyntaxException e) {
            prefs.edit().remove(KEY_CACHED_BRIEFING).apply();
            return null;
        }
    }

    /**
     * 清除缓存的简报
     */
    public void invalidateCache() {
        prefs.edit()
                .remove(KEY_CACHED_BRIEFING)
                .remove(KEY_CACHED_DATE)
                .remove(KEY_CACHED_AT)
                .apply();
    }

    /**
     * 生成今日简报（异步）
     * <p>
     * 流程：获取昨日数据快照 -> 评分 -> 尝试 AI 生成 -> 失败降级本地生成 -> 缓存 -> 回调
     *
     * @param callback 回调接口，在主线程执行
     */
    public void generateBriefing(DailyBriefingCallback callback) {
        executor.execute(() -> {
            try {
                // 1. 获取昨日数据
                long yesterday = DateUtils.getTodayStartTimestamp() - ONE_DAY_MS;
                DailyHealthSnapshot snapshot = aggregationRepo.getDateSnapshot(yesterday);
                HealthScoreCalculator.UserProfile profile = buildUserProfile();
                HealthScoreBreakdown breakdown = HealthScoreCalculator.calculateBreakdown(snapshot, profile);
                snapshot.healthScore = breakdown.totalScore;

                // 2. 尝试 AI 生成
                if (isDirectAiConfigured()) {
                    callAiForBriefing(snapshot, breakdown, callback);
                } else {
                    // 3. 降级为本地生成
                    DailyBriefing briefing = LocalBriefingGenerator.generate(snapshot, breakdown);
                    cacheBriefing(briefing);
                    mainHandler.post(() -> callback.onBriefingReady(briefing));
                }
            } catch (Exception e) {
                // 异常情况下生成最简简报
                DailyBriefing fallback = createEmergencyBriefing();
                mainHandler.post(() -> callback.onBriefingReady(fallback));
            }
        });
    }

    // ================================================================
    // 内部方法
    // ================================================================

    /** Direct personal AI is optional; local generation remains the safe fallback. */
    private boolean isDirectAiConfigured() {
        return !BuildConfig.DEEPSEEK_API_KEY.trim().isEmpty();
    }

    /**
     * 调用 DeepSeek AI 生成简报
     */
    private void callAiForBriefing(DailyHealthSnapshot snapshot,
                                   HealthScoreBreakdown breakdown,
                                   DailyBriefingCallback callback) {
        List<WeeklyTrend> trends = aggregationRepo.getWeeklyTrends();
        String userPrompt = buildUserPrompt(snapshot, breakdown, trends);
        String systemPrompt = "你是专业健康教练。基于用户健康数据生成今日简报。"
                + "以JSON格式输出，不要包含markdown代码块标记："
                + "{\"greeting\":\"...\",\"scoreComment\":\"...\","
                + "\"highlights\":[\"...\"],\"suggestion\":\"...\",\"motivation\":\"...\"}";

        DeepSeekService.sendMessage(userPrompt, systemPrompt,
                new AICallback() {
                    @Override
                    public void onSuccess(String response, String reasoning) {
                        DailyBriefing briefing = parseAiResponse(response);
                        if (briefing == null) {
                            // JSON 解析失败，降级本地生成
                            briefing = LocalBriefingGenerator.generate(snapshot, breakdown);
                        } else {
                            briefing.isLocal = false;
                        }
                        cacheBriefing(briefing);
                        callback.onBriefingReady(briefing);
                    }

                    @Override
                    public void onError(String error) {
                        // AI 调用失败，降级本地生成
                        DailyBriefing briefing = LocalBriefingGenerator.generate(snapshot, breakdown);
                        cacheBriefing(briefing);
                        callback.onBriefingReady(briefing);
                    }

                    @Override
                    public void onPartialUpdate(String content, String reasoning) {
                        // 非流式模式，无需处理
                    }
                });
    }

    /**
     * 构建 AI 用户 Prompt
     */
    private String buildUserPrompt(DailyHealthSnapshot snapshot,
                                   HealthScoreBreakdown breakdown,
                                   List<WeeklyTrend> trends) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户昨日健康数据生成一份简报。\n\n");
        sb.append("【昨日数据】\n");
        sb.append("- 健康评分：").append(breakdown.totalScore).append("/100\n");
        sb.append("- 运动消耗：").append(snapshot.exerciseCalories).append(" 千卡\n");
        sb.append("- 饮食摄入：").append(snapshot.dietCalories).append(" 千卡\n");
        sb.append("- 基础代谢：").append(snapshot.bmr).append(" 千卡\n");
        sb.append("- 热量平衡：").append(snapshot.energyBalance >= 0 ? "+" : "")
                .append(snapshot.energyBalance).append(" 千卡\n");
        sb.append("- 步数：").append(snapshot.steps).append("\n");
        sb.append("- 睡眠时长：").append(String.format(java.util.Locale.getDefault(),
                "%.1f", snapshot.sleepHours)).append(" 小时\n");
        sb.append("- 睡眠质量：").append(snapshot.sleepQuality).append("/5\n");
        sb.append("- 饮水量：").append(snapshot.waterMl).append(" ml\n");
        sb.append("- 完成计划：").append(snapshot.completedPlans)
                .append("/").append(snapshot.totalPlans).append("\n");
        sb.append("- 连续打卡：").append(snapshot.consecutiveDays).append(" 天\n");
        sb.append("- 心情等级：").append(snapshot.moodLevel).append("/5\n");
        if (snapshot.currentPlanName != null) {
            sb.append("- 当前计划：").append(snapshot.currentPlanName).append("\n");
        }

        // 评分明细
        sb.append("\n【评分明细】\n");
        sb.append("- 运动评分：").append(breakdown.exerciseScore).append("/25\n");
        sb.append("- 饮食评分：").append(breakdown.dietScore).append("/25\n");
        sb.append("- 习惯评分：").append(breakdown.habitsScore).append("/20\n");
        sb.append("- 身体指标评分：").append(breakdown.bodyMetricsScore).append("/15\n");
        sb.append("- 坚持度评分：").append(breakdown.consistencyScore).append("/15\n");

        // 7日趋势
        if (trends != null && !trends.isEmpty()) {
            sb.append("\n【近7日趋势】\n");
            for (WeeklyTrend trend : trends) {
                sb.append("- ").append(trend.label).append("：").append(trend.getChangeText()).append("\n");
            }
        }

        sb.append("\n请以JSON格式输出（仅JSON，不含markdown标记）：\n");
        sb.append("{\"greeting\":\"问候语\",\"scoreComment\":\"评分评价\",");
        sb.append("\"highlights\":[\"亮点1\",\"亮点2\"],");
        sb.append("\"suggestion\":\"一条建议\",\"motivation\":\"一句鼓励\"}");
        return sb.toString();
    }

    /**
     * 解析 AI 返回的 JSON 响应
     *
     * @param response AI 返回的原始字符串
     * @return 解析成功的 DailyBriefing，失败返回 null
     */
    private DailyBriefing parseAiResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        try {
            String json = response.trim();
            // 移除可能的 markdown 代码块标记
            if (json.startsWith("```")) {
                int start = json.indexOf('\n');
                if (start != -1) {
                    json = json.substring(start + 1);
                }
                int end = json.lastIndexOf("```");
                if (end != -1) {
                    json = json.substring(0, end);
                }
                json = json.trim();
            }

            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if (obj == null) {
                return null;
            }

            DailyBriefing briefing = new DailyBriefing();
            briefing.greeting = getJsonString(obj, "greeting");
            briefing.scoreComment = getJsonString(obj, "scoreComment");
            briefing.suggestion = getJsonString(obj, "suggestion");
            briefing.motivation = getJsonString(obj, "motivation");

            // 解析亮点列表
            briefing.highlights = new ArrayList<>();
            if (obj.has("highlights") && obj.get("highlights").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("highlights");
                for (int i = 0; i < arr.size(); i++) {
                    briefing.highlights.add(arr.get(i).getAsString());
                }
            }

            briefing.isLocal = false;
            briefing.generatedAt = System.currentTimeMillis();
            return briefing;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全地从 JsonObject 获取字符串字段
     */
    private static String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    /**
     * 缓存简报数据
     */
    private void cacheBriefing(DailyBriefing briefing) {
        try {
            String json = gson.toJson(briefing);
            prefs.edit()
                    .putString(KEY_CACHED_BRIEFING, json)
                    .putLong(KEY_CACHED_DATE, DateUtils.getTodayStartTimestamp())
                    .putLong(KEY_CACHED_AT, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
            // 缓存失败不影响使用
        }
    }

    /**
     * 构建用户健康档案配置
     */
    private HealthScoreCalculator.UserProfile buildUserProfile() {
        HealthScoreCalculator.UserProfile p = new HealthScoreCalculator.UserProfile();
        try {
            AppDatabase db = AppDatabase.getInstance(application);
            User user = db.userDao().getUserSync();
            if (user != null) {
                if (user.getDailyCalorieTarget() > 0) {
                    p.dailyCalorieTarget = user.getDailyCalorieTarget();
                }
                if (user.getDailyWaterTarget() > 0) {
                    p.waterTargetMl = user.getDailyWaterTarget();
                }
                p.weightKg = user.getWeight();
                p.heightCm = user.getHeight();
                p.age = user.getAge();
                p.gender = user.getGender() == 1 ? "male" : "female";
                int goalType = user.getGoalType();
                if (goalType == 0) {
                    p.goalType = "lose";
                } else if (goalType == 1) {
                    p.goalType = "gain";
                } else {
                    p.goalType = "maintain";
                }
            }
        } catch (Exception ignored) {
        }
        return p;
    }

    /**
     * 紧急情况下的最简简报
     */
    private static DailyBriefing createEmergencyBriefing() {
        DailyBriefing briefing = new DailyBriefing();
        briefing.greeting = "欢迎回来！";
        briefing.scoreComment = "暂时无法获取评分数据";
        briefing.highlights = new ArrayList<>();
        briefing.highlights.add("数据加载中，请稍后刷新");
        briefing.suggestion = "下拉刷新页面重试";
        briefing.motivation = "保持好心情，健康每一天！";
        briefing.isLocal = true;
        briefing.generatedAt = System.currentTimeMillis();
        return briefing;
    }
}
