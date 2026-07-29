package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.EventParticipationResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventParticipationResponseRepository extends JpaRepository<EventParticipationResponse, Long> {

    Optional<EventParticipationResponse> findByEventIdAndPersonId(Long eventId, Long personId);

    @Query("""
            SELECT response
            FROM EventParticipationResponse response
            WHERE response.person.id = :personId
            AND response.event.id IN :eventIds
            """)
    List<EventParticipationResponse> findAllByPersonIdAndEventIdIn(
            @Param("personId") Long personId,
            @Param("eventIds") Collection<Long> eventIds
    );

    List<EventParticipationResponse> findAllByEventId(Long eventId);

    @Modifying
    @Query("DELETE FROM EventParticipationResponse response WHERE response.event.id = :eventId")
    void deleteAllByEventId(@Param("eventId") Long eventId);

    @Modifying
    @Query("""
            DELETE FROM EventParticipationResponse response
            WHERE response.event.id = :eventId
            AND response.person.id NOT IN :personIds
            """)
    void deleteAllByEventIdAndPersonIdNotIn(
            @Param("eventId") Long eventId,
            @Param("personIds") Collection<Long> personIds
    );
}
