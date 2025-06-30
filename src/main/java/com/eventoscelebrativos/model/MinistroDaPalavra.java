package com.eventoscelebrativos.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("tb_ministro_da_palavra")
public class MinistroDaPalavra extends Pessoa{
    public MinistroDaPalavra(){
        super();
    }
}
