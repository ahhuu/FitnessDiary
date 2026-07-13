package com.cz.fitnessdiary.repository;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountRepositoryTest {
    @Test
    public void normalizeEmail_trimsAndLowercases() {
        assertEquals("user@example.com", AccountRepository.normalizeEmail("  User@Example.COM "));
    }

    @Test
    public void emailValidation_matchesEmailCodeLoginContract() {
        assertTrue(AccountRepository.isValidEmail("user@example.com"));
        assertFalse(AccountRepository.isValidEmail("not-an-email"));
    }

    @Test
    public void verificationCode_requiresSixDigits() {
        assertTrue(AccountRepository.isValidVerificationCode("123456"));
        assertTrue(AccountRepository.isValidVerificationCode(" 123456 "));
        assertFalse(AccountRepository.isValidVerificationCode("12345"));
        assertFalse(AccountRepository.isValidVerificationCode("12345a"));
        assertFalse(AccountRepository.isValidVerificationCode(null));
    }
}
