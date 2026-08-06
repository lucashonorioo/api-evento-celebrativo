package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                    """
    )
    Page<Long> findAdminPageIds(
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("ministryType") MinistryType ministryType,
            @Param("role") String role,
            Pageable pageable
    );

    @Query("SELECT p FROM Person p WHERE p.id IN :ids")
    List<Person> findAllByIdIn(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Person p WHERE p.phoneNumber = :phoneNumber")
    Optional<Person> findByPhoneNumberForUpdate(@Param("phoneNumber") String phoneNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Person p WHERE p.id = :id")
    Optional<Person> findByIdForUpdate(@Param("id") Long id);
}
