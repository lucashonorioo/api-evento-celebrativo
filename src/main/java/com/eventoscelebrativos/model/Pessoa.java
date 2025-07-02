package com.eventoscelebrativos.model;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING)
public abstract class Pessoa implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private LocalDate dataAniversario;

    @Column(name = "tipo", insertable = false, updatable = false)
    private String tipo;

    @ManyToMany(mappedBy = "pessoas")
    private List<EventoCelebrativo> eventoCelebrativo;

    public Pessoa(){

    }


    public Pessoa(Long id, String nome, LocalDate dataAniversario, String tipo) {
        this.id = id;
        this.nome = nome;
        this.dataAniversario = dataAniversario;
        this.tipo = tipo;
        this.eventoCelebrativo = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Pessoa pessoa = (Pessoa) o;
        return id == pessoa.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public LocalDate getDataAniversario() {
        return dataAniversario;
    }


    public List<EventoCelebrativo> getEventoCelebrativo() {
        return eventoCelebrativo;
    }
    public String getTipo() {
        return tipo;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setDataAniversario(LocalDate dataAniversario) {
        this.dataAniversario = dataAniversario;
    }



}
