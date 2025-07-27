package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class EventoCelebrativoRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nomeMissaOuEvento;

    @NotNull(message = "O campo da data não pode ser vazio")
    @FutureOrPresent(message = "A data só pode ser no presente ou futuro")
    private LocalDate dataEvento;

    @NotNull(message = "O campo da hora não pode ser vazio")
    private LocalTime horaEvento;

    @NotNull(message = "É obrigatório informar se é uma missa ou celebração.")
    private Boolean missaOuCelebracao;

    public EventoCelebrativoRequestDTO(){

    }

    public EventoCelebrativoRequestDTO(String nomeMissaOuEvento, LocalDate dataEvento, LocalTime horaEvento, Boolean missaOuCelebracao) {
        this.nomeMissaOuEvento = nomeMissaOuEvento;
        this.dataEvento = dataEvento;
        this.horaEvento = horaEvento;
        this.missaOuCelebracao = missaOuCelebracao;
    }

    public String getNomeMissaOuEvento() {
        return nomeMissaOuEvento;
    }

    public void setNomeMissaOuEvento(String nomeMissaOuEvento) {
        this.nomeMissaOuEvento = nomeMissaOuEvento;
    }

    public LocalDate getDataEvento() {
        return dataEvento;
    }

    public void setDataEvento(LocalDate dataEvento) {
        this.dataEvento = dataEvento;
    }

    public LocalTime getHoraEvento() {
        return horaEvento;
    }

    public void setHoraEvento(LocalTime horaEvento) {
        this.horaEvento = horaEvento;
    }

    public Boolean getMissaOuCelebracao() {
        return missaOuCelebracao;
    }

    public void setMissaOuCelebracao(Boolean missaOuCelebracao) {
        this.missaOuCelebracao = missaOuCelebracao;
    }
}
