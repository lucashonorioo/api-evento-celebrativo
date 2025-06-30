package com.eventoscelebrativos.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("tb_leitor")
public class Leitor extends Pessoa{
    public Leitor(){
        super();
    }
}
