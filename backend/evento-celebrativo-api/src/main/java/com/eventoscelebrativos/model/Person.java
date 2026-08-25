package com.eventoscelebrativos.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "tb_person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            updatable = false,
            length = 16
    )
    private UUID publicId = UUID.randomUUID();

    private String name;

    @Column(unique = true)
    private String phoneNumber;
    private LocalDate birthdayDate;

    @Column(nullable = false)
    private boolean active = true;

    protected Person(){

    }

    public Person(String name, String phoneNumber, LocalDate birthdayDate) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
    }

    public void updateCadastralData(
            String name,
            String phoneNumber,
            LocalDate birthdayDate
    ) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Person person)) {
            return false;
        }

        return publicId.equals(person.getPublicId());
    }

    @Override
    public int hashCode() {
        return publicId.hashCode();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public LocalDate getBirthdayDate() {
        return birthdayDate;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

}
