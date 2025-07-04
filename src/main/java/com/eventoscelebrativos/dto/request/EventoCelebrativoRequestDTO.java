package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class EventoCelebrativoRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nomeMissaOuEvento;

    @NotNull(message = "O campo da data não pode ser vazio")
    @FutureOrPresent(message = "A data só pode ser no presente ou futuro")
    private LocalDateTime dataHoraEvento;

    @NotNull(message = "É obrigatório informar se é uma missa ou celebração.")
    private Boolean missaOuCelebracao;

    public EventoCelebrativoRequestDTO(){

    }

    public EventoCelebrativoRequestDTO(String nomeMissaOuEvento, LocalDateTime dataHoraEvento, Boolean missaOuCelebracao) {
        this.nomeMissaOuEvento = nomeMissaOuEvento;
        this.dataHoraEvento = dataHoraEvento;
        this.missaOuCelebracao = missaOuCelebracao;
    }

    public String getNomeMissaOuEvento() {
        return nomeMissaOuEvento;
    }

    public void setNomeMissaOuEvento(String nomeMissaOuEvento) {
        this.nomeMissaOuEvento = nomeMissaOuEvento;
    }

    public LocalDateTime getDataHoraEvento() {
        return dataHoraEvento;
    }

    public void setDataHoraEvento(LocalDateTime dataHoraEvento) {
        this.dataHoraEvento = dataHoraEvento;
    }

    public Boolean getMissaOuCelebracao() {
        return missaOuCelebracao;
    }

    public void setMissaOuCelebracao(Boolean missaOuCelebracao) {
        this.missaOuCelebracao = missaOuCelebracao;
    }
}
