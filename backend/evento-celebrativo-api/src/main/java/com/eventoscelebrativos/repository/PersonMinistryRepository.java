package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonMinistryRepository extends JpaRepository<PersonMinistry, Long> {

    boolean existsByPersonIdAndMinistryType(Long personId, MinistryType ministryType);

    Optional<PersonMinistry> findByPersonIdAndMinistryType(Long personId, MinistryType ministryType);

    List<PersonMinistry> findAllByPersonId(Long personId);

    void deleteAllByPersonId(Long personId);
}
