package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public class MinistroDaPalavraRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nome;

    @NotNull(message = "O campo da data não pode ser vazio")
    @Past(message = "A data de nascimento só pode ser no passado")
    private LocalDate dataAniversario;

    public MinistroDaPalavraRequestDTO(){

    }

    public MinistroDaPalavraRequestDTO(String nome, LocalDate dataAniversario) {
        this.nome = nome;
        this.dataAniversario = dataAniversario;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public LocalDate getDataAniversario() {
        return dataAniversario;
    }

    public void setDataAniversario(LocalDate dataAniversario) {
        this.dataAniversario = dataAniversario;
    }
}
