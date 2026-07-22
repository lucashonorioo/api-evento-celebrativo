package com.eventoscelebrativos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
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

class EventAssignmentReadSourcePropertiesTest {

    private static final String PREFIX = "app.event-assignment.read-source.";
    private static final String SHADOW_PREFIX = "app.event-assignment.shadow-read.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    private final ApplicationContextRunner applicationPropertiesContextRunner = contextRunner
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void shouldUseLegacyAsDefaultReadSources() {
        contextRunner.run(context -> {
            EventAssignmentReadSourceProperties properties =
                    context.getBean(EventAssignmentReadSourceProperties.class);

            assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
        });
    }

    @Test
    void shouldDeclareBaseApplicationPropertiesWithLegacyDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application.properties");

        assertFalse(properties.containsKey(PREFIX + "event-detail"));
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL:LEGACY}",
                properties.getProperty(PREFIX + "event-scale-detail")
        );
    }

    @Test
    void shouldDeclareLocalApplicationPropertiesWithParallelDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application-local.properties");

        assertFalse(properties.containsKey(PREFIX + "event-detail"));
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL:PARALLEL}",
                properties.getProperty(PREFIX + "event-scale-detail")
        );
    }

    @Test
    void shouldBindReadSourcesAsParallelInLocalProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                });
    }

    @Test
    void shouldBindReadSourcesAsLegacyInTestProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                });
    }

    @Test
    void shouldBindReadSourcesAsLegacyInMysqlProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                });
    }

    @Test
    void shouldNotOverrideReadSourcesInTestProfile() {
        Properties properties = loadProperties("application-test.properties");

        assertFalse(properties.containsKey(PREFIX + "event-scale-detail"));
    }

    @Test
    void shouldNotOverrideReadSourcesInMysqlProfile() {
        Properties properties = loadProperties("application-mysql.properties");

        assertFalse(properties.containsKey(PREFIX + "event-scale-detail"));
    }

    @Test
    void shouldAllowLocalEventScaleDetailRollbackWithoutChangingShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);
                    EventAssignmentShadowReadProperties shadowProperties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                    assertTrue(shadowProperties.isEventDetailEnabled());
                    assertTrue(shadowProperties.isEventScaleDetailEnabled());
                    assertTrue(shadowProperties.isMonthlyScheduleEnabled());
                    assertTrue(shadowProperties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldAllowShadowReadRollbackWithoutChangingLocalReadSource() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_SHADOW_READ_EVENT_SCALE_DETAIL_ENABLED=false"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);
                    EventAssignmentShadowReadProperties shadowProperties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertFalse(shadowProperties.isEventScaleDetailEnabled());
                    assertTrue(shadowProperties.isEventDetailEnabled());
                    assertTrue(shadowProperties.isMonthlyScheduleEnabled());
                    assertTrue(shadowProperties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldBindReadSourcesIgnoringCase() {
        contextRunner
                .withPropertyValues(PREFIX + "event-scale-detail=parallel")
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                });
    }

    @Test
    void shouldFailContextWhenEventScaleDetailReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues(PREFIX + "event-scale-detail=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, "app.event-assignment.read-source"));
                    assertTrue(hasMessageContaining(failure, "invalid"));
                });
    }

    @Test
    void shouldKeepShadowPropertiesDeclaredUnderTheirOwnPrefix() {
        Properties properties = loadProperties("application.properties");

        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EVENT_DETAIL_ENABLED:false}",
                properties.getProperty(SHADOW_PREFIX + "event-detail-enabled")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EVENT_SCALE_DETAIL_ENABLED:false}",
                properties.getProperty(SHADOW_PREFIX + "event-scale-detail-enabled")
        );
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
    @EnableConfigurationProperties({
            EventAssignmentReadSourceProperties.class,
            EventAssignmentShadowReadProperties.class
    })
    static class Config {
    }
}
