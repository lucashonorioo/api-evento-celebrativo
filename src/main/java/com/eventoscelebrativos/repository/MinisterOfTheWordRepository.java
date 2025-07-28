package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinisterOfTheWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MinisterOfTheWordRepository extends JpaRepository<MinisterOfTheWord, Long> {
}
