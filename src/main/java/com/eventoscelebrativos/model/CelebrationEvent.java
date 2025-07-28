package com.eventoscelebrativos.model;



import com.eventoscelebrativos.model.serializer.MissaOuCelebracaoSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private LocalDate eventDate;
    private LocalTime eventTime;
    private Boolean massOrCelebration;

    @ManyToMany
    @JoinTable(
            name = "tb_event_person",
            joinColumns = @JoinColumn(name = "event_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "person_id", referencedColumnName = "id")
    )
    List<Person> people = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "tb_event_location",
            joinColumns = @JoinColumn(name = "event_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "location_id", referencedColumnName = "id")
    )
    List<Location> locations = new ArrayList<>();

    public CelebrationEvent(){

    }

    public CelebrationEvent(Long id, String nameMassOrEvent, LocalDate eventDate, LocalTime eventTime, Boolean massOrCelebration) {
        this.id = id;
        this.nameMassOrEvent = nameMassOrEvent;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
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

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }

    public void setMassOrCelebration(Boolean massOrCelebration) {
        this.massOrCelebration = massOrCelebration;
    }

    public List<Person> getPeople() {
        return people;
    }


    public List<Location> getLocations() {
        return locations;
    }

}
