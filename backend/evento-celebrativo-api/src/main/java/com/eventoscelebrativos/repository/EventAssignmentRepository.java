package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.projection.PersonScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.PersonScheduleEventProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventAssignmentRepository extends JpaRepository<EventAssignment, Long> {

    List<EventAssignment> findAllByEventId(Long eventId);

    @Query(
            value = """
                    SELECT
                        ce.id AS eventId,
                        ce.name_mass_or_event AS eventName,
                        ce.event_date AS eventDate,
                        ce.event_time AS eventTime,
                        ce.mass_or_celebration AS massOrCelebration,
                        MIN(l.id) AS locationId,
                        MIN(l.church_name) AS locationName
                    FROM tb_event_assignment ea
                    INNER JOIN tb_celebration_event ce ON ce.id = ea.event_id
                    LEFT JOIN tb_event_location el ON el.event_id = ce.id
                    LEFT JOIN tb_location l ON l.id = el.location_id
                    WHERE ea.person_id = :personId
                    AND ce.event_date BETWEEN :startDate AND :endDate
                    GROUP BY
                        ce.id,
                        ce.name_mass_or_event,
                        ce.event_date,
                        ce.event_time,
                        ce.mass_or_celebration
                    ORDER BY ce.event_date, ce.event_time, ce.id
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT ea.event_id)
                    FROM tb_event_assignment ea
                    INNER JOIN tb_celebration_event ce ON ce.id = ea.event_id
                    WHERE ea.person_id = :personId
                    AND ce.event_date BETWEEN :startDate AND :endDate
                    """,
            nativeQuery = true)
    Page<PersonScheduleEventProjection> findScheduleEventsByPersonId(
            @Param("personId") Long personId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT
                        ea.event_id AS eventId,
                        ea.assignment_type AS assignmentType
                    FROM tb_event_assignment ea
                    WHERE ea.person_id = :personId
                    AND ea.event_id IN (:eventIds)
                    """,
            nativeQuery = true)
    List<PersonScheduleAssignmentProjection> findAssignmentTypesByPersonIdAndEventIdIn(
            @Param("personId") Long personId,
            @Param("eventIds") Collection<Long> eventIds
    );

    @Query("""
            SELECT assignment
            FROM EventAssignment assignment
            JOIN FETCH assignment.person person
            WHERE assignment.event.id = :eventId
            ORDER BY assignment.event.id, assignment.assignmentType, COALESCE(LOWER(person.name), ''), person.id, assignment.id
            """)
    List<EventAssignment> findAllByEventIdWithPerson(@Param("eventId") Long eventId);

    @Query("""
            SELECT assignment
            FROM EventAssignment assignment
            JOIN FETCH assignment.person person
            WHERE assignment.event.id IN :eventIds
            ORDER BY assignment.event.id, assignment.assignmentType, COALESCE(LOWER(person.name), ''), person.id, assignment.id
            """)
    List<EventAssignment> findAllByEventIdInWithPerson(@Param("eventIds") Collection<Long> eventIds);

    Optional<EventAssignment> findByEventIdAndPersonId(Long eventId, Long personId);

    boolean existsByEventIdAndPersonId(Long eventId, Long personId);

    boolean existsByPersonIdAndAssignmentType(Long personId, EventAssignmentType assignmentType);

    @Modifying
    @Query("DELETE FROM EventAssignment assignment WHERE assignment.event.id = :eventId")
    void deleteAllByEventId(@Param("eventId") Long eventId);
}
