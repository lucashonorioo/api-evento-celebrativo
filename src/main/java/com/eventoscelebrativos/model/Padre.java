package com.eventoscelebrativos.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@DiscriminatorValue("tb_padre")
public class Padre extends Pessoa{
    public Padre(){
        super();
    }
}
