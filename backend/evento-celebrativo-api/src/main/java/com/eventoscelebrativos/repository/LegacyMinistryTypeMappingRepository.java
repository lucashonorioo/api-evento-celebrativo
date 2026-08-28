package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.LegacyMinistryTypeMapping;
import com.eventoscelebrativos.model.MinistryType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LegacyMinistryTypeMappingRepository extends JpaRepository<LegacyMinistryTypeMapping, Long> {

    @EntityGraph(attributePaths = "ministry")
    Optional<LegacyMinistryTypeMapping> findByMinistryType(MinistryType ministryType);

    @EntityGraph(attributePaths = "ministry")
    List<LegacyMinistryTypeMapping> findByMinistryTypeIn(Collection<MinistryType> ministryTypes);

    @EntityGraph(attributePaths = "ministry")
    Optional<LegacyMinistryTypeMapping> findByMinistryId(Long ministryId);

    @EntityGraph(attributePaths = "ministry")
    List<LegacyMinistryTypeMapping> findByMinistryIdIn(Collection<Long> ministryIds);
}
