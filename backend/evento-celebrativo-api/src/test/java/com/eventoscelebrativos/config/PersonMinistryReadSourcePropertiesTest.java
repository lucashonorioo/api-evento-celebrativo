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

class PersonMinistryReadSourcePropertiesTest {

    private static final String PREFIX = "app.person-ministry.read-source.";
    private static final String SHADOW_PREFIX = "app.person-ministry.shadow-read.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    private final ApplicationContextRunner applicationPropertiesContextRunner = contextRunner
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void shouldUseLegacyAsDefaultReaderReadSource() {
        contextRunner.run(context -> {
            PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

            assertEquals(PersonMinistryReadSource.LEGACY, properties.getReader());
            assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
            assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
            assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
            assertEquals(PersonMinistryReadSource.LEGACY, properties.getEucharisticMinister());
        });
    }

    @Test
    void shouldKeepBaseApplicationReaderReadSourceAsLegacy() {
        Properties properties = loadProperties("application.properties");

        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_READER:LEGACY}",
                properties.getProperty(PREFIX + "reader")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_COMMENTATOR:LEGACY}",
                properties.getProperty(PREFIX + "commentator")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_PRIEST:LEGACY}",
                properties.getProperty(PREFIX + "priest")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD:LEGACY}",
                properties.getProperty(PREFIX + "minister-of-the-word")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER:LEGACY}",
                properties.getProperty(PREFIX + "eucharistic-minister")
        );
    }

    @Test
    void shouldBindReadSourcesAsLegacyInBaseApplicationProperties() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.config.location=file:src/main/resources/application.properties",
                        "spring.profiles.active="
                )
                .run(context -> assertPersonMinistryReadSources(
                        context.getBean(PersonMinistryReadSourceProperties.class),
                        PersonMinistryReadSource.LEGACY,
                        PersonMinistryReadSource.LEGACY,
                        PersonMinistryReadSource.LEGACY,
                        PersonMinistryReadSource.LEGACY,
                        PersonMinistryReadSource.LEGACY
                ));
    }

    @Test
    void shouldEnableParallelReaderReadSourceOnlyInLocalProfileWithEnvironmentOverride() {
        Properties properties = loadProperties("application-local.properties");

        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_READER:PARALLEL}",
                properties.getProperty(PREFIX + "reader")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_COMMENTATOR:PARALLEL}",
                properties.getProperty(PREFIX + "commentator")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_PRIEST:PARALLEL}",
                properties.getProperty(PREFIX + "priest")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD:PARALLEL}",
                properties.getProperty(PREFIX + "minister-of-the-word")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER:PARALLEL}",
                properties.getProperty(PREFIX + "eucharistic-minister")
        );
    }

    @Test
    void shouldBindReadSourcesAsParallelInLocalProfile() {
        applicationPropertiesContextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> assertPersonMinistryReadSources(
                        context.getBean(PersonMinistryReadSourceProperties.class),
                        PersonMinistryReadSource.PARALLEL,
                        PersonMinistryReadSource.PARALLEL,
                        PersonMinistryReadSource.PARALLEL,
                        PersonMinistryReadSource.PARALLEL,
                        PersonMinistryReadSource.PARALLEL
                ));
    }

    @Test
    void shouldDeclareTestApplicationPropertiesWithParallelDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application-test.properties");

        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_READER:PARALLEL}",
                properties.getProperty(PREFIX + "reader")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_COMMENTATOR:PARALLEL}",
                properties.getProperty(PREFIX + "commentator")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_PRIEST:PARALLEL}",
                properties.getProperty(PREFIX + "priest")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD:PARALLEL}",
                properties.getProperty(PREFIX + "minister-of-the-word")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER:PARALLEL}",
                properties.getProperty(PREFIX + "eucharistic-minister")
        );
    }

    @Test
    void shouldBindReadSourcesAsParallelInTestProfileWithoutChangingEventAssignmentSources() {
        applicationPropertiesContextRunner
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );

                    EventAssignmentReadSourceProperties eventAssignmentProperties =
                            context.getBean(EventAssignmentReadSourceProperties.class);
                    assertEquals(EventAssignmentReadSource.PARALLEL, eventAssignmentProperties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, eventAssignmentProperties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, eventAssignmentProperties.getMonthlySchedule());
                });
    }

    @Test
    void shouldDeclareMysqlApplicationPropertiesWithParallelDefaultAndEnvironmentOverride() {
        Properties properties = loadProperties("application-mysql.properties");

        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_READER:PARALLEL}",
                properties.getProperty(PREFIX + "reader")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_COMMENTATOR:PARALLEL}",
                properties.getProperty(PREFIX + "commentator")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_PRIEST:PARALLEL}",
                properties.getProperty(PREFIX + "priest")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD:PARALLEL}",
                properties.getProperty(PREFIX + "minister-of-the-word")
        );
        assertEquals(
                "${PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER:PARALLEL}",
                properties.getProperty(PREFIX + "eucharistic-minister")
        );
    }

    @Test
    void shouldBindReadSourcesAsParallelInMysqlProfileWithoutChangingEventAssignmentSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );

                    EventAssignmentReadSourceProperties eventAssignmentProperties =
                            context.getBean(EventAssignmentReadSourceProperties.class);
                    assertEquals(EventAssignmentReadSource.PARALLEL, eventAssignmentProperties.getEventScaleDetail());
                    assertEquals(EventAssignmentReadSource.PARALLEL, eventAssignmentProperties.getEucharistScale());
                    assertEquals(EventAssignmentReadSource.PARALLEL, eventAssignmentProperties.getMonthlySchedule());
                });
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
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getEucharisticMinister());
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
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getEucharisticMinister());
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
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getEucharisticMinister());
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
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getEucharisticMinister());
                });
    }

    @Test
    void shouldBindEucharisticMinisterReadSourceIndependentlyFromOtherCategories() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.eucharistic-minister=parallel")
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getReader());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getEucharisticMinister());
                });
    }

    @Test
    void shouldBindReaderReadSourceIndependentlyFromCommentatorAndPriest() {
        contextRunner
                .withPropertyValues(
                        "app.person-ministry.read-source.reader=parallel",
                        "app.person-ministry.read-source.commentator=legacy",
                        "app.person-ministry.read-source.priest=legacy",
                        "app.person-ministry.read-source.minister-of-the-word=legacy",
                        "app.person-ministry.read-source.eucharistic-minister=legacy"
                )
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getReader());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getMinisterOfTheWord());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getEucharisticMinister());
                });
    }

    @Test
    void shouldAllowLocalEucharisticMinisterRollbackWithoutChangingOtherLocalSources() {
        contextRunner
                .withPropertyValues(
                        "app.person-ministry.read-source.reader=parallel",
                        "app.person-ministry.read-source.commentator=parallel",
                        "app.person-ministry.read-source.priest=parallel",
                        "app.person-ministry.read-source.minister-of-the-word=parallel",
                        "app.person-ministry.read-source.eucharistic-minister=legacy"
                )
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getReader());
                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getCommentator());
                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getPriest());
                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getMinisterOfTheWord());
                    assertEquals(PersonMinistryReadSource.LEGACY, properties.getEucharisticMinister());
                });
    }

    @Test
    void shouldAllowTestReaderRollbackWithoutChangingOtherTestSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "PERSON_MINISTRY_READ_SOURCE_READER=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowTestCommentatorRollbackWithoutChangingOtherTestSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "PERSON_MINISTRY_READ_SOURCE_COMMENTATOR=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowTestPriestRollbackWithoutChangingOtherTestSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "PERSON_MINISTRY_READ_SOURCE_PRIEST=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowTestMinisterOfTheWordRollbackWithoutChangingOtherTestSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowTestEucharisticMinisterRollbackWithoutChangingOtherTestSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowMysqlReaderRollbackWithoutChangingOtherMysqlSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "PERSON_MINISTRY_READ_SOURCE_READER=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowMysqlCommentatorRollbackWithoutChangingOtherMysqlSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "PERSON_MINISTRY_READ_SOURCE_COMMENTATOR=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowMysqlPriestRollbackWithoutChangingOtherMysqlSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "PERSON_MINISTRY_READ_SOURCE_PRIEST=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowMysqlMinisterOfTheWordRollbackWithoutChangingOtherMysqlSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY,
                            PersonMinistryReadSource.PARALLEL
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowMysqlEucharisticMinisterRollbackWithoutChangingOtherMysqlSourcesOrShadowReadFlags() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        "PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER=LEGACY"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.LEGACY
                    );
                    assertAllPersonMinistryShadowReadFlagsDisabled(context.getBean(PersonMinistryShadowReadProperties.class));
                });
    }

    @Test
    void shouldAllowMysqlShadowReadOverrideWithoutChangingReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=mysql",
                        "MYSQL_DATASOURCE_URL=jdbc:mysql://localhost:3307/evento_celeb_test",
                        "MYSQL_DATASOURCE_USERNAME=test",
                        "MYSQL_DATASOURCE_PASSWORD=test",
                        SHADOW_PREFIX + "reader-enabled=true"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );

                    PersonMinistryShadowReadProperties shadowProperties =
                            context.getBean(PersonMinistryShadowReadProperties.class);
                    assertTrue(shadowProperties.isReaderEnabled());
                    assertFalse(shadowProperties.isCommentatorEnabled());
                    assertFalse(shadowProperties.isPriestEnabled());
                    assertFalse(shadowProperties.isMinisterOfTheWordEnabled());
                    assertFalse(shadowProperties.isEucharisticMinisterEnabled());
                });
    }

    @Test
    void shouldAllowTestShadowReadOverrideWithoutChangingReadSources() {
        applicationPropertiesContextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        SHADOW_PREFIX + "reader-enabled=true"
                )
                .run(context -> {
                    assertPersonMinistryReadSources(
                            context.getBean(PersonMinistryReadSourceProperties.class),
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL,
                            PersonMinistryReadSource.PARALLEL
                    );

                    PersonMinistryShadowReadProperties shadowProperties =
                            context.getBean(PersonMinistryShadowReadProperties.class);
                    assertTrue(shadowProperties.isReaderEnabled());
                    assertFalse(shadowProperties.isCommentatorEnabled());
                    assertFalse(shadowProperties.isPriestEnabled());
                    assertFalse(shadowProperties.isMinisterOfTheWordEnabled());
                    assertFalse(shadowProperties.isEucharisticMinisterEnabled());
                });
    }

    @Test
    void shouldKeepShadowPropertiesDeclaredUnderTheirOwnPrefix() {
        Properties properties = loadProperties("application.properties");

        assertEquals(
                "${PERSON_MINISTRY_SHADOW_READ_READER_ENABLED:false}",
                properties.getProperty(SHADOW_PREFIX + "reader-enabled")
        );
        assertEquals(
                "${PERSON_MINISTRY_SHADOW_READ_EUCHARISTIC_MINISTER_ENABLED:false}",
                properties.getProperty(SHADOW_PREFIX + "eucharistic-minister-enabled")
        );
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

    @Test
    void shouldFailContextWhenEucharisticMinisterReadSourceIsInvalid() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.eucharistic-minister=invalid")
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

    private void assertPersonMinistryReadSources(
            PersonMinistryReadSourceProperties properties,
            PersonMinistryReadSource reader,
            PersonMinistryReadSource commentator,
            PersonMinistryReadSource priest,
            PersonMinistryReadSource ministerOfTheWord,
            PersonMinistryReadSource eucharisticMinister
    ) {
        assertEquals(reader, properties.getReader());
        assertEquals(commentator, properties.getCommentator());
        assertEquals(priest, properties.getPriest());
        assertEquals(ministerOfTheWord, properties.getMinisterOfTheWord());
        assertEquals(eucharisticMinister, properties.getEucharisticMinister());
    }

    private void assertAllPersonMinistryShadowReadFlagsDisabled(PersonMinistryShadowReadProperties properties) {
        assertFalse(properties.isReaderEnabled());
        assertFalse(properties.isCommentatorEnabled());
        assertFalse(properties.isPriestEnabled());
        assertFalse(properties.isMinisterOfTheWordEnabled());
        assertFalse(properties.isEucharisticMinisterEnabled());
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
            PersonMinistryReadSourceProperties.class,
            PersonMinistryShadowReadProperties.class,
            EventAssignmentReadSourceProperties.class,
            EventAssignmentShadowReadProperties.class
    })
    static class Config {
    }
}
