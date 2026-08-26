package com.eventoscelebrativos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinistryTest {

    @Test
    void shouldCreateWithValidName() {
        Ministry ministry = new Ministry("Leitores");

        assertEquals("Leitores", ministry.getName());
        assertEquals("LEITORES", ministry.getNormalizedName());
    }

    @Test
    void shouldStartActive() {
        Ministry ministry = new Ministry("Leitores");

        assertTrue(ministry.isActive());
    }

    @Test
    void shouldTrimName() {
        Ministry ministry = new Ministry("  Acólitos  ");

        assertEquals("Acólitos", ministry.getName());
        assertEquals("ACOLITOS", ministry.getNormalizedName());
    }

    @Test
    void shouldCollapseInternalWhitespace() {
        Ministry ministry = new Ministry("Ministros   da   Palavra");

        assertEquals("Ministros da Palavra", ministry.getName());
        assertEquals("MINISTROS DA PALAVRA", ministry.getNormalizedName());
    }

    @Test
    void shouldNormalizeCaseInsensitiveName() {
        Ministry ministry = new Ministry("leitores");

        assertEquals("leitores", ministry.getName());
        assertEquals("LEITORES", ministry.getNormalizedName());
    }

    @Test
    void shouldRemoveDiacriticsFromNormalizedName() {
        Ministry ministry = new Ministry("Acólitos");

        assertEquals("Acólitos", ministry.getName());
        assertEquals("ACOLITOS", ministry.getNormalizedName());
    }

    @Test
    void shouldRenameAndUpdateNormalizedNameTogether() {
        Ministry ministry = new Ministry("Leitores");

        ministry.rename("  Acólitos ");

        assertEquals("Acólitos", ministry.getName());
        assertEquals("ACOLITOS", ministry.getNormalizedName());
    }

    @Test
    void shouldRejectNullName() {
        assertThrows(IllegalArgumentException.class, () -> new Ministry(null));
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Ministry("   "));
    }

    @Test
    void shouldRejectNameExceedingMaxLengthAfterWhitespaceNormalization() {
        String tooLong = "A".repeat(MinistryNameNormalizer.MAX_NAME_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> new Ministry(tooLong));
    }

    @Test
    void shouldRejectInvalidRename() {
        Ministry ministry = new Ministry("Leitores");

        assertThrows(IllegalArgumentException.class, () -> ministry.rename("   "));
    }

    @Test
    void shouldDeactivate() {
        Ministry ministry = new Ministry("Leitores");

        ministry.deactivate();

        assertFalse(ministry.isActive());
    }

    @Test
    void shouldActivateAgain() {
        Ministry ministry = new Ministry("Leitores");

        ministry.deactivate();
        ministry.activate();

        assertTrue(ministry.isActive());
    }
}
