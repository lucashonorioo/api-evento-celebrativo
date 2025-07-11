package com.eventoscelebrativos.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("comentarista")
public class Comentarista extends Pessoa{
    public Comentarista(){
        super();
    }
}
