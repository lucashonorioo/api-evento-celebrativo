package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityPersonProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonUnavailabilityRepository extends JpaRepository<PersonUnavailability, Long> {

    @Query("""
            SELECT u
            FROM PersonUnavailability u
            WHERE u.person.id = :personId
              AND u.startAt < :queryEndAt
              AND u.endAt > :queryStartAt
            ORDER BY u.startAt ASC, u.endAt ASC, u.id ASC
            """)
    Page<PersonUnavailability> findByPersonIdIntersecting(
            @Param("personId") Long personId,
            @Param("queryStartAt") LocalDateTime queryStartAt,
            @Param("queryEndAt") LocalDateTime queryEndAt,
            Pageable pageable
    );

    Optional<PersonUnavailability> findByIdAndPersonId(Long id, Long personId);

    @Query("""
            SELECT u
            FROM PersonUnavailability u
            WHERE u.person.id = :personId
              AND u.startAt < :endAt
              AND u.endAt > :startAt
            """)
    List<PersonUnavailability> findOverlapping(
            @Param("personId") Long personId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
            SELECT u
            FROM PersonUnavailability u
            WHERE u.person.id = :personId
              AND u.id <> :excludeId
              AND u.startAt < :endAt
              AND u.endAt > :startAt
            """)
    List<PersonUnavailability> findOverlappingExcludingId(
            @Param("personId") Long personId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            SELECT u.person.id AS personId, u.person.name AS personName, u.startAt AS startAt, u.endAt AS endAt
            FROM PersonUnavailability u
            WHERE u.person.id IN :personIds
              AND u.startAt < :endAt
              AND u.endAt > :startAt
            """)
    List<PersonUnavailabilityPersonProjection> findByPersonIdsAndRange(
            @Param("personIds") Collection<Long> personIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
            SELECT u.person.id AS personId, u.person.name AS personName, u.startAt AS startAt, u.endAt AS endAt
            FROM PersonUnavailability u
            WHERE u.startAt < :endAt
              AND u.endAt > :startAt
            ORDER BY u.person.name ASC, u.person.id ASC, u.startAt ASC
            """)
    List<PersonUnavailabilityPersonProjection> findAllByRange(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
