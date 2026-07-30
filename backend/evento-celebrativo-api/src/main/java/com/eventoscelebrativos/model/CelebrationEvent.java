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

    @Column(name = "end_at")
    private LocalDateTime endAt;

    private Boolean massOrCelebration;

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

}
