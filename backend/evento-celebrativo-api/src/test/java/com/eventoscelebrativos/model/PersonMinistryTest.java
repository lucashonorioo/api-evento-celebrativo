package com.eventoscelebrativos.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre os invariantes estruturais de coordenacao diretamente na entidade: coordinator nunca fica
 * true quando active=false, desativacao sempre limpa a coordenacao, e reativacao nunca a restaura.
 */
class PersonMinistryTest {

    private Person person() {
        Person person = new Person("Pessoa", "34999999999", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(person, "id", 1L);
        return person;
    }

    @Test
    void shouldStartWithCoordinatorFalseWhenCreated() {
        PersonMinistry ministry = personMinistry(person(), MinistryType.READER);

        assertTrue(ministry.getActive());
        assertFalse(ministry.getCoordinator());
    }

    @Test
    void shouldKeepPersistentMinistryReferenceWhenCreated() {
        PersonMinistry ministry = personMinistry(person(), MinistryType.READER);

        assertNotNull(ministry.getMinistry());
        assertEquals(unitMinistry(MinistryType.READER).getId(), ministry.getMinistry().getId());
        assertEquals(MinistryType.READER, ministry.getMinistryType());
    }

    @Test
    void shouldRejectMissingMinistryWhenCreated() {
        assertThrows(NullPointerException.class,
                () -> new PersonMinistry(person(), null, MinistryType.READER));
    }

    @Test
    void grantCoordinationShouldSetCoordinatorTrue() {
        PersonMinistry ministry = personMinistry(person(), MinistryType.READER);

        ministry.grantCoordination();

        assertTrue(ministry.getCoordinator());
        assertTrue(ministry.getActive());
    }

    @Test
    void revokeCoordinationShouldSetCoordinatorFalseWithoutTouchingActive() {
        PersonMinistry ministry = personMinistry(person(), MinistryType.READER);
        ministry.grantCoordination();

        ministry.revokeCoordination();

        assertFalse(ministry.getCoordinator());
        assertTrue(ministry.getActive());
    }

    @Test
    void deactivateShouldClearCoordinationAtomically() {
        PersonMinistry ministry = personMinistry(person(), MinistryType.READER);
        ministry.grantCoordination();

        ministry.deactivate();

        assertFalse(ministry.getActive());
        assertFalse(ministry.getCoordinator());
    }

    @Test
    void activateShouldNeverRestoreCoordination() {
        PersonMinistry ministry = personMinistry(person(), MinistryType.READER);
        ministry.grantCoordination();
        ministry.deactivate();

        ministry.activate();

        assertTrue(ministry.getActive());
        assertFalse(ministry.getCoordinator());
    }

    @Test
    void prePersistShouldDefaultNullCoordinatorToFalse() {
        PersonMinistry ministry = personMinistry(person(), MinistryType.READER);
        ReflectionTestUtils.setField(ministry, "coordinator", null);

        ministry.prePersist();

        assertFalse(ministry.getCoordinator());
    }
}
