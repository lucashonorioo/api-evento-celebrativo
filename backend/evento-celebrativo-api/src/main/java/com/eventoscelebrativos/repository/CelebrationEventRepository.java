package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.projection.EventScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CelebrationEventRepository extends JpaRepository<CelebrationEvent, Long> {

    @Query(
            value = """
                    SELECT
                        ce.id AS eventId,
                        ce.name_mass_or_event AS nameMassOrEvent,
                        ce.event_date AS eventDate,
                        ce.event_time AS eventTime,
                        l.church_name AS churchName,
                        GROUP_CONCAT(p.name ORDER BY p.name, p.id) AS ministerNames
                    FROM tb_celebration_event ce
                    INNER JOIN tb_event_location el ON ce.id = el.event_id
                    INNER JOIN tb_location l ON l.id = el.location_id
                    INNER JOIN tb_event_person ep ON ce.id = ep.event_id
                    INNER JOIN tb_person p ON ep.person_id = p.id
                    WHERE p.person_type = 'eucharistic_minister'
                    AND ce.event_date BETWEEN :startDate AND :endDate
                    GROUP BY
                        ce.id,
                        ce.name_mass_or_event,
                        ce.event_date,
                        ce.event_time,
                        l.id,
                        l.church_name
                    ORDER BY ce.event_date, ce.event_time, ce.name_mass_or_event
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT
                            ce.id AS event_id,
                            l.id AS location_id
                        FROM tb_celebration_event ce
                        INNER JOIN tb_event_location el ON ce.id = el.event_id
                        INNER JOIN tb_location l ON l.id = el.location_id
                        INNER JOIN tb_event_person ep ON ce.id = ep.event_id
                        INNER JOIN tb_person p ON ep.person_id = p.id
                        WHERE p.person_type = 'eucharistic_minister'
                        AND ce.event_date BETWEEN :startDate AND :endDate
                        GROUP BY ce.id, l.id
                    ) AS total
                    """,
            nativeQuery = true)
    Page<EucharistScaleEventProjection> findEucharistScale(Pageable pageable, LocalDate startDate, LocalDate endDate);

    @Query(
            value = """
                    SELECT
                        ce.id AS eventId,
                        ce.name_mass_or_event AS nameMassOrEvent,
                        ce.event_date AS eventDate,
                        ce.event_time AS eventTime,
                        l.church_name AS churchName,
                        NULL AS ministerNames
                    FROM tb_celebration_event ce
                    INNER JOIN tb_event_location el ON ce.id = el.event_id
                    INNER JOIN tb_location l ON l.id = el.location_id
                    WHERE ce.event_date BETWEEN :startDate AND :endDate
                    AND EXISTS (
                        SELECT 1
                        FROM tb_event_assignment ea
                        WHERE ea.event_id = ce.id
                        AND ea.assignment_type = 'EUCHARISTIC_MINISTER'
                    )
                    GROUP BY
                        ce.id,
                        ce.name_mass_or_event,
                        ce.event_date,
                        ce.event_time,
                        l.id,
                        l.church_name
                    ORDER BY ce.event_date, ce.event_time, ce.name_mass_or_event
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT
                            ce.id AS event_id,
                            l.id AS location_id
                        FROM tb_celebration_event ce
                        INNER JOIN tb_event_location el ON ce.id = el.event_id
                        INNER JOIN tb_location l ON l.id = el.location_id
                        WHERE ce.event_date BETWEEN :startDate AND :endDate
                        AND EXISTS (
                            SELECT 1
                            FROM tb_event_assignment ea
                            WHERE ea.event_id = ce.id
                            AND ea.assignment_type = 'EUCHARISTIC_MINISTER'
                        )
                        GROUP BY ce.id, l.id
                    ) AS total
                    """,
            nativeQuery = true)
    Page<EucharistScaleEventProjection> findEucharistScaleByAssignments(
            Pageable pageable,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(
            value = """
                    SELECT
                        ea.event_id AS eventId,
                        p.id AS personId,
                        p.name AS personName
                    FROM tb_event_assignment ea
                    INNER JOIN tb_person p ON p.id = ea.person_id
                    WHERE ea.event_id IN (:eventIds)
                    AND ea.assignment_type = 'EUCHARISTIC_MINISTER'
                    ORDER BY ea.event_id, p.name, p.id
                    """,
            nativeQuery = true)
    List<EventScheduleAssignmentProjection> findEucharistScaleAssignmentsByEventIds(
            @Param("eventIds") List<Long> eventIds
    );

    @Query(
            value = """
                    SELECT
                        ce.id AS eventId,
                        ce.name_mass_or_event AS eventName,
                        ce.event_date AS eventDate,
                        ce.event_time AS eventTime,
                        ce.mass_or_celebration AS massOrCelebration,
                        MIN(l.id) AS locationId,
                        MIN(l.church_name) AS churchName
                    FROM tb_celebration_event ce
                    LEFT JOIN tb_event_location el ON ce.id = el.event_id
                    LEFT JOIN tb_location l ON l.id = el.location_id
                    WHERE ce.event_date BETWEEN :startDate AND :endDate
                    AND (
                        :includeUnassigned = TRUE
                        OR EXISTS (
                            SELECT 1
                            FROM tb_event_person ep
                            INNER JOIN tb_person p ON p.id = ep.person_id
                            WHERE ep.event_id = ce.id
                            AND p.person_type = :personType
                        )
                    )
                    GROUP BY
                        ce.id,
                        ce.name_mass_or_event,
                        ce.event_date,
                        ce.event_time,
                        ce.mass_or_celebration
                    ORDER BY ce.event_date, ce.event_time, ce.id
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM tb_celebration_event ce
                    WHERE ce.event_date BETWEEN :startDate AND :endDate
                    AND (
                        :includeUnassigned = TRUE
                        OR EXISTS (
                            SELECT 1
                            FROM tb_event_person ep
                            INNER JOIN tb_person p ON p.id = ep.person_id
                            WHERE ep.event_id = ce.id
                            AND p.person_type = :personType
                        )
                    )
                    """,
            nativeQuery = true)
    Page<EventScheduleEventProjection> findEventScheduleEvents(
            Pageable pageable,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("personType") String personType,
            @Param("includeUnassigned") boolean includeUnassigned
    );

    @Query(
            value = """
                    SELECT
                        ce.id AS eventId,
                        ce.name_mass_or_event AS eventName,
                        ce.event_date AS eventDate,
                        ce.event_time AS eventTime,
                        ce.mass_or_celebration AS massOrCelebration,
                        MIN(l.id) AS locationId,
                        MIN(l.church_name) AS churchName
                    FROM tb_celebration_event ce
                    LEFT JOIN tb_event_location el ON ce.id = el.event_id
                    LEFT JOIN tb_location l ON l.id = el.location_id
                    WHERE ce.event_date BETWEEN :startDate AND :endDate
                    AND (
                        :includeUnassigned = TRUE
                        OR EXISTS (
                            SELECT 1
                            FROM tb_event_assignment ea
                            WHERE ea.event_id = ce.id
                            AND ea.assignment_type = :assignmentType
                        )
                    )
                    GROUP BY
                        ce.id,
                        ce.name_mass_or_event,
                        ce.event_date,
                        ce.event_time,
                        ce.mass_or_celebration
                    ORDER BY ce.event_date, ce.event_time, ce.id
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM tb_celebration_event ce
                    WHERE ce.event_date BETWEEN :startDate AND :endDate
                    AND (
                        :includeUnassigned = TRUE
                        OR EXISTS (
                            SELECT 1
                            FROM tb_event_assignment ea
                            WHERE ea.event_id = ce.id
                            AND ea.assignment_type = :assignmentType
                        )
                    )
                    """,
            nativeQuery = true)
    Page<EventScheduleEventProjection> findEventScheduleEventsByAssignments(
            Pageable pageable,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("assignmentType") String assignmentType,
            @Param("includeUnassigned") boolean includeUnassigned
    );

    @Query(
            value = """
                    SELECT
                        ep.event_id AS eventId,
                        p.id AS personId,
                        p.name AS personName
                    FROM tb_event_person ep
                    INNER JOIN tb_person p ON p.id = ep.person_id
                    WHERE ep.event_id IN (:eventIds)
                    AND p.person_type = :personType
                    ORDER BY ep.event_id, p.name, p.id
                    """,
            nativeQuery = true)
    List<EventScheduleAssignmentProjection> findEventScheduleAssignments(
            @Param("eventIds") List<Long> eventIds,
            @Param("personType") String personType
    );

    @Query(
            value = """
                    SELECT
                        ea.event_id AS eventId,
                        p.id AS personId,
                        p.name AS personName
                    FROM tb_event_assignment ea
                    INNER JOIN tb_person p ON p.id = ea.person_id
                    WHERE ea.event_id IN (:eventIds)
                    AND ea.assignment_type = :assignmentType
                    ORDER BY ea.event_id, p.name, p.id
                    """,
            nativeQuery = true)
    List<EventScheduleAssignmentProjection> findEventScheduleAssignmentsByAssignmentType(
            @Param("eventIds") List<Long> eventIds,
            @Param("assignmentType") String assignmentType
    );

    @Query("""
            SELECT ce
            FROM CelebrationEvent ce
            LEFT JOIN FETCH ce.locations
            WHERE ce.id = :id
            """)
    Optional<CelebrationEvent> findByIdWithLocations(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT ce
            FROM CelebrationEvent ce
            LEFT JOIN FETCH ce.people
            WHERE ce.id = :id
            """)
    Optional<CelebrationEvent> findByIdWithPeople(@Param("id") Long id);

}
