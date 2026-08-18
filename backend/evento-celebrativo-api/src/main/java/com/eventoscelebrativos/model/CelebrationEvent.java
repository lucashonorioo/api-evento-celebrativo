package com.eventoscelebrativos.model;



import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "tb_celebration_event")
public class CelebrationEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nameMassOrEvent;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /**
     * Termino previsto do compromisso para planejamento de disponibilidade e escala.
     * Nao representa o termino real da celebracao.
     */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    private Boolean massOrCelebration;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CelebrationEventStatus status = CelebrationEventStatus.ACTIVE;

    @ManyToMany
    @JoinTable(
            name = "tb_event_location",
            joinColumns = @JoinColumn(name = "event_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "location_id", referencedColumnName = "id")
    )
    List<Location> locations = new ArrayList<>();

    public CelebrationEvent(){

    }

    public CelebrationEvent(Long id, String nameMassOrEvent, LocalDateTime startAt, LocalDateTime endAt, Boolean massOrCelebration) {
        this.id = id;
        this.nameMassOrEvent = nameMassOrEvent;
        this.startAt = startAt;
        this.endAt = endAt;
        this.massOrCelebration = massOrCelebration;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CelebrationEvent that = (CelebrationEvent) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNameMassOrEvent() {
        return nameMassOrEvent;
    }

    public void setNameMassOrEvent(String nameMassOrEvent) {
        this.nameMassOrEvent = nameMassOrEvent;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }

    public void setMassOrCelebration(Boolean massOrCelebration) {
        this.massOrCelebration = massOrCelebration;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public CelebrationEventStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == CelebrationEventStatus.ACTIVE;
    }

    public boolean isCancelled() {
        return status == CelebrationEventStatus.CANCELLED;
    }

    /**
     * Transicao de dominio ACTIVE -&gt; CANCELLED. Nao apaga escala, assignments nem participation
     * responses; a validade temporal da transicao e responsabilidade do caller (lifecycle service).
     */
    public void cancel() {
        this.status = CelebrationEventStatus.CANCELLED;
    }

    /**
     * Transicao de dominio CANCELLED -&gt; ACTIVE. A elegibilidade da escala atual (Person/PersonMinistry
     * ainda ativos) e responsabilidade do caller (lifecycle service), nao desta entidade.
     */
    public void reactivate() {
        this.status = CelebrationEventStatus.ACTIVE;
    }

}
