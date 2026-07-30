package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.projection.EventScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CelebrationEventRepository extends JpaRepository<CelebrationEvent, Long> {

    @Query(
            value = """
                    SELECT
                        ce.id AS eventId,
                        ce.name_mass_or_event AS nameMassOrEvent,
                        ce.start_at AS startAt,
                        ce.end_at AS endAt,
                        l.church_name AS churchName,
                        NULL AS ministerNames
                    FROM tb_celebration_event ce
                    INNER JOIN tb_event_location el ON ce.id = el.event_id
                    INNER JOIN tb_location l ON l.id = el.location_id
                    WHERE ce.start_at < :rangeEnd
                    AND ce.end_at > :rangeStart
                    AND EXISTS (
                        SELECT 1
                        FROM tb_event_assignment ea
                        WHERE ea.event_id = ce.id
                        AND ea.assignment_type = 'EUCHARISTIC_MINISTER'
                    )
                    GROUP BY
                        ce.id,
                        ce.name_mass_or_event,
                        ce.start_at,
                        ce.end_at,
                        l.id,
                        l.church_name
                    ORDER BY ce.start_at, ce.name_mass_or_event
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
                        WHERE ce.start_at < :rangeEnd
                        AND ce.end_at > :rangeStart
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
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
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
                        ce.start_at AS startAt,
                        ce.end_at AS endAt,
                        ce.mass_or_celebration AS massOrCelebration,
                        MIN(l.id) AS locationId,
                        MIN(l.church_name) AS churchName
                    FROM tb_celebration_event ce
                    LEFT JOIN tb_event_location el ON ce.id = el.event_id
                    LEFT JOIN tb_location l ON l.id = el.location_id
                    WHERE ce.start_at < :rangeEnd
                    AND ce.end_at > :rangeStart
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
                        ce.start_at,
                        ce.end_at,
                        ce.mass_or_celebration
                    ORDER BY ce.start_at, ce.id
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM tb_celebration_event ce
                    WHERE ce.start_at < :rangeEnd
                    AND ce.end_at > :rangeStart
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
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("assignmentType") String assignmentType,
            @Param("includeUnassigned") boolean includeUnassigned
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ce FROM CelebrationEvent ce WHERE ce.id = :id")
    Optional<CelebrationEvent> findByIdForUpdate(@Param("id") Long id);

}
