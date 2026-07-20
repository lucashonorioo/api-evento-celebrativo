package com.eventoscelebrativos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonMinistryReadSourcePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void shouldUseLegacyAsDefaultReaderReadSource() {
        contextRunner.run(context -> {
            PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

            assertEquals(PersonMinistryReadSource.LEGACY, properties.getReader());
            assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
            assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
            assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
        });
    }

    @Test
    void shouldKeepBaseApplicationReaderReadSourceAsLegacy() {
        Properties properties = loadProperties("application.properties");

        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_READER:LEGACY}",
                properties.getProperty("app.person-ministry.read-source.reader")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_COMMENTATOR:LEGACY}",
                properties.getProperty("app.person-ministry.read-source.commentator")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_PRIEST:LEGACY}",
                properties.getProperty("app.person-ministry.read-source.priest")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD:LEGACY}",
                properties.getProperty("app.person-ministry.read-source.minister-of-the-word")
        );
    }

    @Test
    void shouldEnableParallelReaderReadSourceOnlyInLocalProfileWithEnvironmentOverride() {
        Properties properties = loadProperties("application-local.properties");

        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_READER:PARALLEL}",
                properties.getProperty("app.person-ministry.read-source.reader")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_COMMENTATOR:PARALLEL}",
                properties.getProperty("app.person-ministry.read-source.commentator")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_PRIEST:PARALLEL}",
                properties.getProperty("app.person-ministry.read-source.priest")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD:PARALLEL}",
                properties.getProperty("app.person-ministry.read-source.minister-of-the-word")
        );
    }

    @Test
    void shouldNotOverrideReaderReadSourceInTestProfile() {
        Properties properties = loadProperties("application-test.properties");

        assertFalse(properties.containsKey("app.person-ministry.read-source.reader"));
        assertFalse(properties.containsKey("app.person-ministry.read-source.commentator"));
        assertFalse(properties.containsKey("app.person-ministry.read-source.priest"));
        assertFalse(properties.containsKey("app.person-ministry.read-source.minister-of-the-word"));
    }

    @Test
    void shouldNotOverrideReaderReadSourceInMysqlProfile() {
        Properties properties = loadProperties("application-mysql.properties");

        assertFalse(properties.containsKey("app.person-ministry.read-source.reader"));
        assertFalse(properties.containsKey("app.person-ministry.read-source.commentator"));
        assertFalse(properties.containsKey("app.person-ministry.read-source.priest"));
        assertFalse(properties.containsKey("app.person-ministry.read-source.minister-of-the-word"));
    }

    @Test
    void shouldBindReaderReadSourceIgnoringCase() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.reader=parallel")
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getReader());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
                });
    }

    @Test
    void shouldBindCommentatorReadSourceIndependentlyFromReader() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.commentator=parallel")
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getReader());
                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
                });
    }

    @Test
    void shouldBindPriestReadSourceIndependentlyFromReaderAndCommentator() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.priest=parallel")
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getReader());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
                });
    }

    @Test
    void shouldBindMinisterOfTheWordReadSourceIndependentlyFromReaderCommentatorAndPriest() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.minister-of-the-word=parallel")
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getReader());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getMinisterOfTheWord());
                });
    }

    @Test
    void shouldBindReaderReadSourceIndependentlyFromCommentatorAndPriest() {
        contextRunner
                .withPropertyValues(
                        "app.person-ministry.read-source.reader=parallel",
                        "app.person-ministry.read-source.commentator=legacy",
                        "app.person-ministry.read-source.priest=legacy",
                        "app.person-ministry.read-source.minister-of-the-word=legacy"
                )
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getReader());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
                });
    }

    @Test
    void shouldFailContextWhenReaderReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.reader=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, "app.person-ministry.read-source"));
                    assertTrue(hasMessageContaining(failure, "invalid"));
                });
    }

    @Test
    void shouldFailContextWhenCommentatorReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.commentator=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, "app.person-ministry.read-source"));
                    assertTrue(hasMessageContaining(failure, "invalid"));
                });
    }

    @Test
    void shouldFailContextWhenPriestReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.priest=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, "app.person-ministry.read-source"));
                    assertTrue(hasMessageContaining(failure, "invalid"));
                });
    }

    @Test
    void shouldFailContextWhenMinisterOfTheWordReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.minister-of-the-word=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, "app.person-ministry.read-source"));
                    assertTrue(hasMessageContaining(failure, "invalid"));
                });
    }

    private boolean hasMessageContaining(Throwable throwable, String text) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Properties loadProperties(String resourceName) {
        Properties properties = new Properties();
        Path resourcePath = Path.of("src", "main", "resources", resourceName);
        try (InputStream inputStream = Files.newInputStream(resourcePath)) {
            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load resource: " + resourceName, exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PersonMinistryReadSourceProperties.class)
    static class Config {
    }
}
