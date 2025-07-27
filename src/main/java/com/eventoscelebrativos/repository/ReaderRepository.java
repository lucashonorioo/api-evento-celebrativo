package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Reader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeitorRepository extends JpaRepository<Reader, Long> {
}
