package com.eventoscelebrativos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Responsabilidade institucional paroquial (PASTOR, PARISH_SECRETARY) atribuida a uma Person.
 * Separada de PersonMinistry, UserAccount/UserAccountRole, EventAssignment e ParishProfile. Uma
 * linha por Person + responsibility: desativacao nunca remove a linha fisicamente, apenas marca
 * active=false; reativacao reutiliza a mesma linha (createdAt preservado).
 */
@Entity
@Table(name = "tb_parish_staff_assignment")
public class ParishStaffAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility", nullable = false, length = 50)
    private ParishResponsibilityType responsibility;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ParishStaffAssignment() {
    }

    public ParishStaffAssignment(Person person, ParishResponsibilityType responsibility) {
        this.person = person;
        this.responsibility = responsibility;
        this.active = true;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public ParishResponsibilityType getResponsibility() {
        return responsibility;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
