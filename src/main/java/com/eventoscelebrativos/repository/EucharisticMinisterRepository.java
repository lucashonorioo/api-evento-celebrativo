package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.EucharisticMinister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EucharisticMinisterRepository extends JpaRepository<EucharisticMinister, Long> {
}
