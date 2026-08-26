package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;
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
public interface PersonMinistryRepository extends JpaRepository<PersonMinistry, Long> {

    boolean existsByPersonIdAndMinistryId(Long personId, Long ministryId);

    /**
     * Usada pela autorizacao escopada de dominio ({@code MinistryAuthorizationService}) para decidir,
     * a cada requisicao, se a Person autenticada pode administrar o Ministry persistente informado.
     */
    boolean existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(Long personId, Long ministryId);

    Optional<PersonMinistry> findByPersonIdAndMinistryId(Long personId, Long ministryId);

    List<PersonMinistry> findAllByPersonId(Long personId);

    @Query("""
            SELECT pm
            FROM PersonMinistry pm
            WHERE pm.person.id = :personId
              AND pm.legacyMinistryType = :ministryType
            """)
    Optional<PersonMinistry> findByPersonIdAndMinistryType(
            @Param("personId") Long personId,
            @Param("ministryType") MinistryType ministryType
    );

    @Query("""
            SELECT CASE WHEN COUNT(pm) > 0 THEN TRUE ELSE FALSE END
            FROM PersonMinistry pm
            WHERE pm.person.id = :personId
              AND pm.legacyMinistryType = :ministryType
            """)
    boolean existsByPersonIdAndMinistryType(
            @Param("personId") Long personId,
            @Param("ministryType") MinistryType ministryType
    );

    @Query("""
            SELECT CASE WHEN COUNT(pm) > 0 THEN TRUE ELSE FALSE END
            FROM PersonMinistry pm
            WHERE pm.person.id = :personId
              AND pm.legacyMinistryType = :ministryType
              AND pm.active = TRUE
              AND pm.coordinator = TRUE
            """)
    boolean existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
            @Param("personId") Long personId,
            @Param("ministryType") MinistryType ministryType
    );

    @Query(
            value = """
                    SELECT pm.person.id
                    FROM PersonMinistry pm
                    WHERE pm.ministry.id = :ministryId
                      AND pm.active = TRUE
                      AND pm.person.active = TRUE
                    ORDER BY pm.person.name ASC, pm.person.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT pm.person.id)
                    FROM PersonMinistry pm
                    WHERE pm.ministry.id = :ministryId
                      AND pm.active = TRUE
                      AND pm.person.active = TRUE
                    """
    )
    Page<Long> findActivePersonIdsByMinistryId(
            @Param("ministryId") Long ministryId,
            Pageable pageable
    );

    @Query("""
            SELECT pm.person
            FROM PersonMinistry pm
            WHERE pm.ministry.id = :ministryId
              AND pm.active = TRUE
              AND pm.person.active = TRUE
            ORDER BY pm.person.name ASC, pm.person.id ASC
            """)
    List<com.eventoscelebrativos.model.Person> findActivePeopleByMinistryId(
            @Param("ministryId") Long ministryId
    );

    @Query(
            value = """
                    SELECT pm.person.id
                    FROM PersonMinistry pm
                    WHERE pm.legacyMinistryType = :ministryType
                      AND pm.active = TRUE
                      AND pm.person.active = TRUE
                    ORDER BY pm.person.name ASC, pm.person.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT pm.person.id)
                    FROM PersonMinistry pm
                    WHERE pm.legacyMinistryType = :ministryType
                      AND pm.active = TRUE
                      AND pm.person.active = TRUE
                    """
    )
    Page<Long> findActivePersonIdsByMinistryType(
            @Param("ministryType") MinistryType ministryType,
            Pageable pageable
    );

    @Query("""
            SELECT pm.person
            FROM PersonMinistry pm
            WHERE pm.legacyMinistryType = :ministryType
              AND pm.active = TRUE
              AND pm.person.active = TRUE
            ORDER BY pm.person.name ASC, pm.person.id ASC
            """)
    List<com.eventoscelebrativos.model.Person> findActivePeopleByMinistryType(
            @Param("ministryType") MinistryType ministryType
    );

    @Query("""
            SELECT pm.person.id AS personId,
                   pm.ministry.id AS ministryId,
                   pm.ministry.normalizedName AS ministryNormalizedName
            FROM PersonMinistry pm
            WHERE pm.active = TRUE
              AND pm.person.id IN :personIds
            ORDER BY pm.person.id ASC, pm.ministry.id ASC
            """)
    List<PersonMinistryCatalogView> findActiveMinistriesByPersonIds(@Param("personIds") Collection<Long> personIds);

    @Query("""
            SELECT pm.person.id AS personId,
                   pm.ministry.id AS ministryId,
                   pm.ministry.normalizedName AS ministryNormalizedName,
                   pm.active AS active
            FROM PersonMinistry pm
            WHERE pm.person.id IN :personIds
            ORDER BY pm.person.id ASC, pm.ministry.id ASC
            """)
    List<PersonMinistryCatalogStatusView> findAllMinistryStatusesByPersonIds(@Param("personIds") Collection<Long> personIds);

    @Query("""
            SELECT pm.ministry
            FROM PersonMinistry pm
            WHERE pm.person.id = :personId
              AND pm.active = TRUE
              AND pm.coordinator = TRUE
            ORDER BY pm.ministry.id ASC
            """)
    List<Ministry> findActiveCoordinatedMinistriesByPersonId(@Param("personId") Long personId);

    @Query("""
            SELECT pm.person.id AS personId,
                   pm.legacyMinistryType AS ministryType
            FROM PersonMinistry pm
            WHERE pm.active = TRUE
              AND pm.person.id IN :personIds
            ORDER BY pm.person.id ASC, pm.legacyMinistryType ASC
            """)
    List<PersonMinistryTypeView> findActiveMinistryTypesByPersonIds(@Param("personIds") Collection<Long> personIds);

    @Query("""
            SELECT pm.legacyMinistryType
            FROM PersonMinistry pm
            WHERE pm.person.id = :personId
              AND pm.active = TRUE
              AND pm.coordinator = TRUE
            ORDER BY pm.legacyMinistryType ASC
            """)
    List<MinistryType> findActiveCoordinatedMinistryTypesByPersonId(@Param("personId") Long personId);

    void deleteAllByPersonId(Long personId);

    /**
     * Usada pelo envio de notificacoes de audience MINISTRY: bloqueia os vinculos ativos relevantes
     * de uma pessoa ja com Person e UserAccount bloqueados, para revalidar apos o lock se algum dos
     * ministerios selecionados continua ativo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT pm FROM PersonMinistry pm
            JOIN FETCH pm.ministry
            WHERE pm.person.id = :personId AND pm.ministry.id IN :ministryIds
            """)
    List<PersonMinistry> findByPersonIdAndMinistryIdInForUpdate(
            @Param("personId") Long personId,
            @Param("ministryIds") Collection<Long> ministryIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT pm FROM PersonMinistry pm
            WHERE pm.person.id = :personId AND pm.legacyMinistryType IN :ministryTypes
            """)
    List<PersonMinistry> findByPersonIdAndMinistryTypeInForUpdate(
            @Param("personId") Long personId,
            @Param("ministryTypes") Collection<MinistryType> ministryTypes
    );

    interface PersonMinistryCatalogView {
        Long getPersonId();

        Long getMinistryId();

        String getMinistryNormalizedName();
    }

    interface PersonMinistryCatalogStatusView {
        Long getPersonId();

        Long getMinistryId();

        String getMinistryNormalizedName();

        Boolean getActive();
    }

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
