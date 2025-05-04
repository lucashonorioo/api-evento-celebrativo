package com.eventoscelebrativos.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ministro_de_eucaristia")
public class MinistroDeEucaristia extends Pessoa{
    public MinistroDeEucaristia(){
        super();
    }
}
