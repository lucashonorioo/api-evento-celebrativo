package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;

import java.util.regex.Pattern;

/**
 * Validacao de conteudo em texto simples do titulo e da mensagem de notificacao: aplica trim
 * externo, limita o tamanho apos o trim e rejeita marcacao HTML evidente, sem sanitizar
 * silenciosamente (o chamador recebe erro, nunca um valor alterado). Espacos internos e quebras de
 * linha sao preservados.
 */
public final class NotificationContentValidator {

    public static final int MAX_TITLE_LENGTH = 120;
    public static final int MAX_MESSAGE_LENGTH = 2000;

    // Casa tags de abertura/fechamento (<script>, </b>, <img src="...">), comentarios (<!-- ... -->)
    // e doctype (<!DOCTYPE ...>): '<' seguido de '/' opcional e uma letra ou '!'. Isso rejeita
    // marcacao HTML evidente sem casar comparacoes legitimas como "2 < 3" (o caractere apos '<' e um
    // espaco, nao uma letra nem '!').
    private static final Pattern HTML_MARKUP = Pattern.compile("<(/?[a-zA-Z!][^<>]*)>");

    private NotificationContentValidator() {
    }

    public static String normalizeTitle(String rawTitle) {
        String trimmed = requireNonBlank(rawTitle, "O título é obrigatório");
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("O título deve ter no máximo 120 caracteres");
        }
        rejectMarkup(trimmed, "título");
        return trimmed;
    }

    public static String normalizeMessage(String rawMessage) {
        String trimmed = requireNonBlank(rawMessage, "A mensagem é obrigatória");
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("A mensagem deve ter no máximo 2000 caracteres");
        }
        rejectMarkup(trimmed, "mensagem");
        return trimmed;
    }

    private static String requireNonBlank(String raw, String message) {
        if (raw == null) {
            throw new BadRequestException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }

    private static void rejectMarkup(String value, String fieldLabel) {
        if (HTML_MARKUP.matcher(value).find()) {
            throw new BadRequestException("O campo " + fieldLabel + " não pode conter marcação HTML");
        }
    }
}
