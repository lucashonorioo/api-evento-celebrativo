package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.ParishAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean nomeado explicitamente ({@code parishAuthorizationService}) para uso estável em expressões
 * futuras de {@code @PreAuthorize}. Consulta apenas o principal já validado pelo converter JWT
 * ({@link AuthenticatedUserResolver}) e uma leitura pontual de {@code ParishStaffAssignment}; não
 * recarrega UserAccount/Person/roles e não mantém nenhum cache.
 */
@Service("parishAuthorizationService")
public class ParishAuthorizationServiceImpl implements ParishAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    public ParishAuthorizationServiceImpl(
            AuthenticatedUserResolver authenticatedUserResolver,
            ParishStaffAssignmentRepository parishStaffAssignmentRepository
    ) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.parishStaffAssignmentRepository = parishStaffAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canPerformSecretaryOperations() {
        AuthenticatedUser currentUser = authenticatedUserResolver.requireCurrentUser();
        if (hasAuthority(currentUser, ROLE_ADMIN)) {
            return true;
        }
        if (!hasAuthority(currentUser, ROLE_OPERATOR)) {
            return false;
        }
        return parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(
                currentUser.personId(), ParishResponsibilityType.PARISH_SECRETARY);
    }

    private boolean hasAuthority(AuthenticatedUser user, String authority) {
        return user.authorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
