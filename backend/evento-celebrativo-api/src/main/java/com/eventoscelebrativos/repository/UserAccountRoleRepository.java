package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.model.UserAccountRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface UserAccountRoleRepository extends JpaRepository<UserAccountRole, UserAccountRoleId> {

    @EntityGraph(attributePaths = "role")
    List<UserAccountRole> findByUserAccountId(Long userAccountId);

    @EntityGraph(attributePaths = "role")
    @Query("SELECT uar FROM UserAccountRole uar WHERE uar.userAccount.id IN :userAccountIds")
    List<UserAccountRole> findByUserAccountIdIn(@Param("userAccountIds") Collection<Long> userAccountIds);

    default Map<Long, List<UserAccountRole>> findByUserAccountIdInGroupedByAccount(Collection<Long> userAccountIds) {
        return findByUserAccountIdIn(userAccountIds).stream()
                .collect(Collectors.groupingBy(uar -> uar.getUserAccount().getId()));
    }
}
