package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.controller.exception.InvalidRequestBodyException;
import com.eventoscelebrativos.model.EventoCelebrativo;
import com.eventoscelebrativos.service.EventoCelebrativoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eventos")
public class EventoCelebrativoController {

    private final EventoCelebrativoService eventoCelebrativoService;

    public EventoCelebrativoController(EventoCelebrativoService eventoCelebrativoService) {
        this.eventoCelebrativoService = eventoCelebrativoService;
    }

    @PostMapping
    public ResponseEntity<EventoCelebrativo> criarEvento(@RequestBody EventoCelebrativo eventoCelebrativo){
        if(eventoCelebrativo.getNomeMissaOuEvento() == null){
            throw new InvalidRequestBodyException("O nome da missa ou evento é obrigatorio.");
        }
        if(eventoCelebrativo.getMissaOuCelebracao() == null){
            throw new InvalidRequestBodyException("É obrigatorio informar se é uma missa ou celebração");
        }
        if(eventoCelebrativo.getDataHoraEvento() == null){
            throw new InvalidRequestBodyException("O obrigado a informar o horario.");
        }
        EventoCelebrativo eventoCriado = eventoCelebrativoService.criarEvento(eventoCelebrativo);
        return new ResponseEntity<>(eventoCriado, HttpStatus.CREATED);

    }






}
