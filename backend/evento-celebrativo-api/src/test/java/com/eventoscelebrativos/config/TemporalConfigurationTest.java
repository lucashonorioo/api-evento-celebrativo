package com.eventoscelebrativos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporalConfigurationTest {

    @Test
    void shouldCreateClockWithConfiguredApplicationZone() {
        Clock clock = new ClockConfig().clock("America/Sao_Paulo");

        assertEquals(ZoneId.of("America/Sao_Paulo"), clock.getZone());
    }

    @Test
    void shouldSerializeLocalDateTimeWithCanonicalSecondPrecision() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new TemporalJsonConfig().localDateTimeJsonCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        String json = objectMapper.writeValueAsString(
                LocalDateTime.of(2026, 8, 9, 19, 0)
        );

        assertEquals("\"2026-08-09T19:00:00\"", json);
    }

    @Test
    void shouldDeserializeFractionsWithoutTruncatingBeforeBusinessValidation() throws Exception {
        ObjectMapper objectMapper = objectMapper();

        LocalDateTime value = objectMapper.readValue(
                "\"2026-08-09T19:00:00.123456\"",
                LocalDateTime.class
        );

        assertEquals(123_456_000, value.getNano());
    }

    @Test
    void shouldRejectOffsetAndUtcDateTimes() {
        ObjectMapper objectMapper = objectMapper();

        assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(
                "\"2026-08-09T19:00:00-03:00\"",
                LocalDateTime.class
        ));
        assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(
                "\"2026-08-09T22:00:00Z\"",
                LocalDateTime.class
        ));
    }

    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new TemporalJsonConfig().localDateTimeJsonCustomizer().customize(builder);
        return builder.build();
    }
}
