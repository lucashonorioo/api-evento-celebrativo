package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova, isolada de banco real (Mockito) e de contexto Spring completo, o contrato de
 * {@link AdminRoleMutexService}: o id da role ROLE_ADMIN e resolvido uma unica vez na construcao do
 * bean (nunca por chamada), toda aquisicao trava exclusivamente por chave primaria (nunca por
 * authority, que faria table scan sob FOR UPDATE - ver Javadoc da classe), a posse do mutex e
 * vinculada a transacao Spring atual (nao a um objeto devolvido ao chamador) e e removida quando a
 * transacao termina. Uma transacao Spring real e simulada via {@link TransactionSynchronizationManager}
 * (sem {@code @SpringBootTest}) para manter este teste rapido; o bloqueio real sob concorrencia MySQL
 * e coberto por {@code AdminRoleMutexServiceMySqlIntegrationTest} e o fluxo ponta a ponta com
 * transacao real (H2) por {@code ScheduleConflictReconcileContractTest}.
 */
@ExtendWith(MockitoExtension.class)
class AdminRoleMutexServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @AfterEach
    void ensureNoLeakedFakeTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            completeFakeTransaction();
        }
    }

    @Test
    void shouldResolveAdminRoleIdOnceAtConstructionTime() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));

        new AdminRoleMutexService(roleRepository);

        verify(roleRepository, times(1)).findByAuthority("ROLE_ADMIN");
    }

    @Test
    void shouldThrowClearErrorAtConstructionWhenRoleAdminIsMissing() {
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new AdminRoleMutexService(roleRepository));

        assertEquals("Role ROLE_ADMIN ausente na base de dados.", exception.getMessage());
    }

    @Test
    void shouldLockByPrimaryKeyOnceEachInSeparateTransactions() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);

        for (int i = 0; i < 3; i++) {
            beginFakeTransaction();
            try {
                service.lockAdminRole();
            } finally {
                completeFakeTransaction();
            }
        }

        verify(roleRepository, times(3)).findByIdForUpdate(7L);
        // findByAuthority so pode ter sido chamado na construcao (1x), nunca durante lockAdminRole.
        verify(roleRepository, times(1)).findByAuthority(any());
        verify(roleRepository, never()).findByAuthorityForUpdate(any());
    }

    @Test
    void shouldLockStrictlyAfterResolvingIdOnConstruction() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(adminRole));

        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);
        beginFakeTransaction();
        service.lockAdminRole();

        InOrder inOrder = inOrder(roleRepository);
        inOrder.verify(roleRepository).findByAuthority("ROLE_ADMIN");
        inOrder.verify(roleRepository).findByIdForUpdate(7L);
    }

    @Test
    void shouldThrowClearErrorWhenRoleIsRemovedBetweenConstructionAndLocking() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);
        beginFakeTransaction();

        IllegalStateException exception = assertThrows(IllegalStateException.class, service::lockAdminRole);

        assertEquals("Role ROLE_ADMIN ausente na base de dados.", exception.getMessage());
    }

    @Test
    void shouldBeIdempotentWhenLockingRepeatedlyWithinTheSameTransaction() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);
        beginFakeTransaction();

        service.lockAdminRole();
        service.lockAdminRole();
        service.lockAdminRole();

        verify(roleRepository, times(1)).findByIdForUpdate(7L);
    }

    @Test
    void shouldThrowWhenLockingWithoutAnActiveTransaction() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);

        assertThrows(IllegalStateException.class, service::lockAdminRole);
        verify(roleRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void requireLockedInCurrentTransactionShouldFailWithoutAnActiveTransaction() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);

        assertThrows(IllegalStateException.class, service::requireLockedInCurrentTransaction);
    }

    @Test
    void requireLockedInCurrentTransactionShouldFailWhenTransactionNeverLockedTheMutex() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);
        beginFakeTransaction();

        assertThrows(IllegalStateException.class, service::requireLockedInCurrentTransaction);
    }

    @Test
    void requireLockedInCurrentTransactionShouldPassAfterLockAdminRoleInTheSameTransaction() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);
        beginFakeTransaction();

        service.lockAdminRole();

        service.requireLockedInCurrentTransaction();
    }

    @Test
    void requireLockedInCurrentTransactionShouldRejectStateLeftOverFromAPreviousCompletedTransaction() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);

        beginFakeTransaction();
        service.lockAdminRole();
        completeFakeTransaction();

        beginFakeTransaction();
        assertThrows(IllegalStateException.class, service::requireLockedInCurrentTransaction,
                "O lock adquirido em uma transacao ja concluida nao pode ser aceito em uma nova transacao");
    }

    private void beginFakeTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completeFakeTransaction() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
