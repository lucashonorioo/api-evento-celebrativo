package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.MinistryAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean nomeado explicitamente ({@code ministryAuthorizationService}) para uso estável em expressões
 * futuras de {@code @PreAuthorize}. Consulta apenas o principal já validado pelo converter JWT
 * ({@link AuthenticatedUserResolver}) e uma leitura pontual de {@code PersonMinistry}; não recarrega
 * UserAccount/Person/roles (isso já foi feito na entrada da requisição) e não mantém nenhum cache.
 */
@Service("ministryAuthorizationService")
public class MinistryAuthorizationServiceImpl implements MinistryAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PersonMinistryRepository personMinistryRepository;

    public MinistryAuthorizationServiceImpl(
            AuthenticatedUserResolver authenticatedUserResolver,
            PersonMinistryRepository personMinistryRepository
    ) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.personMinistryRepository = personMinistryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManageMinistry(Long ministryId) {
        if (ministryId == null || ministryId <= 0) {
            throw new BadRequestException("O Id do ministério deve ser positivo e não nulo");
        }

        AuthenticatedUser currentUser = authenticatedUserResolver.requireCurrentUser();
        if (hasAuthority(currentUser, ROLE_ADMIN)) {
            return true;
        }
        if (!hasAuthority(currentUser, ROLE_OPERATOR)) {
            return false;
        }
        return personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(
                currentUser.personId(), ministryId);
    }

    private boolean hasAuthority(AuthenticatedUser user, String authority) {
        return user.authorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
