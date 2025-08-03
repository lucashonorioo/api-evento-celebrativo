package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PersonDetailsServiceImpl implements UserDetailsService {

    private final PersonRepository personRepository;


    public PersonDetailsServiceImpl(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Optional<Person> personOptional = personRepository.findByPhoneNumber(username);
        Person person = personOptional.orElseThrow(() -> new UsernameNotFoundException("Telefone não encontrado"));

        return person;
    }
}
