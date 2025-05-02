package com.eventoscelebrativos.model;



import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
public class EventoCelebrativo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String nomeMissaOuEvento;
    private LocalDateTime dataHoraEvento;
    private Boolean missaOuCelebracao;

    @OneToMany
    List<Pessoa> pessoas;

    @ManyToMany
    @JoinTable(
            name = "evento_local",
            joinColumns = @JoinColumn(name = "evento_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "local_id", referencedColumnName = "id")
    )
    List<Local> locais;

    public EventoCelebrativo(){

    }

    public EventoCelebrativo(long id, String nomeMissaOuEvento, LocalDateTime dataHoraEvento, Boolean missaOuCelebracao) {
        this.id = id;
        this.nomeMissaOuEvento = nomeMissaOuEvento;
        this.dataHoraEvento = dataHoraEvento;
        this.missaOuCelebracao = missaOuCelebracao;
        this.pessoas = new ArrayList<>();
        this.locais = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EventoCelebrativo that = (EventoCelebrativo) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public long getId() {
        return id;
    }

    public String getNomeMissaOuEvento() {
        return nomeMissaOuEvento;
    }

    public LocalDateTime getDataHoraEvento() {
        return dataHoraEvento;
    }

    public Boolean getMissaOuCelebracao() {
        return missaOuCelebracao;
    }

    public List<Pessoa> getPessoas() {
        return pessoas;
    }

    public List<Local> getLocais() {
        return locais;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setNomeMissaOuEvento(String nomeMissaOuEvento) {
        this.nomeMissaOuEvento = nomeMissaOuEvento;
    }

    public void setDataHoraEvento(LocalDateTime dataHoraEvento) {
        this.dataHoraEvento = dataHoraEvento;
    }

    public void setMissaOuCelebracao(Boolean missaOuCelebracao) {
        this.missaOuCelebracao = missaOuCelebracao;
    }
}
