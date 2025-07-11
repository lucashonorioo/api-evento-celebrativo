package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class EventoCelebrativoResponseDTO {

    private Long id;
    private String nomeMissaOuEvento;
    private LocalDate dataEvento;
    private LocalTime horaEvento;
    private Boolean missaOuCelebracao;


    public EventoCelebrativoResponseDTO(Long id, String nomeMissaOuEvento, LocalDate dataEvento, LocalTime horaEvento, Boolean missaOuCelebracao) {
        this.id = id;
        this.nomeMissaOuEvento = nomeMissaOuEvento;
        this.dataEvento = dataEvento;
        this.horaEvento = horaEvento;
        this.missaOuCelebracao = missaOuCelebracao;
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

    public Boolean getMissaOuCelebracao() {
        return missaOuCelebracao;
    }
}
