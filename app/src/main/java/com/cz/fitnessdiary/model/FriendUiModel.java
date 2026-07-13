package com.cz.fitnessdiary.model;

import com.cz.fitnessdiary.utils.TextUtilsCompat;

public final class FriendUiModel {
    private final String userId;
    private final String nickname;
    private final String friendCode;
    private final String bio;
    private final String avatarUrl;
    private final String requestId;

    public FriendUiModel(String userId, String nickname, String friendCode, String bio,
                         String avatarUrl, String requestId) {
        this.userId = TextUtilsCompat.valueOrDefault(userId, "");
        this.nickname = TextUtilsCompat.valueOrDefault(nickname, "健身伙伴");
        this.friendCode = TextUtilsCompat.valueOrDefault(friendCode, "");
        this.bio = TextUtilsCompat.valueOrDefault(bio, "一起坚持，慢慢变好");
        this.avatarUrl = TextUtilsCompat.valueOrDefault(avatarUrl, "");
        this.requestId = TextUtilsCompat.valueOrDefault(requestId, "");
    }

    public String getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public String getFriendCode() { return friendCode; }
    public String getBio() { return bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRequestId() { return requestId; }
}
