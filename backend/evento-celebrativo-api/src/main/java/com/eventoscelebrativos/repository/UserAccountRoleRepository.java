package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.model.UserAccountRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @EntityGraph(attributePaths = {"role", "userAccount", "userAccount.person"})
    @Query("SELECT uar FROM UserAccountRole uar WHERE uar.userAccount.person.id IN :personIds")
    List<UserAccountRole> findByUserAccountPersonIdIn(@Param("personIds") Collection<Long> personIds);

    /**
     * Roles (nomes de authority, ordenados) por personId, em uma unica consulta em lote. Pessoa sem
     * conta simplesmente nao aparece no mapa resultante (tratada pelo chamador como lista vazia).
     */
    default Map<Long, List<String>> findRoleAuthoritiesByPersonIdsGroupedByPerson(Collection<Long> personIds) {
        return findByUserAccountPersonIdIn(personIds).stream()
                .collect(Collectors.groupingBy(
                        uar -> uar.getUserAccount().getPerson().getId(),
                        Collectors.mapping(uar -> uar.getRole().getAuthority(), Collectors.toList())));
    }

    @Modifying
    @Query("DELETE FROM UserAccountRole uar WHERE uar.userAccount.id = :userAccountId")
    void deleteAllByUserAccountId(@Param("userAccountId") Long userAccountId);

    @Query("""
            SELECT COUNT(DISTINCT ua.id)
            FROM UserAccountRole uar
            JOIN uar.userAccount ua
            JOIN ua.person person
            JOIN uar.role role
            WHERE role.authority = 'ROLE_ADMIN'
              AND ua.enabled = TRUE
              AND person.active = TRUE
            """)
    long countEffectiveAdministrators();
}
