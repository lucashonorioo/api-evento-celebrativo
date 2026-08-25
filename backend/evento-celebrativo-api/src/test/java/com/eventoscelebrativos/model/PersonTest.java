package com.eventoscelebrativos.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersonTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 1);

    @Test
    void shouldGeneratePublicIdWhenCreated() {
        Person person = person("Pessoa Teste", "34999999999");

        assertNotNull(person.getPublicId());
    }

    @Test
    void shouldBeEqualToItself() {
        Person person = person("Pessoa Teste", "34999999999");

        assertEquals(person, person);
    }

    @Test
    void shouldNotBeEqualToNull() {
        Person person = person("Pessoa Teste", "34999999999");

        assertNotEquals(person, null);
    }

    @Test
    void shouldUpdateCadastralData() {
        Person person = new Person(
                "Alice",
                "34999999991",
                LocalDate.of(1990, 1, 10)
        );

        LocalDate newBirthdayDate = LocalDate.of(1991, 5, 20);

        person.updateCadastralData(
                "Maria",
                "34888888888",
                newBirthdayDate
        );

        assertEquals("Maria", person.getName());
        assertEquals("34888888888", person.getPhoneNumber());
        assertEquals(newBirthdayDate, person.getBirthdayDate());
    }

    @Test
    void shouldUpdateCadastralDataWithoutChangingPublicId() {
        Person person = new Person(
                "Alice",
                "34999999991",
                LocalDate.of(1990, 1, 10)
        );

        UUID publicIdBeforeUpdate = person.getPublicId();
        LocalDate newBirthdayDate = LocalDate.of(1991, 5, 20);

        person.updateCadastralData(
                "Maria",
                "34888888888",
                newBirthdayDate
        );

        assertEquals("Maria", person.getName());
        assertEquals("34888888888", person.getPhoneNumber());
        assertEquals(newBirthdayDate, person.getBirthdayDate());
        assertEquals(publicIdBeforeUpdate, person.getPublicId());
    }

    @Test
    void newPersonShouldStartActive() {
        Person person = person("Pessoa Ativa", "34999999996");

        assertTrue(person.isActive());
    }

    @Test
    void shouldDeactivatePerson() {
        Person person = person("Pessoa Inativada", "34999999995");

        person.deactivate();

        assertFalse(person.isActive());
    }

    @Test
    void shouldActivatePersonAgain() {
        Person person = person("Pessoa Reativada", "34999999994");

        person.deactivate();
        person.activate();

        assertTrue(person.isActive());
    }

    @Test
    void deactivatingInactivePersonShouldKeepItInactive() {
        Person person = person("Pessoa Inativa", "34999999993");

        person.deactivate();
        person.deactivate();

        assertFalse(person.isActive());
    }

    @Test
    void activatingActivePersonShouldKeepItActive() {
        Person person = person("Pessoa Ja Ativa", "34999999992");

        person.activate();

        assertTrue(person.isActive());
    }

    @Test
    void differentNewPeopleShouldNotBeEqual() {
        Person first = person("Pessoa Um", "34999999998");
        Person second = person("Pessoa Dois", "34999999997");

        assertNotEquals(first, second);
    }

    @Test
    void changingPhoneNumberShouldNotChangeHashCode() {
        Person person = person("Pessoa Teste", "34999999999");

        int hashBeforeChange = person.hashCode();

        person.updateCadastralData(
                person.getName(),
                "34888888888",
                person.getBirthdayDate()
        );

        int hashAfterChange = person.hashCode();

        assertEquals(hashBeforeChange, hashAfterChange);
    }

    @Test
    void changingPhoneNumberShouldNotBreakHashSetMembership() {
        Person person = person("Pessoa Teste", "34999999999");

        Set<Person> people = new HashSet<>();
        people.add(person);

        person.updateCadastralData(
                person.getName(),
                "34888888888",
                person.getBirthdayDate()
        );

        assertTrue(people.contains(person));
    }

    private Person person(String name, String phoneNumber) {
        return new Person(name, phoneNumber, BIRTHDAY);
    }
}
