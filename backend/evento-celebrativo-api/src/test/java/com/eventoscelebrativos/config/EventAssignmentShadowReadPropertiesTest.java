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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventAssignmentShadowReadPropertiesTest {

    private static final String PREFIX = "app.event-assignment.shadow-read.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void shouldKeepAllFlagsDisabledByDefault() {
        contextRunner.run(context -> {
            EventAssignmentShadowReadProperties properties =
                    context.getBean(EventAssignmentShadowReadProperties.class);

            assertFalse(properties.isEventDetailEnabled());
            assertFalse(properties.isEventScaleDetailEnabled());
            assertFalse(properties.isMonthlyScheduleEnabled());
            assertFalse(properties.isEucharistScaleEnabled());
        });
    }

    @Test
    void shouldDeclareBaseApplicationPropertiesWithFalseDefaultsAndEnvironmentOverrides() {
        Properties properties = loadProperties("application.properties");

        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EVENT_DETAIL_ENABLED:false}",
                properties.getProperty(PREFIX + "event-detail-enabled")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EVENT_SCALE_DETAIL_ENABLED:false}",
                properties.getProperty(PREFIX + "event-scale-detail-enabled")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_MONTHLY_SCHEDULE_ENABLED:false}",
                properties.getProperty(PREFIX + "monthly-schedule-enabled")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EUCHARIST_SCALE_ENABLED:false}",
                properties.getProperty(PREFIX + "eucharist-scale-enabled")
        );
    }

    @Test
    void shouldNotOverrideShadowReadFlagsInLocalProfile() {
        assertProfileDoesNotOverrideEventAssignmentShadowRead("application-local.properties");
    }

    @Test
    void shouldNotOverrideShadowReadFlagsInTestProfile() {
        assertProfileDoesNotOverrideEventAssignmentShadowRead("application-test.properties");
    }

    @Test
    void shouldNotOverrideShadowReadFlagsInMysqlProfile() {
        assertProfileDoesNotOverrideEventAssignmentShadowRead("application-mysql.properties");
    }

    @Test
    void shouldBindEventDetailIndependently() {
        contextRunner
                .withPropertyValues(PREFIX + "event-detail-enabled=true")
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertTrue(properties.isEventDetailEnabled());
                    assertFalse(properties.isEventScaleDetailEnabled());
                    assertFalse(properties.isMonthlyScheduleEnabled());
                    assertFalse(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldBindEventScaleDetailIndependently() {
        contextRunner
                .withPropertyValues(PREFIX + "event-scale-detail-enabled=true")
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertFalse(properties.isEventDetailEnabled());
                    assertTrue(properties.isEventScaleDetailEnabled());
                    assertFalse(properties.isMonthlyScheduleEnabled());
                    assertFalse(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldBindMonthlyScheduleIndependently() {
        contextRunner
                .withPropertyValues(PREFIX + "monthly-schedule-enabled=true")
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertFalse(properties.isEventDetailEnabled());
                    assertFalse(properties.isEventScaleDetailEnabled());
                    assertTrue(properties.isMonthlyScheduleEnabled());
                    assertFalse(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldBindEucharistScaleIndependently() {
        contextRunner
                .withPropertyValues(PREFIX + "eucharist-scale-enabled=true")
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertFalse(properties.isEventDetailEnabled());
                    assertFalse(properties.isEventScaleDetailEnabled());
                    assertFalse(properties.isMonthlyScheduleEnabled());
                    assertTrue(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldFailContextWhenBooleanValueIsInvalid() {
        contextRunner
                .withPropertyValues(PREFIX + "event-detail-enabled=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, PREFIX));
                    assertTrue(hasMessageContaining(failure, "invalid"));
                });
    }

    private void assertProfileDoesNotOverrideEventAssignmentShadowRead(String resourceName) {
        Properties properties = loadProperties(resourceName);

        assertFalse(properties.containsKey(PREFIX + "event-detail-enabled"));
        assertFalse(properties.containsKey(PREFIX + "event-scale-detail-enabled"));
        assertFalse(properties.containsKey(PREFIX + "monthly-schedule-enabled"));
        assertFalse(properties.containsKey(PREFIX + "eucharist-scale-enabled"));
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
    @EnableConfigurationProperties(EventAssignmentShadowReadProperties.class)
    static class Config {
    }
}
