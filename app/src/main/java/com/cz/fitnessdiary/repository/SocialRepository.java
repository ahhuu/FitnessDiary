package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.annotation.NonNull;

import com.cz.fitnessdiary.model.FriendUiModel;
import com.cz.fitnessdiary.model.SocialPostUiModel;
import com.cz.fitnessdiary.service.CloudApiClient;
import com.cz.fitnessdiary.utils.TextUtilsCompat;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Social implementation backed by CloudBase PostgreSQL RPC and RLS. */
public final class SocialRepository {
    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Throwable error);
    }

    private final CloudApiClient api;
    private final Gson gson = new Gson();

    public SocialRepository(@NonNull Application application) {
        api = CloudApiClient.getInstance();
    }

    public void searchFriend(String friendCode, Callback<FriendUiModel> callback) {
        String code = friendCode == null ? "" : friendCode.trim().toUpperCase(Locale.ROOT);
        JsonObject body = new JsonObject();
        body.addProperty("p_friend_code", code);
        api.rpc("fd_find_profile", body, map(callback, value -> {
            List<?> rows = asList(value);
            if (rows.isEmpty()) throw new IllegalStateException("未找到该好友码");
            return friendFrom(asMap(rows.get(0)));
        }));
    }

    public void sendFriendRequest(String friendCode, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_friend_code", friendCode == null ? "" : friendCode.trim().toUpperCase(Locale.ROOT));
        api.rpc("fd_send_friend_request", body, voidCallback(callback));
    }

    public void loadFriends(Callback<List<FriendUiModel>> callback) {
        api.rpc("fd_list_friends", new JsonObject(), map(callback, this::friendList));
    }

    public void loadRequests(Callback<List<FriendUiModel>> callback) {
        api.rpc("fd_list_requests", new JsonObject(), map(callback, this::friendList));
    }

    public void respondToRequest(String requestId, boolean accept, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_request_id", requestId);
        body.addProperty("p_accept", accept);
        api.rpc("fd_respond_friend_request", body, voidCallback(callback));
    }

    public void loadFeed(String cursor, Callback<List<SocialPostUiModel>> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_limit", 20);
        api.rpc("fd_feed", body, map(callback, this::postList));
    }

    public void updateProfile(String nickname, String bio, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_nickname", nickname == null ? "" : nickname.trim());
        body.addProperty("p_bio", bio == null ? "" : bio.trim());
        api.rpc("fd_update_profile", body, voidCallback(callback));
    }

    public void createPost(String content, Map<String, Object> healthSummary, Callback<Void> callback) {
        String safeContent = content == null ? "" : content.trim();
        Map<String, Object> safeSummary;
        try {
            safeSummary = sanitizeHealthSummary(healthSummary);
        } catch (RuntimeException error) {
            callback.onError(error);
            return;
        }
        if (TextUtilsCompat.isBlank(safeContent) && safeSummary.isEmpty()) {
            callback.onError(new IllegalArgumentException("请填写动态内容或选择健康摘要"));
            return;
        }

        JsonObject rpcBody = new JsonObject();
        rpcBody.addProperty("p_content", safeContent);
        rpcBody.add("p_health_summary", gson.toJsonTree(safeSummary));
        rpcBody.addProperty("p_client_request_id", UUID.randomUUID().toString());
        api.rpc("fd_create_post", rpcBody, voidCallback(callback));
    }

    /** Client-side mirror of the database whitelist; the database remains authoritative. */
    public static Map<String, Object> sanitizeHealthSummary(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input == null) return result;
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("achievement".equals(key)) {
                if (!(value instanceof String) || TextUtilsCompat.isBlank((String) value)
                        || ((String) value).trim().length() > 80) {
                    throw new IllegalArgumentException("成就摘要无效");
                }
                result.put(key, ((String) value).trim());
            } else if ("workoutMinutes".equals(key) || "checkInDays".equals(key) || "steps".equals(key)) {
                result.put(key, validSummaryNumber(key, value));
            } else {
                throw new IllegalArgumentException("不支持的健康摘要字段: " + key);
            }
        }
        return result;
    }

    private static long validSummaryNumber(String key, Object value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(key + " 必须是非负整数");
        }
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number) || number < 0
                || number > 1_000_000 || number != Math.rint(number)) {
            throw new IllegalArgumentException(key + " 必须是 0 到 1000000 的整数");
        }
        return (long) number;
    }

    public void setLiked(String postId, boolean liked, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_post_id", postId);
        body.addProperty("p_liked", liked);
        api.rpc("fd_toggle_like", body, voidCallback(callback));
    }

    public void deletePost(String postId, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_post_id", postId);
        api.rpc("fd_delete_post", body, voidCallback(callback));
    }

    public void removeFriend(String userId, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_user_id", userId);
        api.rpc("fd_remove_friend", body, voidCallback(callback));
    }

    public void blockUser(String userId, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_user_id", userId);
        api.rpc("fd_block_user", body, voidCallback(callback));
    }

    public void reportContent(String type, String id, String reason, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("p_target_type", type);
        body.addProperty("p_target_id", id);
        body.addProperty("p_reason", reason);
        api.rpc("fd_report", body, voidCallback(callback));
    }

    private CloudApiClient.ResultCallback voidCallback(Callback<Void> callback) {
        return map(callback, ignored -> null);
    }

    private <T> CloudApiClient.ResultCallback map(Callback<T> callback, Mapper<T> mapper) {
        return new CloudApiClient.ResultCallback() {
            @Override
            public void onSuccess(JsonElement body) {
                try {
                    callback.onSuccess(mapper.map(unwrap(body)));
                } catch (RuntimeException error) {
                    callback.onError(error);
                }
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        };
    }

    private Object unwrap(JsonElement element) {
        Object value = gson.fromJson(element, Object.class);
        Map<String, Object> map = asMap(value);
        return map.containsKey("data") ? map.get("data") : value;
    }

    private List<FriendUiModel> friendList(Object value) {
        List<FriendUiModel> result = new ArrayList<>();
        for (Object row : asList(value)) result.add(friendFrom(asMap(row)));
        return result;
    }

    private FriendUiModel friendFrom(Map<String, Object> row) {
        Map<String, Object> profile = row.containsKey("profile") ? asMap(row.get("profile")) : row;
        return new FriendUiModel(
                string(profile, "userId", "user_id", "id"),
                string(profile, "nickname", "displayName"),
                string(profile, "friendCode", "friend_code"),
                string(profile, "bio"),
                string(profile, "avatarUrl", "avatar_url"),
                string(row, "requestId", "request_id", "id"));
    }

    private List<SocialPostUiModel> postList(Object value) {
        List<SocialPostUiModel> result = new ArrayList<>();
        for (Object item : asList(value)) {
            Map<String, Object> row = asMap(item);
            Map<String, Object> author = asMap(row.get("author"));
            String avatarUrl = string(row, "avatar_url", "avatarUrl", "avatar_object_key");
            if (TextUtilsCompat.isBlank(avatarUrl)) {
                avatarUrl = string(author, "avatar_url", "avatarUrl", "avatar_object_key");
            }
            String authorName = string(author, "nickname", "displayName");
            if (TextUtilsCompat.isBlank(authorName)) {
                authorName = string(row, "nickname", "authorName");
            }
            result.add(new SocialPostUiModel(
                    string(row, "postId", "post_id", "id"),
                    authorName,
                    avatarUrl,
                    string(row, "content", "text"),
                    string(row, "timeLabel", "createdAt", "created_at"),
                    summaryLines(asMap(row.containsKey("healthSummary")
                            ? row.get("healthSummary") : row.get("health_summary"))),
                    integer(row, "likeCount", "like_count"),
                    bool(row, "liked"),
                    bool(row, "ownedByCurrentUser", "owned_by_current_user", "isMine")));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?>) return (List<?>) value;
        Map<String, Object> map = asMap(value);
        Object rows = map.containsKey("items") ? map.get("items") : map.get("results");
        return rows instanceof List<?> ? (List<?>) rows : Collections.emptyList();
    }

    private String string(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.get(key) != null) return String.valueOf(map.get(key));
        return "";
    }

    private int integer(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.get(key) instanceof Number) return ((Number) map.get(key)).intValue();
        return 0;
    }

    private boolean bool(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.get(key) instanceof Boolean) return (Boolean) map.get(key);
        return false;
    }

    private List<String> summaryLines(Map<String, Object> summary) {
        List<String> lines = new ArrayList<>();
        if (summary.get("workoutMinutes") instanceof Number) {
            lines.add("训练 " + ((Number) summary.get("workoutMinutes")).intValue() + " 分钟");
        }
        if (summary.get("checkInDays") instanceof Number) {
            lines.add("连续打卡 " + ((Number) summary.get("checkInDays")).intValue() + " 天");
        }
        if (summary.get("steps") instanceof Number) {
            lines.add("步数 " + ((Number) summary.get("steps")).intValue());
        }
        if (summary.get("achievement") instanceof String) {
            lines.add((String) summary.get("achievement"));
        }
        return lines;
    }

    private interface Mapper<T> {
        T map(Object value);
    }
}
