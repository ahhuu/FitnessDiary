package com.cz.fitnessdiary.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.config.CloudApiConfig;
import com.cz.fitnessdiary.utils.TextUtilsCompat;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Authenticated client for CloudBase PostgreSQL PostgREST/RPC. */
public final class CloudApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final CloudApiClient INSTANCE = new CloudApiClient();

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final Object refreshLock = new Object();
    private final List<PendingRequest> pendingRequests = new ArrayList<>();

    @Nullable private TokenStore tokenStore;
    private boolean refreshing;

    public interface ResultCallback {
        void onSuccess(JsonElement body);
        void onError(Throwable error);
    }

    private interface Continuation {
        void run();
    }

    private static final class PendingRequest {
        private final Continuation continuation;
        @Nullable private final ResultCallback callback;

        private PendingRequest(Continuation continuation, @Nullable ResultCallback callback) {
            this.continuation = continuation;
            this.callback = callback;
        }
    }

    public static CloudApiClient getInstance() {
        return INSTANCE;
    }

    /** Restores the short-lived access token from the encrypted refresh token after app start. */
    public void initialize(Context context) {
        tokenStore = new TokenStore(context.getApplicationContext());
        if (AccessTokenStore.getInstance().get() == null) {
            refreshAccessToken(() -> { }, null);
        }
    }

    public void get(String path, ResultCallback callback) {
        request("GET", path, null, false, callback);
    }

    public void post(String path, JsonObject body, ResultCallback callback) {
        request("POST", path, body, true, callback);
    }

    public void put(String path, JsonObject body, ResultCallback callback) {
        request("PUT", path, body, true, callback);
    }

    public void delete(String path, ResultCallback callback) {
        request("DELETE", path, null, true, callback);
    }

    public void rpc(String functionName, JsonObject body, ResultCallback callback) {
        request("POST", "/v1/rdb/rest/rpc/" + functionName, body, true, callback);
    }

    private void request(String method, String path, @Nullable JsonObject body, boolean idempotent,
                         ResultCallback callback) {
        request(method, path, body, idempotent, callback, false);
    }

    private void request(String method, String path, @Nullable JsonObject body, boolean idempotent,
                         ResultCallback callback, boolean refreshed) {
        String baseUrl = CloudApiConfig.getCloudBaseGatewayUrl();
        if (baseUrl.isEmpty()) {
            callback.onError(new IllegalStateException("CloudBase environment is not configured"));
            return;
        }

        String accessToken = AccessTokenStore.getInstance().get();
        if (TextUtilsCompat.isBlank(accessToken)) {
            refreshAccessToken(() -> request(method, path, body, idempotent, callback, true), callback);
            return;
        }

        HttpUrl url = HttpUrl.parse(baseUrl + path);
        if (url == null) {
            callback.onError(new IllegalStateException("Invalid CloudBase API address"));
            return;
        }

        RequestBody requestBody = body == null ? null : RequestBody.create(gson.toJson(body), JSON);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken);
        if (idempotent) {
            builder.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        if ("GET".equals(method)) {
            builder.get();
        } else if ("DELETE".equals(method)) {
            builder.delete();
        } else if ("PUT".equals(method)) {
            builder.put(requestBody == null ? RequestBody.create("{}", JSON) : requestBody);
        } else {
            builder.post(requestBody == null ? RequestBody.create("{}", JSON) : requestBody);
        }

        client.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException error) {
                callback.onError(error);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String text = response.body() == null ? "" : response.body().string();
                if (response.code() == 401 && !refreshed) {
                    AccessTokenStore.getInstance().clear();
                    refreshAccessToken(() -> request(method, path, body, idempotent, callback, true), callback);
                    return;
                }
                if (!response.isSuccessful()) {
                    callback.onError(new IllegalStateException("Cloud request failed: " + response.code()));
                    return;
                }
                try {
                    callback.onSuccess(gson.fromJson(text, JsonElement.class));
                } catch (RuntimeException exception) {
                    callback.onError(exception);
                }
            }
        });
    }

    private void refreshAccessToken(Continuation continuation, @Nullable ResultCallback callback) {
        TokenStore store = tokenStore;
        String refreshToken = store == null ? null : store.getRefreshToken();
        if (TextUtilsCompat.isBlank(refreshToken)) {
            if (callback != null) {
                callback.onError(new IllegalStateException("登录已失效，请重新登录"));
            }
            return;
        }

        synchronized (refreshLock) {
            pendingRequests.add(new PendingRequest(continuation, callback));
            if (refreshing) {
                return;
            }
            refreshing = true;
        }

        CloudBaseAuthGateway.getInstance().refresh(refreshToken, new CloudBaseAuthGateway.ResultCallback() {
            @Override
            public void onSuccess(JsonObject token) {
                String accessToken = string(token, "access_token");
        if (TextUtilsCompat.isBlank(accessToken)) {
                    finishRefreshError(new IllegalStateException("未收到有效的登录令牌"));
                    return;
                }
                try {
                    String newRefreshToken = string(token, "refresh_token");
                    TokenStore currentStore = tokenStore;
        if (!TextUtilsCompat.isBlank(newRefreshToken) && currentStore != null) {
                        currentStore.saveRefreshToken(newRefreshToken);
                    }
                    AccessTokenStore.getInstance().set(accessToken);
                    finishRefreshSuccess();
                } catch (Exception error) {
                    finishRefreshError(error);
                }
            }

            @Override
            public void onError(Throwable error) {
                finishRefreshError(error);
            }
        });
    }

    private void finishRefreshSuccess() {
        List<PendingRequest> pending = takePendingRequests();
        for (PendingRequest request : pending) {
            request.continuation.run();
        }
    }

    private void finishRefreshError(Throwable error) {
        AccessTokenStore.getInstance().clear();
        TokenStore store = tokenStore;
        if (store != null) {
            store.clear();
        }
        List<PendingRequest> pending = takePendingRequests();
        for (PendingRequest request : pending) {
            if (request.callback != null) {
                request.callback.onError(error);
            }
        }
    }

    private List<PendingRequest> takePendingRequests() {
        synchronized (refreshLock) {
            List<PendingRequest> pending = new ArrayList<>(pendingRequests);
            pendingRequests.clear();
            refreshing = false;
            return pending;
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
