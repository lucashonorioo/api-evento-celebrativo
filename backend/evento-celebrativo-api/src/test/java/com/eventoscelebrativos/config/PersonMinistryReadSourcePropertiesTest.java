package com.eventoscelebrativos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        });
    }

    @Test
    void shouldBindReaderReadSourceIgnoringCase() {
        contextRunner
                .withPropertyValues("app.person-ministry.read-source.reader=parallel")
                .run(context -> {
                    PersonMinistryReadSourceProperties properties = context.getBean(PersonMinistryReadSourceProperties.class);

                    assertEquals(PersonMinistryReadSource.PARALLEL, properties.getReader());
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PersonMinistryReadSourceProperties.class)
    static class Config {
    }
}
