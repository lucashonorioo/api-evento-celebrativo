package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 6;
    private static final int BCRYPT_MAX_BYTES = 72;

    public boolean isPresent(String password) {
        return password != null;
    }

    public void validateRequired(String password) {
        if (password == null) {
            throw new BadRequestException("A senha e obrigatoria");
        }
        validatePresent(password);
    }

    public void validatePresent(String password) {
        if (password == null) {
            return;
        }
        if (password.isBlank()) {
            throw new BadRequestException("A senha nao pode ser vazia");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BadRequestException("A senha deve ter no minimo 6 caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BadRequestException("A senha excede o limite aceito pelo encoder");
        }
    }
}
