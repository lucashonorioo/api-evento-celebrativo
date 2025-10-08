package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Commentator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentatorRepository extends JpaRepository<Commentator, Long> {
}
