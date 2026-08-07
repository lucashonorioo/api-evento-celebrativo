package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * FlushMode COMMIT: evita que o Hibernate faca auto-flush da mutacao pendente de phoneNumber (a
     * propria pessoa cujo telefone esta sendo alterado) antes desta consulta, o que faria a
     * constraint unica disparar como DataIntegrityViolationException generica antes da checagem
     * amigavel explicita em {@link com.eventoscelebrativos.service.PersonCadastralUpdateService}.
     * <p>
     * Deliberadamente NAO usa lock pessimista: um {@code SELECT ... FOR UPDATE} sobre um telefone que
     * ainda pode nao pertencer a ninguem tomaria um gap lock do InnoDB, o que pode gerar deadlock
     * genuino quando duas pessoas disputam o mesmo telefone novo simultaneamente. A garantia final de
     * unicidade fica a cargo da constraint {@code uk_tb_person_phone_number}, verificada no flush
     * explicito do service - esta consulta serve apenas para retornar um erro amigavel no caso comum
     * (sem concorrencia real).
     */
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    Optional<Person> findByPhoneNumber(String phoneNumber);

    @Query(
            value = """
                    SELECT p.id
                    FROM Person p
                    WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
                      AND (:phoneNumber IS NULL OR p.phoneNumber LIKE CONCAT('%', :phoneNumber, '%'))
                      AND (:ministryType IS NULL OR EXISTS (
                          SELECT pm
                          FROM PersonMinistry pm
                          WHERE pm.person = p
                            AND pm.ministryType = :ministryType
                            AND pm.active = TRUE
                      ))
                      AND (:role IS NULL OR EXISTS (
                          SELECT uar
                          FROM UserAccountRole uar
                          WHERE uar.userAccount.person = p
                            AND uar.role.authority = :role
                      ))
                      AND (:personActive IS NULL OR p.active = :personActive)
                      AND (:accountExists IS NULL
                           OR (:accountExists = TRUE AND EXISTS (SELECT ua1 FROM UserAccount ua1 WHERE ua1.person = p))
                           OR (:accountExists = FALSE AND NOT EXISTS (SELECT ua2 FROM UserAccount ua2 WHERE ua2.person = p))
                      )
                      AND (:accountEnabled IS NULL OR EXISTS (
                          SELECT ua3 FROM UserAccount ua3 WHERE ua3.person = p AND ua3.enabled = :accountEnabled
                      ))
                    ORDER BY p.name ASC, p.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(p.id)
                    FROM Person p
                    WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
                      AND (:phoneNumber IS NULL OR p.phoneNumber LIKE CONCAT('%', :phoneNumber, '%'))
                      AND (:ministryType IS NULL OR EXISTS (
                          SELECT pm
                          FROM PersonMinistry pm
                          WHERE pm.person = p
                            AND pm.ministryType = :ministryType
                            AND pm.active = TRUE
                      ))
                      AND (:role IS NULL OR EXISTS (
                          SELECT uar
                          FROM UserAccountRole uar
                          WHERE uar.userAccount.person = p
                            AND uar.role.authority = :role
                      ))
                      AND (:personActive IS NULL OR p.active = :personActive)
                      AND (:accountExists IS NULL
                           OR (:accountExists = TRUE AND EXISTS (SELECT ua1 FROM UserAccount ua1 WHERE ua1.person = p))
                           OR (:accountExists = FALSE AND NOT EXISTS (SELECT ua2 FROM UserAccount ua2 WHERE ua2.person = p))
                      )
                      AND (:accountEnabled IS NULL OR EXISTS (
                          SELECT ua3 FROM UserAccount ua3 WHERE ua3.person = p AND ua3.enabled = :accountEnabled
                      ))
                    """
    )
    Page<Long> findAdminPageIds(
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("ministryType") MinistryType ministryType,
            @Param("role") String role,
            @Param("personActive") Boolean personActive,
            @Param("accountExists") Boolean accountExists,
            @Param("accountEnabled") Boolean accountEnabled,
            Pageable pageable
    );

    @Query("SELECT p FROM Person p WHERE p.id IN :ids")
    List<Person> findAllByIdIn(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Person p WHERE p.id = :id")
    Optional<Person> findByIdForUpdate(@Param("id") Long id);
}
