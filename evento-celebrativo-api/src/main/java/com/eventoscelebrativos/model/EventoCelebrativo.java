package com.eventoscelebrativos.model;



import java.time.LocalDateTime;
import java.util.List;


public class EventoCelebrativo {

    private long id;
    private String nomeMissaOuEvento;
    private LocalDateTime dataHoraEvento;
    private Boolean missaOuCelebracao;

    List<Pessoa> pessoas;
}
