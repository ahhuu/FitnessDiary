package com.cz.fitnessdiary.model;

import androidx.annotation.Nullable;

public final class AccountUser {
    private final String objectId;
    private final String email;
    private final boolean emailVerified;

    public AccountUser(String objectId, @Nullable String email, boolean emailVerified) {
        this.objectId = objectId;
        this.email = email;
        this.emailVerified = emailVerified;
    }

    public String getObjectId() {
        return objectId;
    }

    @Nullable
    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
