package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;

public class PadreResponseDTO {

    private Long id;
    private String nome;
    private LocalDate dataAniversario;

    public PadreResponseDTO(Long id, String nome, LocalDate dataAniversario) {
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
