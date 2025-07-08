package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;

public class LocalRequestDTO {

    @NotBlank(message = "O nome da igreja não pode ser vazio")
    private String nomeDaIgreja;

    @NotBlank(message = "O endereço não pode ser vazio")
    private String endereco;

    public LocalRequestDTO(){

    }

    public LocalRequestDTO(String nomeDaIgreja, String endereco) {
        this.nomeDaIgreja = nomeDaIgreja;
        this.endereco = endereco;
    }

    public String getNomeDaIgreja() {
        return nomeDaIgreja;
    }

    public void setNomeDaIgreja(String nomeDaIgreja) {
        this.nomeDaIgreja = nomeDaIgreja;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }
}
