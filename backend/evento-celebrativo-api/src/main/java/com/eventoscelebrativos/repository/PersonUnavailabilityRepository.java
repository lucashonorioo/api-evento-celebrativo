package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityPersonProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonUnavailabilityRepository extends JpaRepository<PersonUnavailability, Long> {

    @Query("""
            SELECT u
            FROM PersonUnavailability u
            WHERE u.person.id = :personId
              AND u.startDate <= :queryEndDate
              AND u.endDate >= :queryStartDate
            ORDER BY u.startDate ASC, u.endDate ASC, u.id ASC
            """)
    Page<PersonUnavailability> findByPersonIdIntersecting(
            @Param("personId") Long personId,
            @Param("queryStartDate") LocalDate queryStartDate,
            @Param("queryEndDate") LocalDate queryEndDate,
            Pageable pageable
    );

    Optional<PersonUnavailability> findByIdAndPersonId(Long id, Long personId);

    @Query("""
            SELECT u
            FROM PersonUnavailability u
            WHERE u.person.id = :personId
              AND u.startDate <= :endDate
              AND u.endDate >= :startDate
            """)
    List<PersonUnavailability> findOverlapping(
            @Param("personId") Long personId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT u
            FROM PersonUnavailability u
            WHERE u.person.id = :personId
              AND u.id <> :excludeId
              AND u.startDate <= :endDate
              AND u.endDate >= :startDate
            """)
    List<PersonUnavailability> findOverlappingExcludingId(
            @Param("personId") Long personId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            SELECT u.person.id AS personId, u.person.name AS personName, u.startDate AS startDate, u.endDate AS endDate
            FROM PersonUnavailability u
            WHERE u.person.id IN :personIds
              AND u.startDate <= :date
              AND u.endDate >= :date
            """)
    List<PersonUnavailabilityPersonProjection> findByPersonIdsAndDate(
            @Param("personIds") Collection<Long> personIds,
            @Param("date") LocalDate date
    );

    @Query("""
            SELECT u.person.id AS personId, u.person.name AS personName, u.startDate AS startDate, u.endDate AS endDate
            FROM PersonUnavailability u
            WHERE u.startDate <= :date
              AND u.endDate >= :date
            ORDER BY u.person.name ASC, u.person.id ASC
            """)
    List<PersonUnavailabilityPersonProjection> findAllByDate(@Param("date") LocalDate date);
}
