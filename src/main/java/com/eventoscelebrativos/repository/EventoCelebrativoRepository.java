package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.EventoCelebrativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoCelebrativoRepository extends JpaRepository<EventoCelebrativo, Long> {

}
