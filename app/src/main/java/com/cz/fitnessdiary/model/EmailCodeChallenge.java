package com.cz.fitnessdiary.model;

import com.cz.fitnessdiary.utils.TextUtilsCompat;

/** A short-lived CloudBase email verification challenge. Never persist the code itself. */
public final class EmailCodeChallenge {
    private final String email;
    private final String verificationId;
    private final boolean existingUser;

    public EmailCodeChallenge(String email, String verificationId, boolean existingUser) {
        this.email = TextUtilsCompat.valueOrDefault(email, "");
        this.verificationId = TextUtilsCompat.valueOrDefault(verificationId, "");
        this.existingUser = existingUser;
    }

    public String getEmail() { return email; }
    public String getVerificationId() { return verificationId; }
    public boolean isExistingUser() { return existingUser; }
}
