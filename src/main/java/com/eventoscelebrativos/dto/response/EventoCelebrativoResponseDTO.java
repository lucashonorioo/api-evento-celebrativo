package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;

public class EventoCelebrativoResponseDTO {

    private Long id;
    private String nomeMissaOuEvento;
    private LocalDateTime dataHoraEvento;
    private Boolean missaOuCelebracao;

    public EventoCelebrativoResponseDTO(Long id, String nomeMissaOuEvento, LocalDateTime dataHoraEvento, Boolean missaOuCelebracao) {
        this.id = id;
        this.nomeMissaOuEvento = nomeMissaOuEvento;
        this.dataHoraEvento = dataHoraEvento;
        this.missaOuCelebracao = missaOuCelebracao;
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

    public Boolean getMissaOuCelebracao() {
        return missaOuCelebracao;
    }
}
