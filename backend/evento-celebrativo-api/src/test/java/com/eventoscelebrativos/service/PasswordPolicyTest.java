package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void shouldRejectNullPasswordWhenRequired() {
        assertThrows(BadRequestException.class, () -> passwordPolicy.validateRequired(null));
    }

    @Test
    void shouldRejectEmptyOrBlankPasswordWhenPresent() {
        assertThrows(BadRequestException.class, () -> passwordPolicy.validatePresent(""));
        assertThrows(BadRequestException.class, () -> passwordPolicy.validatePresent("   "));
        assertThrows(BadRequestException.class, () -> passwordPolicy.validatePresent("\t\n"));
    }

    @Test
    void shouldRejectPasswordShorterThanSixCharacters() {
        assertThrows(BadRequestException.class, () -> passwordPolicy.validatePresent("12345"));
    }

    @Test
    void shouldAcceptUnicodePasswordWithinBcryptLimit() {
        assertDoesNotThrow(() -> passwordPolicy.validatePresent("áéíóúç"));
        assertDoesNotThrow(() -> passwordPolicy.validatePresent("🔐".repeat(18)));
    }

    @Test
    void shouldRejectPasswordAboveBcryptByteLimit() {
        assertDoesNotThrow(() -> passwordPolicy.validatePresent("a".repeat(72)));
        assertThrows(BadRequestException.class, () -> passwordPolicy.validatePresent("a".repeat(73)));
        assertThrows(BadRequestException.class, () -> passwordPolicy.validatePresent("🔐".repeat(19)));
    }
}
