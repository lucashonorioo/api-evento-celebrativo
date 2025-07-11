package com.eventoscelebrativos.model;



import com.eventoscelebrativos.model.serializer.MissaOuCelebracaoSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "tb_evento_celebrativo")
public class EventoCelebrativo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nomeMissaOuEvento;
    private LocalDate dataEvento;
    private LocalTime horaEvento;
    private Boolean missaOuCelebracao;

    @ManyToMany
    @JoinTable(
            name = "tb_evento_pessoa",
            joinColumns = @JoinColumn(name = "evento_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "pessoa_id", referencedColumnName = "id")
    )
    List<Pessoa> pessoas;

    @ManyToMany
    @JoinTable(
            name = "tb_evento_local",
            joinColumns = @JoinColumn(name = "evento_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "local_id", referencedColumnName = "id")
    )
    List<Local> locais;

    public EventoCelebrativo(){

    }

    public EventoCelebrativo(Long id, String nomeMissaOuEvento, LocalDate dataEvento, LocalTime horaEvento, Boolean missaOuCelebracao) {
        this.id = id;
        this.nomeMissaOuEvento = nomeMissaOuEvento;
        this.dataEvento = dataEvento;
        this.horaEvento = horaEvento;
        this.missaOuCelebracao = missaOuCelebracao;
        pessoas = new ArrayList<>();
        locais = new ArrayList<>();
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

    public Long getId() {
        return id;
    }

    public String getNomeMissaOuEvento() {
        return nomeMissaOuEvento;
    }

    public LocalDate getDataEvento() {
        return dataEvento;
    }

    public LocalTime getHoraEvento() {
        return horaEvento;
    }

    @JsonSerialize(using = MissaOuCelebracaoSerializer.class)
    public Boolean getMissaOuCelebracao() {
        return missaOuCelebracao;
    }

    public List<Pessoa> getPessoas() {
        return pessoas;
    }

    public List<Local> getLocais() {
        return locais;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNomeMissaOuEvento(String nomeMissaOuEvento) {
        this.nomeMissaOuEvento = nomeMissaOuEvento;
    }

    public void setDataEvento(LocalDate dataEvento) {
        this.dataEvento = dataEvento;
    }

    public void setHoraEvento(LocalTime horaEvento) {
        this.horaEvento = horaEvento;
    }

    public void setMissaOuCelebracao(Boolean missaOuCelebracao) {
        this.missaOuCelebracao = missaOuCelebracao;
    }
}
