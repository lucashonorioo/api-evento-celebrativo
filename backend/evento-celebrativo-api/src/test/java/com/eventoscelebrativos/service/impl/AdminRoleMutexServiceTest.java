package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.AdminRoleMutexGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova, isolada de banco real (Mockito), o contrato de {@link AdminRoleMutexService}: o id da role
 * ROLE_ADMIN e resolvido uma unica vez na construcao do bean (nunca por chamada) e toda aquisicao
 * subsequente trava exclusivamente por chave primaria, nunca por authority (que faria table scan
 * sob FOR UPDATE, ver Javadoc da classe). O bloqueio real sob concorrencia MySQL e coberto por
 * {@code AdminRoleMutexServiceMySqlIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AdminRoleMutexServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Test
    void shouldResolveAdminRoleIdOnceAtConstructionTime() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));

        new AdminRoleMutexService(roleRepository);

        verify(roleRepository, times(1)).findByAuthority("ROLE_ADMIN");
    }

    @Test
    void shouldLockOnlyByPrimaryKeyAndNeverScanByAuthorityOnEachCall() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(adminRole));
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);

        AdminRoleMutexGuard first = service.lockAdminRole();
        AdminRoleMutexGuard second = service.lockAdminRole();
        AdminRoleMutexGuard third = service.lockAdminRole();

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
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
        service.lockAdminRole();

        InOrder inOrder = inOrder(roleRepository);
        inOrder.verify(roleRepository).findByAuthority("ROLE_ADMIN");
        inOrder.verify(roleRepository).findByIdForUpdate(7L);
    }

    @Test
    void shouldThrowClearErrorAtConstructionWhenRoleAdminIsMissing() {
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new AdminRoleMutexService(roleRepository));

        assertEquals("Role ROLE_ADMIN ausente na base de dados.", exception.getMessage());
    }

    @Test
    void shouldThrowClearErrorWhenRoleIsRemovedBetweenConstructionAndLocking() {
        Role adminRole = new Role(7L, "ROLE_ADMIN");
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        AdminRoleMutexService service = new AdminRoleMutexService(roleRepository);

        IllegalStateException exception = assertThrows(IllegalStateException.class, service::lockAdminRole);

        assertEquals("Role ROLE_ADMIN ausente na base de dados.", exception.getMessage());
    }
}
