package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Priest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriestRepository extends JpaRepository<Priest, Long> {
}
