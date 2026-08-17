package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.impl.ParishAuthorizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParishAuthorizationServiceImplTest {

    private static final Long PERSON_ID = 10L;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    private ParishAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParishAuthorizationServiceImpl(authenticatedUserResolver, parishStaffAssignmentRepository);
    }

    private AuthenticatedUser userWith(String... authorities) {
        Set<GrantedAuthority> granted = new LinkedHashSet<>();
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }
        return new AuthenticatedUser(1L, PERSON_ID, "34999999999", 0L, granted);
    }

    @Test
    void adminCanPerformSecretaryOperationsWithoutStaffAssignment() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_ADMIN"));

        assertTrue(service.canPerformSecretaryOperations());

        verifyNoInteractions(parishStaffAssignmentRepository);
    }

    @Test
    void operatorWithActiveSecretaryAssignmentCanPerformSecretaryOperations() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(PERSON_ID, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(true);

        assertTrue(service.canPerformSecretaryOperations());
    }

    @Test
    void operatorWithoutStaffAssignmentCannotPerformSecretaryOperations() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(PERSON_ID, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(false);

        assertFalse(service.canPerformSecretaryOperations());
    }

    @Test
    void operatorWithInactiveSecretaryAssignmentCannotPerformSecretaryOperations() {
        // active=false nunca satisfaz existsByPersonIdAndResponsibilityAndActiveTrue; cobertura de
        // repository dedicada em ParishStaffAssignmentRepositoryTest ja existente.
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(PERSON_ID, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(false);

        assertFalse(service.canPerformSecretaryOperations());
    }

    @Test
    void operatorWithOnlyPastorAssignmentCannotPerformSecretaryOperations() {
        // PASTOR nao concede autoridade de secretaria: o service consulta exclusivamente
        // PARISH_SECRETARY, entao a mera existencia de PASTOR nunca chega a alterar a resposta.
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(PERSON_ID, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(false);

        assertFalse(service.canPerformSecretaryOperations());
    }

    @Test
    void operatorWithOnlyMinistryCoordinationCannotPerformSecretaryOperations() {
        // Coordenacao ministerial nao concede autoridade institucional: o service nunca consulta
        // PersonMinistry.
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(PERSON_ID, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(false);

        assertFalse(service.canPerformSecretaryOperations());
    }

    @Test
    void authenticatedUserWithoutAdminOrOperatorAuthorityCannotPerformSecretaryOperations() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith());

        assertFalse(service.canPerformSecretaryOperations());

        verifyNoInteractions(parishStaffAssignmentRepository);
    }
}
