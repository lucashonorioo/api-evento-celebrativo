package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PersonDetailsServiceImpl implements UserDetailsService {

    private final PersonRepository personRepository;


    private final PasswordEncoder passwordEncoder;



    public PersonDetailsServiceImpl(PersonRepository personRepository, PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Optional<Person> personOptional = personRepository.findByPhoneNumber(username);
        Person person = personOptional.orElseThrow(() -> new UsernameNotFoundException("Telefone não encontrado"));

        return person;
    }
}
