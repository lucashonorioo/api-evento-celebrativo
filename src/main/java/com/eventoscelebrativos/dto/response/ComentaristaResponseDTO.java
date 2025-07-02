package com.eventoscelebrativos.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public class ComentaristaResponseDTO {

    private Long id;
    private String nome;
    private LocalDate dataAniversario;

    public ComentaristaResponseDTO(Long id, String nome, LocalDate dataAniversario) {
        this.id = id;
        this.nome = nome;
        this.dataAniversario = dataAniversario;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public LocalDate getDataAniversario() {
        return dataAniversario;
    }
}
