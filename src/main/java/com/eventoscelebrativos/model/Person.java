package com.eventoscelebrativos.model;

import jakarta.persistence.*;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "tb_pessoa")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING)
public abstract class Pessoa implements Serializable, UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;

    @Column(unique = true)
    private String telefone;
    private LocalDate dataAniversario;
    private String password;


    @Column(name = "tipo", insertable = false, updatable = false)
    private String tipo;

    @ManyToMany(mappedBy = "pessoas")
    private List<CelebrationEvent> celebrationEvent;

    @ManyToMany
    @JoinTable(
            name = "tb_pessoa_role",
            joinColumns = @JoinColumn(name = "pessoa_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    protected Set<Role> roles = new HashSet<>();

    public Pessoa(){

    }

    public Pessoa(Long id, String nome, String telefone, LocalDate dataAniversario, String password, String tipo) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.dataAniversario = dataAniversario;
        this.password = password;
        this.tipo = tipo;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Pessoa pessoa = (Pessoa) o;
        return Objects.equals(telefone, pessoa.telefone);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(telefone);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public LocalDate getDataAniversario() {
        return dataAniversario;
    }


    public List<CelebrationEvent> getEventoCelebrativo() {
        return celebrationEvent;
    }
    public String getTipo() {
        return tipo;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public void setDataAniversario(LocalDate dataAniversario) {
        this.dataAniversario = dataAniversario;
    }

    public String getPassword() { return password;
    }

    public void setPassword(String password) { this.password = password;
    }

    public void addRole(Role role){ roles.add(role);
    }

    public Boolean hasRole(String roleName){
        for(Role role : roles){
            if(role.getAuthority().equals(roleName)){
                return true;
            }
        }
        return false;
    }

}
