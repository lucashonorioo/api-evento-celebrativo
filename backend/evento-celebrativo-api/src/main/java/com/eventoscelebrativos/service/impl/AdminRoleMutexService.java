package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.AdminRoleMutexGuard;
import org.springframework.stereotype.Service;

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
     * Trava a role ROLE_ADMIN e devolve um comprovante de posse do mutex nesta transacao. O
     * comprovante e o unico parametro aceito por {@link com.eventoscelebrativos.service.ScheduleConflictNotificationService#reconcile}
     * - como {@link AdminRoleMutexGuardToken} so pode ser construido aqui (construtor privado da
     * classe aninhada), nao ha como chamar reconcile sem ter passado por este metodo primeiro
     * (secao 2 da auditoria: precondicao de reconcile deixa de ser apenas documentada em Javadoc).
     */
    public AdminRoleMutexGuard lockAdminRole() {
        roleRepository.findByIdForUpdate(adminRoleId)
                .orElseThrow(() -> new IllegalStateException("Role ROLE_ADMIN ausente na base de dados."));
        return new AdminRoleMutexGuardToken();
    }

    private static final class AdminRoleMutexGuardToken implements AdminRoleMutexGuard {
        private AdminRoleMutexGuardToken() {
        }
    }
}
