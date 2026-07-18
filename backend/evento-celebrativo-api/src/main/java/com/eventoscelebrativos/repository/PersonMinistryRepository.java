package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonMinistryRepository extends JpaRepository<PersonMinistry, Long> {

    boolean existsByPersonIdAndMinistryType(Long personId, MinistryType ministryType);

    Optional<PersonMinistry> findByPersonIdAndMinistryType(Long personId, MinistryType ministryType);

    List<PersonMinistry> findAllByPersonId(Long personId);

    @Query(
            value = """
                    SELECT pm.person.id
                    FROM PersonMinistry pm
                    WHERE pm.ministryType = :ministryType
                      AND pm.active = TRUE
                    ORDER BY pm.person.name ASC, pm.person.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT pm.person.id)
                    FROM PersonMinistry pm
                    WHERE pm.ministryType = :ministryType
                      AND pm.active = TRUE
                    """
    )
    Page<Long> findActivePersonIdsByMinistryType(
            @Param("ministryType") MinistryType ministryType,
            Pageable pageable
    );

    @Query("""
            SELECT pm.person
            FROM PersonMinistry pm
            WHERE pm.ministryType = :ministryType
              AND pm.active = TRUE
            ORDER BY pm.person.name ASC, pm.person.id ASC
            """)
    List<com.eventoscelebrativos.model.Person> findActivePeopleByMinistryType(
            @Param("ministryType") MinistryType ministryType
    );

    @Query("""
            SELECT pm.person.id AS personId,
                   pm.ministryType AS ministryType
            FROM PersonMinistry pm
            WHERE pm.active = TRUE
              AND pm.person.id IN :personIds
            ORDER BY pm.person.id ASC, pm.ministryType ASC
            """)
    List<PersonMinistryTypeView> findActiveMinistryTypesByPersonIds(@Param("personIds") Collection<Long> personIds);

    @Query("""
            SELECT pm.person.id AS personId,
                   pm.ministryType AS ministryType,
                   pm.active AS active
            FROM PersonMinistry pm
            WHERE pm.person.id IN :personIds
            ORDER BY pm.person.id ASC, pm.ministryType ASC
            """)
    List<PersonMinistryStatusView> findAllMinistryStatusesByPersonIds(@Param("personIds") Collection<Long> personIds);

    void deleteAllByPersonId(Long personId);

    interface PersonMinistryTypeView {
        Long getPersonId();

        MinistryType getMinistryType();
    }

    interface PersonMinistryStatusView {
        Long getPersonId();

        MinistryType getMinistryType();

        Boolean getActive();
    }
}
