package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByPersonId(Long personId);

    @EntityGraph(attributePaths = {"roles", "roles.role"})
    @Query("SELECT ua FROM UserAccount ua WHERE ua.person.id = :personId")
    Optional<UserAccount> findByPersonIdWithRoles(@Param("personId") Long personId);

    Optional<UserAccount> findByUsername(String username);

    boolean existsByPersonId(Long personId);

    boolean existsByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ua FROM UserAccount ua WHERE ua.person.id = :personId")
    Optional<UserAccount> findByPersonIdForUpdate(@Param("personId") Long personId);

    /**
     * Usada exclusivamente no login: carrega conta, pessoa e roles em uma unica operacao para
     * {@link com.eventoscelebrativos.service.UserAccountAuthenticationService}, evitando N+1 e
     * lazy loading fora da transacao.
     */
    @EntityGraph(attributePaths = {"person", "roles", "roles.role"})
    @Query("SELECT ua FROM UserAccount ua WHERE ua.username = :username")
    Optional<UserAccount> findByUsernameForAuthentication(@Param("username") String username);

    /**
     * Usada exclusivamente na validacao do bearer token (por accountId, nunca por username), para
     * recarregar o estado atual de conta/pessoa/roles em uma unica operacao.
     */
    @EntityGraph(attributePaths = {"person", "roles", "roles.role"})
    @Query("SELECT ua FROM UserAccount ua WHERE ua.id = :accountId")
    Optional<UserAccount> findByIdForAuthentication(@Param("accountId") Long accountId);
}
