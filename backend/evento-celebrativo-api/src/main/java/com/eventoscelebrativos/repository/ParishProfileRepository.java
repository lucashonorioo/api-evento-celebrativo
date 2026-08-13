package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.ParishProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParishProfileRepository extends JpaRepository<ParishProfile, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pp FROM ParishProfile pp WHERE pp.id = :id")
    Optional<ParishProfile> findByIdForUpdate(@Param("id") Long id);
}
