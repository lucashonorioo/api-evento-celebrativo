package com.eventoscelebrativos.model;



import com.eventoscelebrativos.model.serializer.MissaOuCelebracaoSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
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
    private LocalDateTime dataHoraEvento;
    private Boolean missaOuCelebracao;

    @ManyToMany
    @JoinTable(
            name = "evento_pessoa",
            joinColumns = @JoinColumn(name = "evento_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "pessoa_id", referencedColumnName = "id")
    )
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

    public EventoCelebrativo(Long id, String nomeMissaOuEvento, LocalDateTime dataHoraEvento, Boolean missaOuCelebracao) {
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

    public Long getId() {
        return id;
    }

    public String getNomeMissaOuEvento() {
        return nomeMissaOuEvento;
    }

    public LocalDateTime getDataHoraEvento() {
        return dataHoraEvento;
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

    public void setDataHoraEvento(LocalDateTime dataHoraEvento) {
        this.dataHoraEvento = dataHoraEvento;
    }

    public void setMissaOuCelebracao(Boolean missaOuCelebracao) {
        this.missaOuCelebracao = missaOuCelebracao;
    }
}
