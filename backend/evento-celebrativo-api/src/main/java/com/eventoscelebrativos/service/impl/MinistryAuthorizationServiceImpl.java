package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.MinistryAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("ministryAuthorizationService")
public class MinistryAuthorizationServiceImpl implements MinistryAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final MinistryRepository ministryRepository;
    private final PersonMinistryRepository personMinistryRepository;

    public MinistryAuthorizationServiceImpl(
            AuthenticatedUserResolver authenticatedUserResolver,
            MinistryRepository ministryRepository,
            PersonMinistryRepository personMinistryRepository
    ) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.ministryRepository = ministryRepository;
        this.personMinistryRepository = personMinistryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManageMinistry(Long ministryId) {
        if (ministryId == null || ministryId <= 0) {
            throw new BadRequestException("O Id do ministerio deve ser positivo e nao nulo");
        }

        AuthenticatedUser currentUser = authenticatedUserResolver.requireCurrentUser();
        if (hasAuthority(currentUser, ROLE_ADMIN)) {
            return true;
        }
        if (!hasAuthority(currentUser, ROLE_OPERATOR)) {
            return false;
        }
        boolean activeMinistry = ministryRepository.findById(ministryId)
                .map(Ministry::isActive)
                .orElse(false);
        if (!activeMinistry) {
            return false;
        }
        return personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(
                currentUser.personId(), ministryId);
    }

    private boolean hasAuthority(AuthenticatedUser user, String authority) {
        return user.authorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
