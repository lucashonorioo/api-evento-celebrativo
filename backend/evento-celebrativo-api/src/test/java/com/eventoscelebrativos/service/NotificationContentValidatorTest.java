package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.service.impl.NotificationContentValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationContentValidatorTest {

    @Test
    void shouldTrimExternalWhitespaceFromTitleAndMessage() {
        assertEquals("Título", NotificationContentValidator.normalizeTitle("   Título   "));
        assertEquals("Mensagem", NotificationContentValidator.normalizeMessage("  Mensagem  "));
    }

    @Test
    void shouldPreserveInternalSpacesAndLineBreaksInMessage() {
        String message = "Linha 1\nLinha  2\n\nLinha 3 com   espacos";
        assertEquals(message, NotificationContentValidator.normalizeMessage("  " + message + "  "));
    }

    @Test
    void shouldRejectBlankOrNullTitle() {
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeTitle(null));
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeTitle("   "));
    }

    @Test
    void shouldRejectBlankOrNullMessage() {
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeMessage(null));
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeMessage("   "));
    }

    @Test
    void shouldAcceptTitleAtExactly120CharactersAfterTrim() {
        String title = "T".repeat(120);
        assertEquals(title, NotificationContentValidator.normalizeTitle("  " + title + "  "));
    }

    @Test
    void shouldRejectTitleLongerThan120CharactersAfterTrim() {
        String title = "T".repeat(121);
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeTitle(title));
    }

    @Test
    void shouldAcceptMessageAtExactly2000CharactersAfterTrim() {
        String message = "M".repeat(2000);
        assertEquals(message, NotificationContentValidator.normalizeMessage("  " + message + "  "));
    }

    @Test
    void shouldRejectMessageLongerThan2000CharactersAfterTrim() {
        String message = "M".repeat(2001);
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeMessage(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "</b>",
            "<img src=\"x\">",
            "<a href=\"http://x\">link</a>",
            "<iframe></iframe>",
            "<!-- comentario -->",
            "<!DOCTYPE html>"
    })
    void shouldRejectEvidentHtmlMarkupInTitleAndMessage(String markup) {
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeTitle(markup));
        assertThrows(BadRequestException.class, () -> NotificationContentValidator.normalizeMessage(markup));
    }

    @Test
    void shouldAcceptLegitimateNumericComparisons() {
        assertEquals("2 < 3", NotificationContentValidator.normalizeTitle("2 < 3"));
        assertEquals("5 > 4", NotificationContentValidator.normalizeMessage("5 > 4"));
        assertEquals(
                "Resultado: 2 < 3 e 5 > 4",
                NotificationContentValidator.normalizeMessage("Resultado: 2 < 3 e 5 > 4")
        );
    }

    @Test
    void shouldAcceptUnicodeContent() {
        String unicode = "Reunião às 19h30 — ministério 🙏 café ☕日本語";
        assertEquals(unicode, NotificationContentValidator.normalizeMessage(unicode));
    }

    @Test
    void shouldNotSanitizeSilentlyButRejectWithException() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> NotificationContentValidator.normalizeMessage("Ola <b>mundo</b>")
        );
        assertEquals("BAD_REQUEST", exception.getErrorCode());
    }
}
