package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MinistryRepository extends JpaRepository<Ministry, Long> {

    Optional<Ministry> findByNormalizedName(String normalizedName);

    List<Ministry> findByNormalizedNameIn(Collection<String> normalizedNames);

    boolean existsByNormalizedName(String normalizedName);

    List<Ministry> findAllByOrderByNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Ministry m WHERE m.id = :id")
    Optional<Ministry> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM Ministry m
            WHERE m.id IN :ids
            ORDER BY m.id ASC
            """)
    List<Ministry> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
