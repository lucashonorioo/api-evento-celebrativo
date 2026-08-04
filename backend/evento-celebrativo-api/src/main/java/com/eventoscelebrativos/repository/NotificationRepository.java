package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationOrigin;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Filtros do historico administrativo, todos opcionais e combinados com AND. O intervalo de
     * datas ja chega resolvido em {@code [startInclusive, endExclusive)}. sender e sender.person sao
     * carregados com LEFT JOIN FETCH (senderUserAccount e nulo para SYSTEM) para evitar N+1 ao montar
     * senderPersonId por linha; nao ha join para colecoes one-to-many, entao a paginacao aqui e sempre
     * correta. Contagens agregadas de destinatarios sao feitas em consulta em lote separada.
     */
    @Query(
            value = """
                    SELECT n FROM Notification n
                    LEFT JOIN FETCH n.senderUserAccount sender
                    LEFT JOIN FETCH sender.person
                    WHERE (:origin IS NULL OR n.origin = :origin)
                      AND (:audience IS NULL OR n.audience = :audience)
                      AND (:senderPersonId IS NULL OR sender.person.id = :senderPersonId)
                      AND (:startInclusive IS NULL OR n.createdAt >= :startInclusive)
                      AND (:endExclusive IS NULL OR n.createdAt < :endExclusive)
                      AND (:categoryFilter IS NULL OR n.category = :categoryFilter)
                      AND (:resolvedFilter IS NULL
                           OR (:resolvedFilter = TRUE AND n.resolvedAt IS NOT NULL)
                           OR (:resolvedFilter = FALSE AND n.resolvedAt IS NULL))
                    ORDER BY n.createdAt DESC, n.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(n) FROM Notification n
                    WHERE (:origin IS NULL OR n.origin = :origin)
                      AND (:audience IS NULL OR n.audience = :audience)
                      AND (:senderPersonId IS NULL OR n.senderUserAccount.person.id = :senderPersonId)
                      AND (:startInclusive IS NULL OR n.createdAt >= :startInclusive)
                      AND (:endExclusive IS NULL OR n.createdAt < :endExclusive)
                      AND (:categoryFilter IS NULL OR n.category = :categoryFilter)
                      AND (:resolvedFilter IS NULL
                           OR (:resolvedFilter = TRUE AND n.resolvedAt IS NOT NULL)
                           OR (:resolvedFilter = FALSE AND n.resolvedAt IS NULL))
                    """
    )
    Page<Notification> findAdminHistory(
            @Param("origin") NotificationOrigin origin,
            @Param("audience") NotificationAudience audience,
            @Param("senderPersonId") Long senderPersonId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("categoryFilter") NotificationCategory categoryFilter,
            @Param("resolvedFilter") Boolean resolvedFilter,
            Pageable pageable
    );

    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.senderUserAccount sender
            LEFT JOIN FETCH sender.person
            WHERE n.id = :id
            """)
    Optional<Notification> findByIdWithSender(@Param("id") Long id);

    /**
     * Usada pelo reconciliador (secao 9) e pela exclusao de evento para carregar, com lock, a
     * ocorrencia ativa (nao resolvida) de conflito de escala para uma identidade eventId+personId.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.activeSourceKey = :activeSourceKey")
    Optional<Notification> findByActiveSourceKeyForUpdate(@Param("activeSourceKey") String activeSourceKey);

    /**
     * Usada pelo scheduler de resolucao temporal para travar uma notificacao especifica antes de
     * resolve-la (evento ja encerrado ou ausente).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);

    /**
     * Ocorrencias ativas de conflito de escala referentes a um evento especifico (referenceType =
     * CELEBRATION_EVENT, referenceId = eventId). Usada na exclusao de evento, para resolver todas
     * antes de excluir o evento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT n FROM Notification n
            WHERE n.category = com.eventoscelebrativos.model.NotificationCategory.SCHEDULE_CONFLICT
              AND n.resolvedAt IS NULL
              AND n.referenceType = :referenceType
              AND n.referenceId = :referenceId
            """)
    List<Notification> findActiveScheduleConflictsByReferenceId(
            @Param("referenceType") String referenceType,
            @Param("referenceId") Long referenceId
    );

    /**
     * Ocorrencias ativas de conflito de escala envolvendo uma pessoa especifica. Nao ha coluna
     * personId em Notification; o sourceKey desta categoria e sempre "eventId:personId" (secao 4),
     * entao o sufixo ":personId" identifica a pessoa sem ambiguidade (o separador ':' impede colisao
     * entre eventId e personId). referenceId (CELEBRATION_EVENT) fornece o eventId de cada linha
     * diretamente, sem precisar reanalisar o sourceKey. Usada na integracao de indisponibilidade
     * (uniao de eventos afetados).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT n FROM Notification n
            WHERE n.category = com.eventoscelebrativos.model.NotificationCategory.SCHEDULE_CONFLICT
              AND n.resolvedAt IS NULL
              AND n.sourceKey LIKE CONCAT('%:', :personId)
            """)
    List<Notification> findActiveScheduleConflictsByPersonId(@Param("personId") Long personId);

    /**
     * Leitura em lote (sem lock) para o scheduler de resolucao temporal; o lock e adquirido por item
     * em uma transacao curta separada.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.category = com.eventoscelebrativos.model.NotificationCategory.SCHEDULE_CONFLICT
              AND n.resolvedAt IS NULL
            ORDER BY n.id ASC
            """)
    List<Notification> findActiveScheduleConflictsBatch(Pageable pageable);
}
