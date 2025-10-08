package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Person;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<Person> findByPhoneNumber(String phoneNumber);

}
