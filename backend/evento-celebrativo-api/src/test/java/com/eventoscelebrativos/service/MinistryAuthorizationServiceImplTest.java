package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.impl.MinistryAuthorizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinistryAuthorizationServiceImplTest {

    private static final Long PERSON_ID = 10L;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    @Mock
    private LegacyMinistryTypeResolver legacyMinistryTypeResolver;

    private MinistryAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MinistryAuthorizationServiceImpl(
                authenticatedUserResolver,
                personMinistryRepository,
                legacyMinistryTypeResolver
        );
    }

    private AuthenticatedUser userWith(String... authorities) {
        Set<GrantedAuthority> granted = new LinkedHashSet<>();
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }
        return new AuthenticatedUser(1L, PERSON_ID, "34999999999", 0L, granted);
    }

    @Test
    void adminCanManageAnyMinistryTypeWithoutPersonMinistry() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_ADMIN"));

        assertTrue(service.canManageMinistry(MinistryType.READER));

        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void operatorCoordinatingRequestedMinistryTypeCanManageIt() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(MinistryType.READER, true);

        assertTrue(service.canManageMinistry(MinistryType.READER));
    }

    @Test
    void operatorCoordinatingReaderCannotManageCommentator() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(MinistryType.COMMENTATOR, false);

        assertFalse(service.canManageMinistry(MinistryType.COMMENTATOR));
    }

    @Test
    void operatorMemberOfMinistryWithoutCoordinationCannotManageIt() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(MinistryType.READER, false);

        assertFalse(service.canManageMinistry(MinistryType.READER));
    }

    @Test
    void operatorWithoutAnyPersonMinistryCannotManage() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(MinistryType.READER, false);

        assertFalse(service.canManageMinistry(MinistryType.READER));
    }

    @Test
    void operatorCoordinatingMultipleMinistriesOnlyManagesEachCorrespondingOne() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(MinistryType.READER, true);
        mockCoordinatorStatus(MinistryType.COMMENTATOR, true);
        mockCoordinatorStatus(MinistryType.EUCHARISTIC_MINISTER, false);

        assertTrue(service.canManageMinistry(MinistryType.READER));
        assertTrue(service.canManageMinistry(MinistryType.COMMENTATOR));
        assertFalse(service.canManageMinistry(MinistryType.EUCHARISTIC_MINISTER));
    }

    @Test
    void secretaryWithoutCoordinationCannotManageMinistry() {
        // PARISH_SECRETARY nao concede autoridade ministerial: o service nunca consulta
        // ParishStaffAssignment, entao a decisao depende exclusivamente da coordenacao ministerial.
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(MinistryType.READER, false);

        assertFalse(service.canManageMinistry(MinistryType.READER));
    }

    @Test
    void pastorWithoutCoordinationCannotManageMinistry() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(MinistryType.READER, false);

        assertFalse(service.canManageMinistry(MinistryType.READER));
    }

    @Test
    void ministryTypeNullFailsClosedWithoutTouchingResolverOrRepository() {
        assertFalse(service.canManageMinistry(null));

        verifyNoInteractions(authenticatedUserResolver);
        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void authenticatedUserWithoutAdminOrOperatorAuthorityCannotManage() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith());

        assertFalse(service.canManageMinistry(MinistryType.READER));

        verifyNoInteractions(personMinistryRepository);
    }

    private void mockCoordinatorStatus(MinistryType ministryType, boolean canManage) {
        Long ministryId = unitMinistry(ministryType).getId();
        when(legacyMinistryTypeResolver.requireMinistry(ministryType)).thenReturn(unitMinistry(ministryType));
        when(personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(PERSON_ID, ministryId))
                .thenReturn(canManage);
    }
}
