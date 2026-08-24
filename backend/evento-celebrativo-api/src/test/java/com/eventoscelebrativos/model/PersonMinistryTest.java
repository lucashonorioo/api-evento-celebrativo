package com.eventoscelebrativos.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        PersonMinistry ministry = new PersonMinistry(person(), MinistryType.READER);

        assertTrue(ministry.getActive());
        assertFalse(ministry.getCoordinator());
    }

    @Test
    void grantCoordinationShouldSetCoordinatorTrue() {
        PersonMinistry ministry = new PersonMinistry(person(), MinistryType.READER);

        ministry.grantCoordination();

        assertTrue(ministry.getCoordinator());
        assertTrue(ministry.getActive());
    }

    @Test
    void revokeCoordinationShouldSetCoordinatorFalseWithoutTouchingActive() {
        PersonMinistry ministry = new PersonMinistry(person(), MinistryType.READER);
        ministry.grantCoordination();

        ministry.revokeCoordination();

        assertFalse(ministry.getCoordinator());
        assertTrue(ministry.getActive());
    }

    @Test
    void deactivateShouldClearCoordinationAtomically() {
        PersonMinistry ministry = new PersonMinistry(person(), MinistryType.READER);
        ministry.grantCoordination();

        ministry.deactivate();

        assertFalse(ministry.getActive());
        assertFalse(ministry.getCoordinator());
    }

    @Test
    void activateShouldNeverRestoreCoordination() {
        PersonMinistry ministry = new PersonMinistry(person(), MinistryType.READER);
        ministry.grantCoordination();
        ministry.deactivate();

        ministry.activate();

        assertTrue(ministry.getActive());
        assertFalse(ministry.getCoordinator());
    }

    @Test
    void prePersistShouldDefaultNullCoordinatorToFalse() {
        PersonMinistry ministry = new PersonMinistry();
        ministry.setPerson(person());
        ministry.setMinistryType(MinistryType.READER);

        ministry.prePersist();

        assertFalse(ministry.getCoordinator());
    }
}
