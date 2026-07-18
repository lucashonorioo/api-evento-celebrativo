package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Person;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<Person> findByPhoneNumber(String phoneNumber);

    @EntityGraph(attributePaths = "roles")
    @Query("SELECT p FROM Person p WHERE p.id = :id")
    Optional<Person> findByIdWithRoles(@Param("id") Long id);

    @Query(
            value = """
                    SELECT p.id
                    FROM Person p
                    WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
                      AND (:phoneNumber IS NULL OR p.phoneNumber LIKE CONCAT('%', :phoneNumber, '%'))
                      AND (:personType IS NULL OR p.personType = :personType)
                      AND (:role IS NULL OR EXISTS (
                          SELECT r.id
                          FROM p.roles r
                          WHERE r.authority = :role
                      ))
                    ORDER BY p.name ASC, p.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(p.id)
                    FROM Person p
                    WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
                      AND (:phoneNumber IS NULL OR p.phoneNumber LIKE CONCAT('%', :phoneNumber, '%'))
                      AND (:personType IS NULL OR p.personType = :personType)
                      AND (:role IS NULL OR EXISTS (
                          SELECT r.id
                          FROM p.roles r
                          WHERE r.authority = :role
                      ))
                    """
    )
    Page<Long> findAdminPageIds(
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("personType") String personType,
            @Param("role") String role,
            Pageable pageable
    );

    @Query("SELECT DISTINCT p FROM Person p LEFT JOIN FETCH p.roles WHERE p.id IN :ids")
    List<Person> findAllByIdInWithRoles(@Param("ids") Collection<Long> ids);

    @Query("SELECT p FROM Person p WHERE p.id IN :ids")
    List<Person> findAllByIdIn(@Param("ids") Collection<Long> ids);

    @Query(
            value = """
                    SELECT p.id
                    FROM Person p
                    ORDER BY p.name ASC, p.id ASC
                    """,
            countQuery = "SELECT COUNT(p.id) FROM Person p"
    )
    Page<Long> findPersonIdsForMinistryAudit(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT DISTINCT p
            FROM Person p
            JOIN FETCH p.roles r
            WHERE r.authority = :authority
            """)
    List<Person> findPeopleByRoleForUpdate(@Param("authority") String authority);
}
