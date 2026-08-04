package com.eventoscelebrativos.service;

/**
 * Comprovante de que o mutex central ROLE_ADMIN ja foi adquirido na transacao atual
 * (AdminRoleMutexService#lockAdminRole). Interface marcadora sem membros: a unica implementacao
 * existente e uma classe privada aninhada em AdminRoleMutexService, entao nenhum outro codigo
 * consegue construir uma instancia valida - o unico jeito de obter um guard e chamando
 * lockAdminRole() primeiro. Usado como parametro obrigatorio de
 * {@link ScheduleConflictNotificationService#reconcile} para impedir, em tempo de compilacao, que
 * reconcile seja chamado sem o mutex ja travado (ver Javadoc de AdminRoleMutexService#lockAdminRole).
 */
public interface AdminRoleMutexGuard {
}
