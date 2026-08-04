package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.projection.PersonScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.PersonScheduleEventProjection;
import com.eventoscelebrativos.projection.PersonUnavailabilityAssignmentConflictProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface EventAssignmentRepository extends JpaRepository<EventAssignment, Long> {

    List<EventAssignment> findAllByEventId(Long eventId);

    @Query(
            value = """
                    SELECT
                        ce.id AS eventId,
                        ce.name_mass_or_event AS eventName,
                        ce.start_at AS startAt,
                        ce.end_at AS endAt,
                        ce.mass_or_celebration AS massOrCelebration,
                        MIN(l.id) AS locationId,
                        MIN(l.church_name) AS locationName
                    FROM tb_event_assignment ea
                    INNER JOIN tb_celebration_event ce ON ce.id = ea.event_id
                    LEFT JOIN tb_event_location el ON el.event_id = ce.id
                    LEFT JOIN tb_location l ON l.id = el.location_id
                    WHERE ea.person_id = :personId
                    AND ce.start_at < :rangeEnd
                    AND ce.end_at > :rangeStart
                    GROUP BY
                        ce.id,
                        ce.name_mass_or_event,
                        ce.start_at,
                        ce.end_at,
                        ce.mass_or_celebration
                    ORDER BY ce.start_at, ce.id
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT ea.event_id)
                    FROM tb_event_assignment ea
                    INNER JOIN tb_celebration_event ce ON ce.id = ea.event_id
                    WHERE ea.person_id = :personId
                    AND ce.start_at < :rangeEnd
                    AND ce.end_at > :rangeStart
                    """,
            nativeQuery = true)
    Page<PersonScheduleEventProjection> findScheduleEventsByPersonId(
            @Param("personId") Long personId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM EventAssignment a WHERE a.event.id = :eventId AND a.person.id = :personId")
    List<EventAssignment> findAllByEventIdAndPersonIdForUpdate(
            @Param("eventId") Long eventId,
            @Param("personId") Long personId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM EventAssignment a WHERE a.event.id = :eventId")
    List<EventAssignment> findAllByEventIdForUpdate(@Param("eventId") Long eventId);

    @Query("""
            SELECT a.event.id AS eventId, a.event.nameMassOrEvent AS eventName, a.event.startAt AS startAt,
                   a.event.endAt AS endAt, a.assignmentType AS assignmentType
            FROM EventAssignment a
            WHERE a.person.id = :personId
              AND a.event.startAt < :endAt
              AND a.event.endAt > :startAt
            """)
    List<PersonUnavailabilityAssignmentConflictProjection> findAssignmentConflictsByPersonIdAndRange(
            @Param("personId") Long personId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    boolean existsByPersonIdAndAssignmentType(Long personId, EventAssignmentType assignmentType);

    boolean existsByEvent_IdAndPerson_Id(Long eventId, Long personId);

    /**
     * EventIds distintos aos quais a pessoa esta atribuida e cujo evento ainda nao encerrou
     * (endAt > currentSecond), em ordem crescente. Usada apos criar uma indisponibilidade para saber
     * quais eventos reconciliar (secao 12); a verificacao de sobreposicao real e feita pelo proprio
     * reconciliador por evento, nao aqui.
     */
    @Query("""
            SELECT DISTINCT a.event.id
            FROM EventAssignment a
            WHERE a.person.id = :personId
              AND a.event.endAt > :currentSecond
            ORDER BY a.event.id ASC
            """)
    List<Long> findEventIdsByPersonIdAndEndAtAfter(
            @Param("personId") Long personId,
            @Param("currentSecond") LocalDateTime currentSecond
    );

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
            FROM EventAssignment a
            WHERE a.person.id = :personId
              AND a.event.endAt > :currentSecond
            """)
    boolean existsActiveOrFutureAssignmentByPersonId(
            @Param("personId") Long personId,
            @Param("currentSecond") LocalDateTime currentSecond
    );

    // clearAutomatically evita entidades "zumbis": deleteEventById agora carrega os assignments
    // (findAllByEventIdForUpdate) antes deste delete em lote para travá-los, e um DELETE JPQL em
    // lote nao sincroniza o contexto de persistencia sozinho, deixando esses assignments ja
    // excluidos no banco ainda "gerenciados" na sessao - o que quebra o flush seguinte ao remover o
    // proprio evento. Locks JA adquiridos na transacao (linhas de banco) nao sao afetados por este
    // clear, que atua apenas no cache de primeiro nivel do Hibernate. flushAutomatically e
    // obrigatorio junto: sem ele, o clear descartaria sem persistir qualquer alteracao pendente no
    // contexto (ex.: Notification.resolve(...) chamado por deleteEventById antes deste delete).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EventAssignment assignment WHERE assignment.event.id = :eventId")
    void deleteAllByEventId(@Param("eventId") Long eventId);
}
