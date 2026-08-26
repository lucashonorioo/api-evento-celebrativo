package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.UserAccount;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByPersonId(Long personId);

    @EntityGraph(attributePaths = {"roles", "roles.role"})
    @Query("SELECT ua FROM UserAccount ua WHERE ua.person.id = :personId")
    Optional<UserAccount> findByPersonIdWithRoles(@Param("personId") Long personId);

    /**
     * FlushMode COMMIT: evita que o Hibernate faca auto-flush da mutacao pendente de username (a
     * propria conta cujo username esta sendo alterado) antes desta consulta, o que faria a constraint
     * unica disparar como DataIntegrityViolationException generica antes da checagem amigavel
     * explicita em {@link com.eventoscelebrativos.service.PersonAccountCoordinator}.
     * <p>
     * Deliberadamente NAO usa lock pessimista: um {@code SELECT ... FOR UPDATE} sobre um username que
     * ainda pode nao pertencer a ninguem tomaria um gap lock do InnoDB, o que pode gerar deadlock
     * genuino quando duas contas disputam o mesmo username novo simultaneamente. A garantia final de
     * unicidade fica a cargo da constraint {@code uk_tb_user_account_username}, verificada no flush
     * explicito do service - esta consulta serve apenas para retornar um erro amigavel no caso comum
     * (sem concorrencia real).
     */
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
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
     * Contas elegiveis para notificacao (habilitadas, com pessoa ativa e exatamente uma role, cuja
     * authority esteja em ROLE_ADMIN/ROLE_OPERATOR). Quando {@code requiredAuthority} e informado,
     * restringe ainda mais as contas cuja unica role possua essa authority (usado pela audience
     * ADMIN); quando nulo, serve a audience GLOBAL.
     */
    @Query("""
            SELECT ua.id AS accountId, ua.person.id AS personId FROM UserAccount ua
            WHERE ua.enabled = TRUE
              AND ua.person.active = TRUE
              AND (SELECT COUNT(uar) FROM UserAccountRole uar WHERE uar.userAccount = ua) = 1
              AND EXISTS (
                  SELECT 1 FROM UserAccountRole uarValid
                  WHERE uarValid.userAccount = ua AND uarValid.role.authority IN ('ROLE_ADMIN', 'ROLE_OPERATOR')
              )
              AND (:requiredAuthority IS NULL OR EXISTS (
                  SELECT 1 FROM UserAccountRole uar2
                  WHERE uar2.userAccount = ua AND uar2.role.authority = :requiredAuthority
              ))
            """)
    List<EligibleAccount> findEligibleAccounts(@Param("requiredAuthority") String requiredAuthority);

    /**
     * Ternas (accountId, personId, ministryId) de contas elegiveis (habilitadas, pessoa ativa,
     * exatamente uma role valida - ROLE_ADMIN ou ROLE_OPERATOR) cuja pessoa possui vinculo ativo com
     * algum dos ministerios informados. Usada tanto para descobrir o conjunto de candidatos da
     * audience MINISTRY quanto para checar, por Ministry persistente, se ha pelo menos um
     * destinatario elegivel.
     */
    @Query("""
            SELECT ua.id AS accountId, ua.person.id AS personId, pm.ministry.id AS ministryId
            FROM UserAccount ua
            JOIN PersonMinistry pm ON pm.person = ua.person
            WHERE ua.enabled = TRUE
              AND ua.person.active = TRUE
              AND pm.active = TRUE
              AND pm.ministry.id IN :ministryIds
              AND (SELECT COUNT(uar) FROM UserAccountRole uar WHERE uar.userAccount = ua) = 1
              AND EXISTS (
                  SELECT 1 FROM UserAccountRole uarValid
                  WHERE uarValid.userAccount = ua AND uarValid.role.authority IN ('ROLE_ADMIN', 'ROLE_OPERATOR')
              )
            """)
    List<EligibleMinistryAccount> findEligibleAccountsByMinistryIds(
            @Param("ministryIds") Collection<Long> ministryIds);

    /**
     * Estado de conta (username, enabled) por personId, em uma unica consulta em lote. Pessoa sem
     * conta simplesmente nao aparece no mapa resultante - usada pela listagem/detalhe administrativos
     * de pessoas para expor accountExists/accountEnabled/username sem N+1.
     */
    @Query("SELECT ua.person.id AS personId, ua.username AS username, ua.enabled AS enabled "
            + "FROM UserAccount ua WHERE ua.person.id IN :personIds")
    List<AccountState> findAccountStatesByPersonIdIn(@Param("personIds") Collection<Long> personIds);

    default Map<Long, AccountState> findAccountStatesByPersonIdInGroupedByPerson(Collection<Long> personIds) {
        return findAccountStatesByPersonIdIn(personIds).stream()
                .collect(Collectors.toMap(AccountState::getPersonId, Function.identity()));
    }

    interface AccountState {
        Long getPersonId();

        String getUsername();

        boolean isEnabled();
    }

    interface EligibleAccount {
        Long getAccountId();

        Long getPersonId();
    }

    interface EligibleMinistryAccount {
        Long getAccountId();

        Long getPersonId();

        Long getMinistryId();
    }
}
