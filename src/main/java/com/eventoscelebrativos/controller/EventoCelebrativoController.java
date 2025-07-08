package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.model.EventoCelebrativo;
import com.eventoscelebrativos.service.EventoCelebrativoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/eventos")
public class EventoCelebrativoController {

    private final EventoCelebrativoService eventoCelebrativoService;

    public EventoCelebrativoController(EventoCelebrativoService eventoCelebrativoService) {
        this.eventoCelebrativoService = eventoCelebrativoService;
    }

    @PostMapping
    public ResponseEntity<EventoCelebrativo> criarEvento(@RequestBody EventoCelebrativo eventoCelebrativo){

        EventoCelebrativo eventoCriado = eventoCelebrativoService.criarEvento(eventoCelebrativo);
        return new ResponseEntity<>(eventoCriado, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<EventoCelebrativo>> listarEventos(){
        List<EventoCelebrativo> eventos = eventoCelebrativoService.listarTodosEventos();
        if(eventos.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(eventos, HttpStatus.OK);
    }
    @GetMapping("/{id}")
    public ResponseEntity<EventoCelebrativo> buscarEventoPorId(@PathVariable Long id){
        Optional<EventoCelebrativo> eventoOptional = eventoCelebrativoService.buscarEventoPorId(id);
        return eventoOptional.map(evento -> new ResponseEntity<>(evento, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));

    }

    @PutMapping("/{id}")
    public ResponseEntity<EventoCelebrativo> atualizarEvento(@PathVariable Long id, @RequestBody EventoCelebrativo eventoAtualizado){

        EventoCelebrativo eventoAtualizadoResultado = eventoCelebrativoService.atualizarEvento(id, eventoAtualizado);
        return new ResponseEntity<>(eventoAtualizadoResultado, HttpStatus.OK);
    }

    @DeleteMapping
    public ResponseEntity<Void> deletarEvento(@PathVariable Long id){
        eventoCelebrativoService.deletarEvento(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
