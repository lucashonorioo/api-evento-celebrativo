package com.eventoscelebrativos.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

@Configuration
public class TemporalJsonConfig {

    private static final DateTimeFormatter SECOND_PRECISION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final DateTimeFormatter LOCAL_DATE_TIME_INPUT_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                    .optionalEnd()
                    .toFormatter()
                    .withResolverStyle(ResolverStyle.STRICT);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeJsonCustomizer() {
        return builder -> {
            builder.serializers(new LocalDateTimeSerializer(SECOND_PRECISION_FORMATTER));
            builder.deserializers(new LocalDateTimeInputDeserializer());
        };
    }

    private static final class LocalDateTimeInputDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public Class<?> handledType() {
            return LocalDateTime.class;
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String value = parser.getValueAsString();
            try {
                return LocalDateTime.parse(value, LOCAL_DATE_TIME_INPUT_FORMATTER);
            } catch (DateTimeParseException exception) {
                throw InvalidFormatException.from(
                        parser,
                        "Use uma data e hora local no formato yyyy-MM-dd'T'HH:mm:ss.",
                        value,
                        LocalDateTime.class
                );
            }
        }
    }
}
