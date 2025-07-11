package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class MinistroDeEucaristiaRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nome;

    @NotBlank(message = "O campo telefone não pode ser vazio")
    @Size(min = 11, max = 11, message = "O Telefone deve ter 11 digitos com o DD")
    private String telefone;

    @NotNull(message = "O campo da data não pode ser vazio")
    @Past(message = "A data de nascimento só pode ser no passado")
    private LocalDate dataAniversario;

    public MinistroDeEucaristiaRequestDTO(){

    }

    public MinistroDeEucaristiaRequestDTO(String nome, String telefone, LocalDate dataAniversario) {
        this.nome = nome;
        this.telefone = telefone;
        this.dataAniversario = dataAniversario;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public LocalDate getDataAniversario() {
        return dataAniversario;
    }

    public void setDataAniversario(LocalDate dataAniversario) {
        this.dataAniversario = dataAniversario;
    }
}
