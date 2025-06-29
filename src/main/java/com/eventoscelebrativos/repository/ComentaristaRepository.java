package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Comentarista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComentaristaRepository extends JpaRepository<Comentarista, Long> {
}
