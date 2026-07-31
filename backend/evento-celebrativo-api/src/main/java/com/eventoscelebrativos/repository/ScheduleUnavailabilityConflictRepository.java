package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.projection.ScheduleConflictUnavailabilityProjection;
import com.eventoscelebrativos.projection.ScheduleUnavailabilityConflictKeyProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Consulta, de forma derivada, os conflitos entre EventAssignment e PersonUnavailability
 * (nenhuma tabela ou estado de conflito e persistido). Como a V12 garante no maximo um
 * EventAssignment por (event_id, person_id), cada linha retornada aqui ja representa uma
 * combinacao evento+pessoa distinta, com um unico assignmentType.
 */
@Repository
public interface ScheduleUnavailabilityConflictRepository extends JpaRepository<EventAssignment, Long> {

    @Query(
            value = """
                    SELECT
                        ea.event_id AS eventId,
                        ce.name_mass_or_event AS eventName,
                        ce.start_at AS eventStartAt,
                        ce.end_at AS eventEndAt,
                        p.id AS personId,
                        p.name AS personName,
                        ea.assignment_type AS assignmentType
                    FROM tb_event_assignment ea
                    INNER JOIN tb_celebration_event ce ON ce.id = ea.event_id
                    INNER JOIN tb_person p ON p.id = ea.person_id
                    WHERE ea.event_id = :eventId
                    AND ce.end_at > :currentSecond
                    AND EXISTS (
                        SELECT 1
                        FROM tb_person_unavailability pu
                        WHERE pu.person_id = ea.person_id
                        AND pu.start_at < ce.end_at
                        AND pu.end_at > ce.start_at
                    )
                    ORDER BY p.name, p.id
                    """,
            nativeQuery = true)
    List<ScheduleUnavailabilityConflictKeyProjection> findConflictsByEventId(
            @Param("eventId") Long eventId,
            @Param("currentSecond") LocalDateTime currentSecond
    );

    @Query(
            value = """
                    SELECT
                        ea.event_id AS eventId,
                        ce.name_mass_or_event AS eventName,
                        ce.start_at AS eventStartAt,
                        ce.end_at AS eventEndAt,
                        p.id AS personId,
                        p.name AS personName,
                        ea.assignment_type AS assignmentType
                    FROM tb_event_assignment ea
                    INNER JOIN tb_celebration_event ce ON ce.id = ea.event_id
                    INNER JOIN tb_person p ON p.id = ea.person_id
                    WHERE ce.start_at < :rangeEnd
                    AND ce.end_at > :rangeStart
                    AND ce.end_at > :currentSecond
                    AND EXISTS (
                        SELECT 1
                        FROM tb_person_unavailability pu
                        WHERE pu.person_id = ea.person_id
                        AND pu.start_at < ce.end_at
                        AND pu.end_at > ce.start_at
                    )
                    ORDER BY ce.start_at, ea.event_id, p.name, p.id
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM tb_event_assignment ea
                    INNER JOIN tb_celebration_event ce ON ce.id = ea.event_id
                    WHERE ce.start_at < :rangeEnd
                    AND ce.end_at > :rangeStart
                    AND ce.end_at > :currentSecond
                    AND EXISTS (
                        SELECT 1
                        FROM tb_person_unavailability pu
                        WHERE pu.person_id = ea.person_id
                        AND pu.start_at < ce.end_at
                        AND pu.end_at > ce.start_at
                    )
                    """,
            nativeQuery = true)
    Page<ScheduleUnavailabilityConflictKeyProjection> findConflictsByRange(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("currentSecond") LocalDateTime currentSecond,
            Pageable pageable
    );

    @Query("""
            SELECT u.person.id AS personId, u.id AS id, u.startAt AS startAt, u.endAt AS endAt
            FROM PersonUnavailability u
            WHERE u.person.id IN :personIds
            ORDER BY u.person.id, u.startAt, u.endAt, u.id
            """)
    List<ScheduleConflictUnavailabilityProjection> findUnavailabilitiesByPersonIdIn(@Param("personIds") Collection<Long> personIds);
}
