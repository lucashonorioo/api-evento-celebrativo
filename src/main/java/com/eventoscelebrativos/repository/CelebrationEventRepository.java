package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface CelebrationEventRepository extends JpaRepository<CelebrationEvent, Long> {

    @Query(nativeQuery = true, value = """
        SELECT
             ce.name_mass_or_event AS eventName,
             ce.event_date AS eventDate,
             ce.event_time AS eventTime,
             l.church_name AS churchName,
             p.name AS ministerName
        FROM
             tb_celebration_event ce 
        INNER JOIN
              tb_event_location el ON ce.id = el.event_id 
        INNER JOIN
              tb_location l ON l.id = el.location_id 
        INNER JOIN
               tb_event_person ep ON ce.id = ep.event_id 
        INNER JOIN
               tb_person p ON ep.person_id = p.id
        WHERE
               p.type = 'eucharist_minister'
               AND ce.event_date BETWEEN :startDate AND :endDate
        ORDER BY
               ce.name_mass_or_event, ce.event_date
    """)
    Page<EucharistScaleEventProjection> buscarEscalaMinistro(Pageable pageable, LocalDate starDate, LocalDate endDate);

}
