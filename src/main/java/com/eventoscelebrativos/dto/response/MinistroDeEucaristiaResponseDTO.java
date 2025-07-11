package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;

public class MinistroDeEucaristiaResponseDTO {

    private Long id;
    private String nome;
    private String telefone;
    private LocalDate dataAniversario;

    public MinistroDeEucaristiaResponseDTO(Long id, String nome, String telefone, LocalDate dataAniversario) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.dataAniversario = dataAniversario;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public LocalDate getDataAniversario() {
        return dataAniversario;
    }
}
