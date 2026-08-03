package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationOrigin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
                    ORDER BY n.createdAt DESC, n.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(n) FROM Notification n
                    WHERE (:origin IS NULL OR n.origin = :origin)
                      AND (:audience IS NULL OR n.audience = :audience)
                      AND (:senderPersonId IS NULL OR n.senderUserAccount.person.id = :senderPersonId)
                      AND (:startInclusive IS NULL OR n.createdAt >= :startInclusive)
                      AND (:endExclusive IS NULL OR n.createdAt < :endExclusive)
                    """
    )
    Page<Notification> findAdminHistory(
            @Param("origin") NotificationOrigin origin,
            @Param("audience") NotificationAudience audience,
            @Param("senderPersonId") Long senderPersonId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );

    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.senderUserAccount sender
            LEFT JOIN FETCH sender.person
            WHERE n.id = :id
            """)
    Optional<Notification> findByIdWithSender(@Param("id") Long id);
}
