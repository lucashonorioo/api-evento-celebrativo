package com.eventoscelebrativos.model;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@MappedSuperclass
public abstract class Pessoa implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String nome;
    private LocalDate dataAniversario;
    private LocalDateTime dataAtuacao;

    @ManyToOne
    private EventoCelebrativo eventoCelebrativo;

    public Pessoa(){

    }

    public Pessoa(long id, String nome, LocalDate dataAniversario, LocalDateTime dataAtuacao, EventoCelebrativo eventoCelebrativo) {
        this.id = id;
        this.nome = nome;
        this.dataAniversario = dataAniversario;
        this.dataAtuacao = dataAtuacao;
        this.eventoCelebrativo = eventoCelebrativo;
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

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public LocalDate getDataAniversario() {
        return dataAniversario;
    }

    public LocalDateTime getDataAtuacao() {
        return dataAtuacao;
    }

    public EventoCelebrativo getEventoCelebrativo() {
        return eventoCelebrativo;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setDataAniversario(LocalDate dataAniversario) {
        this.dataAniversario = dataAniversario;
    }

    public void setDataAtuacao(LocalDateTime dataAtuacao) {
        this.dataAtuacao = dataAtuacao;
    }

    public void setEventoCelebrativo(EventoCelebrativo eventoCelebrativo) {
        this.eventoCelebrativo = eventoCelebrativo;
    }
}
