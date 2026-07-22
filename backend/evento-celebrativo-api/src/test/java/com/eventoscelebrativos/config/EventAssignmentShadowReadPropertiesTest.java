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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventAssignmentShadowReadPropertiesTest {

    private static final String PREFIX = "app.event-assignment.shadow-read.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    private final ApplicationContextRunner applicationPropertiesContextRunner = contextRunner
            .withInitializer(new ConfigDataApplicationContextInitializer());

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
    void shouldEnableAllFlagsInLocalProfile() {
        Properties properties = loadProperties("application-local.properties");

        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EVENT_DETAIL_ENABLED:true}",
                properties.getProperty(PREFIX + "event-detail-enabled")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EVENT_SCALE_DETAIL_ENABLED:true}",
                properties.getProperty(PREFIX + "event-scale-detail-enabled")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_MONTHLY_SCHEDULE_ENABLED:true}",
                properties.getProperty(PREFIX + "monthly-schedule-enabled")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_SHADOW_READ_EUCHARIST_SCALE_ENABLED:true}",
                properties.getProperty(PREFIX + "eucharist-scale-enabled")
        );
    }

    @Test
    void shouldBindAllFlagsEnabledInLocalProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertTrue(properties.isEventDetailEnabled());
                    assertTrue(properties.isEventScaleDetailEnabled());
                    assertTrue(properties.isMonthlyScheduleEnabled());
                    assertTrue(properties.isEucharistScaleEnabled());
                });
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
    void shouldBindAllFlagsDisabledInTestProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertFalse(properties.isEventDetailEnabled());
                    assertFalse(properties.isEventScaleDetailEnabled());
                    assertFalse(properties.isMonthlyScheduleEnabled());
                    assertFalse(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldBindAllFlagsDisabledInMysqlProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test"
                )
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertFalse(properties.isEventDetailEnabled());
                    assertFalse(properties.isEventScaleDetailEnabled());
                    assertFalse(properties.isMonthlyScheduleEnabled());
                    assertFalse(properties.isEucharistScaleEnabled());
                });
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
    void shouldAllowLocalEventDetailRollbackWithoutChangingOtherFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_SHADOW_READ_EVENT_DETAIL_ENABLED=false"
                )
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertFalse(properties.isEventDetailEnabled());
                    assertTrue(properties.isEventScaleDetailEnabled());
                    assertTrue(properties.isMonthlyScheduleEnabled());
                    assertTrue(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldAllowLocalEventScaleDetailRollbackWithoutChangingOtherFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_SHADOW_READ_EVENT_SCALE_DETAIL_ENABLED=false"
                )
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertTrue(properties.isEventDetailEnabled());
                    assertFalse(properties.isEventScaleDetailEnabled());
                    assertTrue(properties.isMonthlyScheduleEnabled());
                    assertTrue(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldAllowLocalMonthlyScheduleRollbackWithoutChangingOtherFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_SHADOW_READ_MONTHLY_SCHEDULE_ENABLED=false"
                )
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertTrue(properties.isEventDetailEnabled());
                    assertTrue(properties.isEventScaleDetailEnabled());
                    assertFalse(properties.isMonthlyScheduleEnabled());
                    assertTrue(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldAllowLocalEucharistScaleRollbackWithoutChangingOtherFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_SHADOW_READ_EUCHARIST_SCALE_ENABLED=false"
                )
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertTrue(properties.isEventDetailEnabled());
                    assertTrue(properties.isEventScaleDetailEnabled());
                    assertTrue(properties.isMonthlyScheduleEnabled());
                    assertFalse(properties.isEucharistScaleEnabled());
                });
    }

    @Test
    void shouldAllowLocalRollbackForAllFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_SHADOW_READ_EVENT_DETAIL_ENABLED=false",
                        "EVENT_ASSIGNMENT_SHADOW_READ_EVENT_SCALE_DETAIL_ENABLED=false",
                        "EVENT_ASSIGNMENT_SHADOW_READ_MONTHLY_SCHEDULE_ENABLED=false",
                        "EVENT_ASSIGNMENT_SHADOW_READ_EUCHARIST_SCALE_ENABLED=false"
                )
                .run(context -> {
                    EventAssignmentShadowReadProperties properties =
                            context.getBean(EventAssignmentShadowReadProperties.class);

                    assertFalse(properties.isEventDetailEnabled());
                    assertFalse(properties.isEventScaleDetailEnabled());
                    assertFalse(properties.isMonthlyScheduleEnabled());
                    assertFalse(properties.isEucharistScaleEnabled());
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
