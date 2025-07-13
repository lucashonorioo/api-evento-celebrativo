package com.eventoscelebrativos.projection;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface EventoEscalaMinistrosProjection {

    String getNomeEvento();
    LocalDate getDataEvento();
    LocalTime getHoraEvento();
    String getNomeIgreja();
    String getNomeMinistro();
}
