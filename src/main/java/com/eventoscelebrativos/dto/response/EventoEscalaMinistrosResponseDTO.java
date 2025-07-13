package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.EventoCelebrativo;
import com.eventoscelebrativos.model.Pessoa;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class EventoEscalaMinistrosResponseDTO {

    String nomeMissaOuEvento;
    LocalDate dataEvento;
    LocalTime horaEvento;
    String nomeDaIgreja;
    List<String> nomeMinistros;


    public EventoEscalaMinistrosResponseDTO(String nomeMissaOuEvento, LocalDate dataEvento, LocalTime horaEvento, String nomeDaIgreja) {
        this.nomeMissaOuEvento = nomeMissaOuEvento;
        this.dataEvento = dataEvento;
        this.horaEvento = horaEvento;
        this.nomeDaIgreja = nomeDaIgreja;
        this.nomeMinistros = new ArrayList<>();
    }

    public EventoEscalaMinistrosResponseDTO(EventoCelebrativo eventoCelebrativo) {
        nomeMissaOuEvento = eventoCelebrativo.getNomeMissaOuEvento();
        dataEvento = eventoCelebrativo.getDataEvento();
        horaEvento = eventoCelebrativo.getHoraEvento();
        nomeDaIgreja = eventoCelebrativo.getLocais().get(0).getNomeDaIgreja();
        nomeMinistros = eventoCelebrativo.getPessoas().stream().filter( p -> "ministro_de_eucaristia".equals(p.getTipo())).map(Pessoa::getNome).toList();
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

    public String getNomeDaIgreja() {
        return nomeDaIgreja;
    }

    public List<String> getNomeMinistros() {
        return nomeMinistros;
    }
}
