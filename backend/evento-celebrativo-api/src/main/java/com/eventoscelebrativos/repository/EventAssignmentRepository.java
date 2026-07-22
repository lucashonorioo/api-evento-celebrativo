package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.EventAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventAssignmentRepository extends JpaRepository<EventAssignment, Long> {

    List<EventAssignment> findAllByEventId(Long eventId);

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

    @Modifying
    @Query("DELETE FROM EventAssignment assignment WHERE assignment.event.id = :eventId")
    void deleteAllByEventId(@Param("eventId") Long eventId);
}
