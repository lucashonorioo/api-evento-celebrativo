package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
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

    boolean existsByMinistryIdAndActiveTrue(Long ministryId);

    /**
     * Usada pela autorizacao escopada de dominio ({@code MinistryAuthorizationService}) para decidir,
     * a cada requisicao, se a Person autenticada pode administrar o Ministry persistente informado.
     */
    boolean existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(Long personId, Long ministryId);

    Optional<PersonMinistry> findByPersonIdAndMinistryId(Long personId, Long ministryId);

    List<PersonMinistry> findAllByPersonId(Long personId);

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

    @Query("""
            SELECT pm.person.id AS personId,
                   pm.ministry.id AS ministryId,
                   pm.ministry.name AS ministryName,
                   pm.coordinator AS coordinator
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

    interface PersonMinistryCatalogView {
        Long getPersonId();

        Long getMinistryId();

        String getMinistryName();

        Boolean getCoordinator();
    }

    interface PersonMinistryCatalogStatusView {
        Long getPersonId();

        Long getMinistryId();

        String getMinistryNormalizedName();

        Boolean getActive();
    }

}
