package com.eventoscelebrativos.model;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "tb_location")
public class Location implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String churchName;
    private String address;

    @ManyToMany(mappedBy = "locations")
    private List<CelebrationEvent> celebrationEvents = new ArrayList<>();

    public Location(){

    }

    public Location(Long id, String churchName, String address) {
        this.id = id;
        this.churchName = churchName;
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return id == location.id;
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

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public List<CelebrationEvent> getCelebrationEvents() {
        return celebrationEvents;
    }

    public void setCelebrationEvents(List<CelebrationEvent> celebrationEvents) {
        this.celebrationEvents = celebrationEvents;
    }
}
