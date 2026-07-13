package com.cz.fitnessdiary.service;

import androidx.annotation.Nullable;

/** Access tokens intentionally live only in process memory. */
public final class AccessTokenStore {
    private static final AccessTokenStore INSTANCE = new AccessTokenStore();
    @Nullable private volatile String accessToken;
    private AccessTokenStore() { }
    public static AccessTokenStore getInstance() { return INSTANCE; }
    public void set(@Nullable String value) { accessToken = value; }
    @Nullable public String get() { return accessToken; }
    public void clear() { accessToken = null; }
}
