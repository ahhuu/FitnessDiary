package com.cz.fitnessdiary.service;

import androidx.annotation.NonNull;

import com.cz.fitnessdiary.config.CloudApiConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Native Android integration for CloudBase Auth HTTP API. Authentication calls
 * go directly to CloudBase; no Tencent administrator key is embedded in APK.
 */
public final class CloudBaseAuthGateway {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final CloudBaseAuthGateway INSTANCE = new CloudBaseAuthGateway();
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();

    public interface ResultCallback {
        void onSuccess(JsonObject result);
        void onError(Throwable error);
    }

    public static CloudBaseAuthGateway getInstance() {
        return INSTANCE;
    }

    public boolean isConfigured() {
        return !CloudApiConfig.getCloudBaseGatewayUrl().isEmpty();
    }

    public void sendEmailCode(String email, ResultCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("target", "ANY");
        post("/auth/v1/verification", body, callback);
    }

    public void verifyCode(String verificationId, String verificationCode, ResultCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("verification_id", verificationId);
        body.addProperty("verification_code", verificationCode);
        post("/auth/v1/verification/verify", body, callback);
    }

    public void signIn(String verificationToken, ResultCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("verification_token", verificationToken);
        post("/auth/v1/signin", body, callback);
    }

    public void signUp(String email, String verificationToken, ResultCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("verification_token", verificationToken);
        post("/auth/v1/signup", body, callback);
    }

    public void refresh(String refreshToken, ResultCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("grant_type", "refresh_token");
        body.addProperty("refresh_token", refreshToken);
        post("/auth/v1/token", body, callback);
    }

    private void post(String path, JsonObject body, ResultCallback callback) {
        String gatewayUrl = CloudApiConfig.getCloudBaseGatewayUrl();
        if (gatewayUrl.isEmpty()) {
            callback.onError(new IllegalStateException("CloudBase 环境未配置，云端功能暂不可用"));
            return;
        }
        Request request = new Request.Builder()
                .url(gatewayUrl + path)
                .post(RequestBody.create(gson.toJson(body), JSON))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                callbackExecutor.execute(() -> callback.onError(exception));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String text = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    callbackExecutor.execute(() -> callback.onError(new IllegalStateException(errorMessage(text, response.code()))));
                    return;
                }
                try {
                    JsonObject result = gson.fromJson(text, JsonObject.class);
                    callbackExecutor.execute(() -> callback.onSuccess(result == null ? new JsonObject() : result));
                } catch (RuntimeException exception) {
                    callbackExecutor.execute(() -> callback.onError(exception));
                }
            }
        });
    }

    private String errorMessage(String text, int statusCode) {
        try {
            JsonObject body = gson.fromJson(text, JsonObject.class);
            if (body != null && body.has("error_description")) return body.get("error_description").getAsString();
            if (body != null && body.has("message")) return body.get("message").getAsString();
        } catch (RuntimeException ignored) {
            // Fall through to the safe generic message.
        }
        return "云端认证请求失败（" + statusCode + "）";
    }
}
