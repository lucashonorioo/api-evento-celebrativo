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
            assertEquals(EventAssignmentReadSource.LEGACY, properties.getEucharistScale());
            assertEquals(EventAssignmentReadSource.LEGACY, properties.getMonthlySchedule());
        });
    }

    @Test
    void shouldDeclareBaseApplicationPropertiesWithParallelDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application.properties");

        assertFalse(properties.containsKey(PREFIX + "event-detail"));
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL:PARALLEL}",
                properties.getProperty(PREFIX + "event-scale-detail")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE:PARALLEL}",
                properties.getProperty(PREFIX + "eucharist-scale")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE:PARALLEL}",
                properties.getProperty(PREFIX + "monthly-schedule")
        );
    }

    @Test
    void shouldBindReadSourcesAsParallelInBaseApplicationProperties() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.config.location=file:src/main/resources/application.properties",
                        "spring.profiles.active="
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldDeclareLocalApplicationPropertiesWithParallelDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application-local.properties");

        assertFalse(properties.containsKey(PREFIX + "event-detail"));
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL:PARALLEL}",
                properties.getProperty(PREFIX + "event-scale-detail")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE:PARALLEL}",
                properties.getProperty(PREFIX + "eucharist-scale")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE:PARALLEL}",
                properties.getProperty(PREFIX + "monthly-schedule")
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
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldBindReadSourcesAsParallelInTestProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldBindReadSourcesAsParallelInMysqlProfile() {
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

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldDeclareTestApplicationPropertiesWithParallelDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application-test.properties");

        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL:PARALLEL}",
                properties.getProperty(PREFIX + "event-scale-detail")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE:PARALLEL}",
                properties.getProperty(PREFIX + "eucharist-scale")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE:PARALLEL}",
                properties.getProperty(PREFIX + "monthly-schedule")
        );
    }

    @Test
    void shouldDeclareMysqlApplicationPropertiesWithParallelDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application-mysql.properties");

        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL:PARALLEL}",
                properties.getProperty(PREFIX + "event-scale-detail")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE:PARALLEL}",
                properties.getProperty(PREFIX + "eucharist-scale")
        );
        assertEquals(
                "${EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE:PARALLEL}",
                properties.getProperty(PREFIX + "monthly-schedule")
        );
    }

    @Test
    void shouldAllowLocalEventScaleDetailRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowLocalEucharistScaleRollbackWithoutChangingEventScaleDetail() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowLocalMonthlyScheduleRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowMysqlEventScaleDetailRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowTestEventScaleDetailRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowTestEucharistScaleRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowTestMonthlyScheduleRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowMysqlEucharistScaleRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldAllowMysqlMonthlyScheduleRollbackWithoutChangingOtherReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE=LEGACY"
                )
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getMonthlySchedule());
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
            assertEquals(EventAssignmentReadSource.LEGACY, properties.getEucharistScale());
            assertEquals(EventAssignmentReadSource.LEGACY, properties.getMonthlySchedule());
        });
    }

    @Test
    void shouldBindEucharistScaleReadSourceIgnoringCase() {
        contextRunner
                .withPropertyValues(PREFIX + "eucharist-scale=parallel")
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getMonthlySchedule());
                });
    }

    @Test
    void shouldBindMonthlyScheduleReadSourceIgnoringCase() {
        contextRunner
                .withPropertyValues(PREFIX + "monthly-schedule=parallel")
                .run(context -> {
                    EventAssignmentReadSourceProperties properties =
                            context.getBean(EventAssignmentReadSourceProperties.class);

                    assertEquals(EventAssignmentReadSource.PARALLEL, properties.getMonthlySchedule());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.LEGACY, properties.getEucharistScale());
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
    void shouldFailContextWhenMonthlyScheduleReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues(PREFIX + "monthly-schedule=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, "app.event-assignment.read-source"));
                    assertTrue(hasMessageContaining(failure, "invalid"));
                });
    }

    @Test
    void shouldFailContextWhenEucharistScaleReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues(PREFIX + "eucharist-scale=invalid")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();

                    assertNotNull(failure);
                    assertTrue(hasMessageContaining(failure, "app.event-assignment.read-source"));
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
    @EnableConfigurationProperties({
            EventAssignmentReadSourceProperties.class
    })
    static class Config {
    }
}
