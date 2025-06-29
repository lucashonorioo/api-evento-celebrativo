package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistroDeEucaristia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MinistroDeEucaristiaRepository extends JpaRepository<MinistroDeEucaristia, Long> {
}
