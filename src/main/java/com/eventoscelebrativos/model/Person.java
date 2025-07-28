package com.eventoscelebrativos.model;

import jakarta.persistence.*;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "tb_person")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Person implements Serializable, UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @Column(unique = true)
    private String phoneNumber;
    private LocalDate birthdayDate;
    private String password;


    @Column(name = "type", insertable = false, updatable = false)
    private String type;

    @ManyToMany(mappedBy = "people")
    private List<CelebrationEvent> celebrationEvent = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "tb_person_role",
            joinColumns = @JoinColumn(name = "person_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    protected Set<Role> roles = new HashSet<>();

    public Person(){

    }

    public Person(Long id, String name, String phoneNumber, LocalDate birthdayDate, String password, String type) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
        this.password = password;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(phoneNumber, person.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(phoneNumber);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDate getBirthdayDate() {
        return birthdayDate;
    }

    public void setBirthdayDate(LocalDate birthdayDate) {
        this.birthdayDate = birthdayDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPassword() { return password;
    }

    public void setPassword(String password) { this.password = password;
    }

    public List<CelebrationEvent> getCelebrationEvent() {
        return celebrationEvent;
    }

    public Set<Role> getRoles() {
        return roles;
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
