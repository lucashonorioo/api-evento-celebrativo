package com.eventoscelebrativos.dto.response;

public class LocalResponseDTO {

    private Long id;
    private String nomeDaIgreja;
    private String endereco;

    public LocalResponseDTO(Long id, String nomeDaIgreja, String endereco) {
        this.id = id;
        this.nomeDaIgreja = nomeDaIgreja;
        this.endereco = endereco;
    }

    public Long getId() {
        return id;
    }

    public String getNomeDaIgreja() {
        return nomeDaIgreja;
    }

    public String getEndereco() {
        return endereco;
    }
}
