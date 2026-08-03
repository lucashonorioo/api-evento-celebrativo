package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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
    @Query("SELECT ua FROM UserAccount ua WHERE ua.username = :username")
    Optional<UserAccount> findByUsernameForUpdate(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"roles", "roles.role"})
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

    /**
     * Usada pelo envio de notificacoes para bloquear uma conta especifica (em ordem crescente de
     * accountId) e revalidar seu estado apos o lock, com roles carregadas para checar o invariante
     * de role unica sem consultar {@code Person.roles}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"person", "roles", "roles.role"})
    @Query("SELECT ua FROM UserAccount ua WHERE ua.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);

    /**
     * Contas elegiveis para notificacao (habilitadas, com pessoa ativa e exatamente uma role).
     * Quando {@code requiredAuthority} e informado, restringe as contas cuja unica role possua essa
     * authority (usado pela audience ADMIN); quando nulo, serve a audience GLOBAL.
     */
    @Query("""
            SELECT ua.id AS accountId, ua.person.id AS personId FROM UserAccount ua
            WHERE ua.enabled = TRUE
              AND ua.person.active = TRUE
              AND (SELECT COUNT(uar) FROM UserAccountRole uar WHERE uar.userAccount = ua) = 1
              AND (:requiredAuthority IS NULL OR EXISTS (
                  SELECT 1 FROM UserAccountRole uar2
                  WHERE uar2.userAccount = ua AND uar2.role.authority = :requiredAuthority
              ))
            """)
    List<EligibleAccount> findEligibleAccounts(@Param("requiredAuthority") String requiredAuthority);

    /**
     * Ternas (accountId, personId, ministryType) de contas elegiveis (habilitadas, pessoa ativa,
     * exatamente uma role) cuja pessoa possui vinculo ativo com algum dos ministerios informados.
     * Usada tanto para descobrir o conjunto de candidatos da audience MINISTRY quanto para checar,
     * por tipo, se ha pelo menos um destinatario elegivel.
     */
    @Query("""
            SELECT ua.id AS accountId, ua.person.id AS personId, pm.ministryType AS ministryType
            FROM UserAccount ua
            JOIN PersonMinistry pm ON pm.person = ua.person
            WHERE ua.enabled = TRUE
              AND ua.person.active = TRUE
              AND pm.active = TRUE
              AND pm.ministryType IN :ministryTypes
              AND (SELECT COUNT(uar) FROM UserAccountRole uar WHERE uar.userAccount = ua) = 1
            """)
    List<EligibleMinistryAccount> findEligibleAccountsByMinistryTypes(
            @Param("ministryTypes") Collection<MinistryType> ministryTypes);

    interface EligibleAccount {
        Long getAccountId();

        Long getPersonId();
    }

    interface EligibleMinistryAccount {
        Long getAccountId();

        Long getPersonId();

        MinistryType getMinistryType();
    }
}
