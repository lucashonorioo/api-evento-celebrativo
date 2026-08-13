package com.eventoscelebrativos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Representa o perfil institucional unico da paroquia desta instalacao (uma instalacao = uma
 * paroquia, sem multi-tenancy). Singleton real: a unica linha valida tem {@code id = SINGLETON_ID},
 * garantido pela constraint {@code chk_tb_parish_profile_singleton_id} criada em V19. Nao usa
 * {@code @GeneratedValue}: a linha id=1 ja existe desde a migration, e a aplicacao sempre carrega e
 * atualiza essa linha, nunca insere uma nova.
 */
@Entity
@Table(name = "tb_parish_profile")
public class ParishProfile {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "configured", nullable = false)
    private boolean configured;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "diocese", length = 150)
    private String diocese;

    @Column(name = "institutional_phone", length = 30)
    private String institutionalPhone;

    @Column(name = "institutional_email", length = 254)
    private String institutionalEmail;

    @Column(name = "office_address", length = 255)
    private String officeAddress;

    @Column(name = "office_hours", length = 500)
    private String officeHours;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ParishProfile() {
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

    public Long getId() {
        return id;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDiocese() {
        return diocese;
    }

    public void setDiocese(String diocese) {
        this.diocese = diocese;
    }

    public String getInstitutionalPhone() {
        return institutionalPhone;
    }

    public void setInstitutionalPhone(String institutionalPhone) {
        this.institutionalPhone = institutionalPhone;
    }

    public String getInstitutionalEmail() {
        return institutionalEmail;
    }

    public void setInstitutionalEmail(String institutionalEmail) {
        this.institutionalEmail = institutionalEmail;
    }

    public String getOfficeAddress() {
        return officeAddress;
    }

    public void setOfficeAddress(String officeAddress) {
        this.officeAddress = officeAddress;
    }

    public String getOfficeHours() {
        return officeHours;
    }

    public void setOfficeHours(String officeHours) {
        this.officeHours = officeHours;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
