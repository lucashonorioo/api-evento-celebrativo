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
    void differentNewPeopleShouldNotBeEqual() {
        Person first = person("Pessoa Um", "34999999998");
        Person second = person("Pessoa Dois", "34999999997");

        assertNotEquals(first, second);
    }

    @Test
    void changingPhoneNumberShouldNotChangeHashCode() {
        Person person = person("Pessoa Teste", "34999999999");

        int hashBeforeChange = person.hashCode();

        person.setPhoneNumber("34888888888");

        int hashAfterChange = person.hashCode();

        assertEquals(hashBeforeChange, hashAfterChange);
    }

    @Test
    void changingPhoneNumberShouldNotBreakHashSetMembership() {
        Person person = person("Pessoa Teste", "34999999999");

        Set<Person> people = new HashSet<>();
        people.add(person);

        person.setPhoneNumber("34888888888");

        assertTrue(people.contains(person));
    }

    private Person person(String name, String phoneNumber) {
        return new Person(name, phoneNumber, BIRTHDAY);
    }
}
