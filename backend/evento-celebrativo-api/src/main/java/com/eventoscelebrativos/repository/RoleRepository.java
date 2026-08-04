package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByAuthority(String authority);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.authority = :authority")
    Optional<Role> findByAuthorityForUpdate(@Param("authority") String authority);

    /**
     * Trava por chave primaria (indexada, lock de linha unica) em vez de por authority: a coluna
     * authority nao tem indice, entao um FOR UPDATE filtrando por ela faz table scan e trava todas
     * as linhas de tb_role (nao so a que casa o filtro), colidindo com qualquer outro lock que
     * dependa de uma linha diferente desta tabela (ex.: JOIN FETCH de roles de UserAccount).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.id = :id")
    Optional<Role> findByIdForUpdate(@Param("id") Long id);
}
