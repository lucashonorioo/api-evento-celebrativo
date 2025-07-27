package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Priest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PadreRepository extends JpaRepository<Priest, Long> {
}
