package com.eventoscelebrativos.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Entity
@DiscriminatorValue("ministro_de_eucaristia")
public class MinistroDeEucaristia extends Pessoa{
    public MinistroDeEucaristia(){
        super();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @Override
    public String getUsername() {
        return "telefone";
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
