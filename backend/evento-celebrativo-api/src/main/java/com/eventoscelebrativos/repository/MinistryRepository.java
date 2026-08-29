package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MinistryRepository extends JpaRepository<Ministry, Long> {

    Optional<Ministry> findByNormalizedName(String normalizedName);

    List<Ministry> findByNormalizedNameIn(Collection<String> normalizedNames);

    boolean existsByNormalizedName(String normalizedName);
}
