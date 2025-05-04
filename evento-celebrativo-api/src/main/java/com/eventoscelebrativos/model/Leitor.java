package com.eventoscelebrativos.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("leitor")
public class Leitor extends Pessoa{
    public Leitor(){
        super();
    }
}
