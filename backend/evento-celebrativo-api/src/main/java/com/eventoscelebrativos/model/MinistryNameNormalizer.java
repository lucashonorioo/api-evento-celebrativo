package com.eventoscelebrativos.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MinistryNameNormalizer {

    public static final int MAX_NAME_LENGTH = 150;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private MinistryNameNormalizer() {
    }

    public static String normalizeDisplayName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Nome do ministerio nao pode ser vazio.");
        }

        String normalizedWhitespace = WHITESPACE.matcher(name.trim()).replaceAll(" ");
        if (normalizedWhitespace.isBlank()) {
            throw new IllegalArgumentException("Nome do ministerio nao pode ser vazio.");
        }
        if (normalizedWhitespace.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Nome do ministerio deve ter no maximo " + MAX_NAME_LENGTH + " caracteres."
            );
        }

        return normalizedWhitespace;
    }

    public static String normalizeIdentity(String name) {
        String displayName = normalizeDisplayName(name);
        String withoutDiacritics = DIACRITICS.matcher(
                Normalizer.normalize(displayName, Normalizer.Form.NFD)
        ).replaceAll("");
        String normalizedName = withoutDiacritics.toUpperCase(Locale.ROOT);

        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Nome normalizado do ministerio deve ter no maximo " + MAX_NAME_LENGTH + " caracteres."
            );
        }

        return normalizedName;
    }
}
