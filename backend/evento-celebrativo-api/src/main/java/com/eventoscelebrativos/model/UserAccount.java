package com.eventoscelebrativos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Espelho de acesso de uma {@link Person}, mantido sincronizado pelos fluxos legados.
 * Nao e usado como principal de autenticacao nesta etapa (ver feature/user-account-authentication-cutover).
 */
@Entity
@Table(name = "tb_user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "userAccount", fetch = FetchType.LAZY)
    private Set<UserAccountRole> roles = new HashSet<>();

    protected UserAccount() {
    }

    public UserAccount(Person person, String username, String passwordHash, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.person = person;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable(LocalDateTime updatedAt) {
        this.enabled = true;
        this.updatedAt = updatedAt;
    }

    public void disable(LocalDateTime updatedAt) {
        if (this.enabled) {
            incrementTokenVersion();
        }
        this.enabled = false;
        this.updatedAt = updatedAt;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<UserAccountRole> getRoles() {
        return roles;
    }
}
