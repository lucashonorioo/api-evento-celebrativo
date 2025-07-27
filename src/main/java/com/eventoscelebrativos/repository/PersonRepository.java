package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PessoaRepository extends JpaRepository<Person, Long> {

    @Query(nativeQuery = true, value = """
         SELECT tb_pessoa.telefone AS username, tb_pessoa.password, tb_role.id
         AS roleId, tb_role.authority
         FROM tb_pessoa
         INNER JOIN tb_pessoa_role ON tb_pessoa.id = tb_pessoa_role.pessoa_id
         INNER JOIN tb_role ON tb_role.id = tb_pessoa_role.role_id
         WHERE tb_pessoa.telefone = :telefone
         """)
    Person findByTelefone(String telefone);

}
