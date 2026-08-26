package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MinistryRepository extends JpaRepository<Ministry, Long> {

    Optional<Ministry> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedName(String normalizedName);
}
