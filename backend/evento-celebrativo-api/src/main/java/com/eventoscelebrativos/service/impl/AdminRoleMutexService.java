package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mutex central de notificacoes: trava a linha de {@code ROLE_ADMIN} com PESSIMISTIC_WRITE antes de
 * qualquer envio ADMIN/SYSTEM ou reconciliacao de conflito de escala travar destinatarios,
 * evitando inversao de ordem de locks entre envio manual, envio automatico e comandos de
 * escala/indisponibilidade que ja travam pessoas.
 * <p>
 * O id da role e resolvido uma unica vez na construcao do bean (fora de qualquer transacao de
 * requisicao, no startup da aplicacao) e cacheado: resolver por authority a cada chamada exigiria
 * uma leitura simples (sem lock) antes do FOR UPDATE, o que sob MySQL/InnoDB REPEATABLE READ
 * fixaria o snapshot da transacao de requisicao ANTES de qualquer lock ser adquirido, violando o
 * protocolo "nenhuma leitura simples antes de todos os locks necessarios" usado em todo o projeto e
 * causando leituras obsoletas em validacoes subsequentes (ex.: validateNoOverlap). Travar
 * diretamente por chave primaria (indexada) tambem evita o table scan que uma busca por authority
 * (coluna sem indice) faria sob FOR UPDATE, que travaria todas as linhas de tb_role.
 * <p>
 * A posse do mutex e comprovada por um recurso vinculado a transacao Spring atual via
 * {@link TransactionSynchronizationManager} (nao por um objeto Java devolvido ao chamador): um
 * marker-interface publico como guard anterior podia ser "forjado" por qualquer classe que o
 * implementasse (nada impede {@code new AlgumaClasse() implements AdminRoleMutexGuard {}} em outro
 * pacote), entao nao provava posse real do lock. O recurso e removido automaticamente ao final da
 * transacao (commit ou rollback), nunca sobrevive entre transacoes e e restaurado corretamente por
 * transacoes {@code REQUIRES_NEW} suspensas/retomadas na mesma thread (comportamento padrao do
 * Spring para recursos vinculados a transacao).
 */
@Service
public class AdminRoleMutexService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final RoleRepository roleRepository;
    private final Long adminRoleId;

    public AdminRoleMutexService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        this.adminRoleId = roleRepository.findByAuthority(ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Role ROLE_ADMIN ausente na base de dados."))
                .getId();
    }

    /**
     * Trava a role ROLE_ADMIN e vincula a posse do mutex a transacao Spring atual. Idempotente:
     * chamadas repetidas na mesma transacao nao releem o banco (o lock de linha do InnoDB ja e
     * reentrante dentro da mesma transacao, mas evitar o round-trip extra tambem evita reabrir a
     * discussao sobre reentrancia a cada chamador). Exige uma transacao com sincronizacao ativa
     * (todo chamador de producao e {@code @Transactional}).
     */
    public void lockAdminRole() {
        if (isLockedInCurrentTransaction()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "lockAdminRole() exige uma transacao Spring ativa com sincronizacao habilitada.");
        }

        roleRepository.findByIdForUpdate(adminRoleId)
                .orElseThrow(() -> new IllegalStateException("Role ROLE_ADMIN ausente na base de dados."));

        TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(AdminRoleMutexService.this)) {
                    TransactionSynchronizationManager.unbindResource(AdminRoleMutexService.this);
                }
            }
        });
    }

    /**
     * Precondicao real (nao apenas documentada) de {@link com.eventoscelebrativos.service.ScheduleConflictNotificationService#reconcile}:
     * falha quando nao ha transacao ativa ou quando o mutex nao foi adquirido nesta transacao
     * especifica (o estado de outra transacao, mesmo que ja concluida na mesma thread, nunca e
     * aceito - o recurso e removido no {@code afterCompletion} de cada transacao).
     */
    public void requireLockedInCurrentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Mutex ROLE_ADMIN nao adquirido: nao ha transacao ativa nesta chamada.");
        }
        if (!isLockedInCurrentTransaction()) {
            throw new IllegalStateException(
                    "Mutex ROLE_ADMIN nao adquirido na transacao atual; chame lockAdminRole() antes.");
        }
    }

    private boolean isLockedInCurrentTransaction() {
        return Boolean.TRUE.equals(TransactionSynchronizationManager.getResource(this));
    }
}
