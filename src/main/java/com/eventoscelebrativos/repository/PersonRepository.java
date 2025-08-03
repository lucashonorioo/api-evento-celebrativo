package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    @Query(nativeQuery = true, value = """
         SELECT tb_person.phone_number AS username, tb_person.password, tb_role.id
         AS roleId, tb_role.authority
         FROM tb_person
         INNER JOIN tb_person_role ON tb_person.id = tb_person_role.person_id
         INNER JOIN tb_role ON tb_role.id = tb_person_role.role_id
         WHERE tb_person.phone_number = :phoneNumber
         """)
    Optional<Person> findByPhoneNumber(String phoneNumber);

}
