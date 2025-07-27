package com.eventoscelebrativos.model;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "tb_local")
public class Local implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nomeDaIgreja;
    private String endereco;

    @ManyToMany(mappedBy = "locais")
    private List<CelebrationEvent> celebrationEvents;

    public Local(){

    }

    public Local(Long id, String nomeDaIgreja, String endereco) {
        this.id = id;
        this.nomeDaIgreja = nomeDaIgreja;
        this.endereco = endereco;
        this.celebrationEvents = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Local local = (Local) o;
        return id == local.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public Long getId() {
        return id;
    }

    public String getNomeDaIgreja() {
        return nomeDaIgreja;
    }

    public String getEndereco() {
        return endereco;
    }

    public List<CelebrationEvent> getEventoCelebrativos() {
        return celebrationEvents;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNomeDaIgreja(String nomeDaIgreja) {
        this.nomeDaIgreja = nomeDaIgreja;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }
}
