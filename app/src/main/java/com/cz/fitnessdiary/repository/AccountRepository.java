package com.cz.fitnessdiary.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.cz.fitnessdiary.model.AccountUser;
import com.cz.fitnessdiary.model.EmailCodeChallenge;
import com.cz.fitnessdiary.service.CloudBaseAuthGateway;
import com.cz.fitnessdiary.service.CloudApiClient;
import com.cz.fitnessdiary.service.AccessTokenStore;
import com.cz.fitnessdiary.service.TokenStore;
import com.cz.fitnessdiary.utils.TextUtilsCompat;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.util.Locale;
import java.util.regex.Pattern;

/** CloudBase email-code account session. Local onboarding remains independent. */
public final class AccountRepository {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9]{6}$");

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Throwable error);
    }

    private final CloudBaseAuthGateway authGateway;
    private final TokenStore tokenStore;
    private final UserRepository userRepository;
    @Nullable private volatile AccountUser currentAccount;

    public AccountRepository(Context context) {
        this(CloudBaseAuthGateway.getInstance(), new TokenStore(context.getApplicationContext()),
                new UserRepository(context.getApplicationContext()));
    }

    AccountRepository(CloudBaseAuthGateway authGateway, TokenStore tokenStore, UserRepository userRepository) {
        this.authGateway = authGateway;
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
        String accountId = tokenStore.getAccountId();
        String email = tokenStore.getAccountEmail();
        if (accountId != null && email != null) {
            currentAccount = new AccountUser(accountId, email, true);
        }
    }

    @Nullable
    public AccountUser getCurrentAccount() { return currentAccount; }
    public boolean isLoggedIn() { return currentAccount != null; }
    public boolean isEmailVerified() { return currentAccount != null; }

    public void requestEmailCode(String email, Callback<EmailCodeChallenge> callback) {
        if (!isValidEmail(email)) {
            callback.onError(new IllegalArgumentException("请输入有效邮箱"));
            return;
        }
        String normalizedEmail = normalizeEmail(email);
        authGateway.sendEmailCode(normalizedEmail, new CloudBaseAuthGateway.ResultCallback() {
            @Override public void onSuccess(JsonObject result) {
                String verificationId = string(result, "verification_id");
                if (TextUtilsCompat.isBlank(verificationId)) {
                    callback.onError(new IllegalStateException("未收到验证码会话，请稍后重试"));
                    return;
                }
                boolean existingUser = result.has("is_user") && result.get("is_user").getAsBoolean();
                callback.onSuccess(new EmailCodeChallenge(normalizedEmail, verificationId, existingUser));
            }
            @Override public void onError(Throwable error) { callback.onError(error); }
        });
    }

    public void verifyEmailCode(EmailCodeChallenge challenge, String code, Callback<AccountUser> callback) {
        if (challenge == null || TextUtilsCompat.isBlank(challenge.getVerificationId())) {
            callback.onError(new IllegalArgumentException("验证码已失效，请重新获取"));
            return;
        }
        if (!isValidVerificationCode(code)) {
            callback.onError(new IllegalArgumentException("请输入 6 位验证码"));
            return;
        }
        authGateway.verifyCode(challenge.getVerificationId(), code.trim(), new CloudBaseAuthGateway.ResultCallback() {
            @Override public void onSuccess(JsonObject result) {
                String verificationToken = string(result, "verification_token");
                if (TextUtilsCompat.isBlank(verificationToken)) {
                    callback.onError(new IllegalStateException("验证码校验失败，请重新获取"));
                    return;
                }
                CloudBaseAuthGateway.ResultCallback tokenCallback = new CloudBaseAuthGateway.ResultCallback() {
                    @Override public void onSuccess(JsonObject token) { saveSession(challenge.getEmail(), token, callback); }
                    @Override public void onError(Throwable error) { callback.onError(error); }
                };
                if (challenge.isExistingUser()) authGateway.signIn(verificationToken, tokenCallback);
                else authGateway.signUp(challenge.getEmail(), verificationToken, tokenCallback);
            }
            @Override public void onError(Throwable error) { callback.onError(error); }
        });
    }

    public void bindCurrentLocalProfile(Callback<AccountUser> callback) {
        AccountUser account = currentAccount;
        if (account == null) {
            callback.onError(new IllegalStateException("请先登录云端账号"));
            return;
        }
        userRepository.bindCloudAccount(account.getObjectId(), System.currentTimeMillis(), success -> {
            if (success) callback.onSuccess(account);
            else callback.onError(new IllegalStateException("没有可绑定的本地档案"));
        });
    }

    /** Profile is created lazily by the CloudBase PostgreSQL RPC if it doesn't exist. */
    public void loadMyProfile(Callback<com.cz.fitnessdiary.model.FriendUiModel> callback) {
        CloudApiClient.getInstance().rpc("fd_my_profile", new JsonObject(), new CloudApiClient.ResultCallback() {
            @Override public void onSuccess(com.google.gson.JsonElement body) {
                JsonObject profile = new JsonObject();
                if (body.isJsonObject()) {
                    profile = body.getAsJsonObject();
                } else if (body.isJsonArray()) {
                    JsonArray profiles = body.getAsJsonArray();
                    if (!profiles.isEmpty() && profiles.get(0).isJsonObject()) {
                        profile = profiles.get(0).getAsJsonObject();
                    }
                }
                String userId = string(profile, "user_id", "id");
                String nickname = string(profile, "nickname", "displayName");
                String friendCode = string(profile, "friend_code", "friendCode");
                String bio = string(profile, "bio");
                String avatarUrl = string(profile, "avatar_object_key", "avatar_url", "avatarUrl");
                callback.onSuccess(new com.cz.fitnessdiary.model.FriendUiModel(userId, nickname, friendCode, bio, avatarUrl, ""));
            }
            @Override public void onError(Throwable error) { callback.onError(error); }
        });
    }

    public void logout() {
        AccountUser account = currentAccount;
        if (account != null) {
            userRepository.unbindCloudAccount(account.getObjectId(), ignored -> { });
        }
        tokenStore.clear();
        AccessTokenStore.getInstance().clear();
        currentAccount = null;
    }

    public void deleteAccount(Callback<Void> callback) {
        callback.onError(new IllegalStateException("请在服务端部署完成后再执行账号注销"));
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(normalizeEmail(email)).matches();
    }

    public static boolean isValidVerificationCode(String code) {
        return code != null && CODE_PATTERN.matcher(code.trim()).matches();
    }

    private void saveSession(String email, JsonObject token, Callback<AccountUser> callback) {
        String accessToken = string(token, "access_token");
        String refreshToken = string(token, "refresh_token");
        String userId = string(token, "sub", "user_id");
        if (TextUtilsCompat.isBlank(accessToken) || TextUtilsCompat.isBlank(refreshToken)
                || TextUtilsCompat.isBlank(userId)) {
            callback.onError(new IllegalStateException("CloudBase 登录响应不完整"));
            return;
        }
        try {
            tokenStore.saveSession(email, userId, refreshToken);
            AccessTokenStore.getInstance().set(accessToken);
            AccountUser account = new AccountUser(userId, email, true);
            currentAccount = account;
            callback.onSuccess(account);
        } catch (Exception error) {
            callback.onError(error);
        }
    }

    private static String string(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) return object.get(key).getAsString();
        }
        return "";
    }
}
