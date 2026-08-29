package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinistryAuthorizationServiceImplTest {

    private static final Long PERSON_ID = 10L;
    private static final Long READER_MINISTRY_ID = 10_002L;
    private static final Long COMMENTATOR_MINISTRY_ID = 10_003L;
    private static final Long EUCHARISTIC_MINISTRY_ID = 10_005L;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    private MinistryAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MinistryAuthorizationServiceImpl(
                authenticatedUserResolver,
                personMinistryRepository
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
    void adminCanManageAnyMinistryIdWithoutPersonMinistry() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_ADMIN"));

        assertTrue(service.canManageMinistry(READER_MINISTRY_ID));

        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void operatorCoordinatingRequestedMinistryIdCanManageIt() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(READER_MINISTRY_ID, true);

        assertTrue(service.canManageMinistry(READER_MINISTRY_ID));
    }

    @Test
    void operatorCoordinatingReaderCannotManageCommentator() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(COMMENTATOR_MINISTRY_ID, false);

        assertFalse(service.canManageMinistry(COMMENTATOR_MINISTRY_ID));
    }

    @Test
    void operatorMemberOfMinistryWithoutCoordinationCannotManageIt() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(READER_MINISTRY_ID, false);

        assertFalse(service.canManageMinistry(READER_MINISTRY_ID));
    }

    @Test
    void operatorWithoutAnyPersonMinistryCannotManage() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(READER_MINISTRY_ID, false);

        assertFalse(service.canManageMinistry(READER_MINISTRY_ID));
    }

    @Test
    void operatorCoordinatingMultipleMinistriesOnlyManagesEachCorrespondingOne() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(READER_MINISTRY_ID, true);
        mockCoordinatorStatus(COMMENTATOR_MINISTRY_ID, true);
        mockCoordinatorStatus(EUCHARISTIC_MINISTRY_ID, false);

        assertTrue(service.canManageMinistry(READER_MINISTRY_ID));
        assertTrue(service.canManageMinistry(COMMENTATOR_MINISTRY_ID));
        assertFalse(service.canManageMinistry(EUCHARISTIC_MINISTRY_ID));
    }

    @Test
    void secretaryWithoutCoordinationCannotManageMinistry() {
        // PARISH_SECRETARY nao concede autoridade ministerial: o service nunca consulta
        // ParishStaffAssignment, entao a decisao depende exclusivamente da coordenacao ministerial.
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(READER_MINISTRY_ID, false);

        assertFalse(service.canManageMinistry(READER_MINISTRY_ID));
    }

    @Test
    void pastorWithoutCoordinationCannotManageMinistry() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith("ROLE_OPERATOR"));
        mockCoordinatorStatus(READER_MINISTRY_ID, false);

        assertFalse(service.canManageMinistry(READER_MINISTRY_ID));
    }

    @Test
    void invalidMinistryIdIsRejectedWithoutTouchingCurrentUserOrRepository() {
        assertThrows(BadRequestException.class, () -> service.canManageMinistry(null));
        assertThrows(BadRequestException.class, () -> service.canManageMinistry(0L));
        assertThrows(BadRequestException.class, () -> service.canManageMinistry(-1L));

        verifyNoInteractions(authenticatedUserResolver);
        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void authenticatedUserWithoutAdminOrOperatorAuthorityCannotManage() {
        when(authenticatedUserResolver.requireCurrentUser()).thenReturn(userWith());

        assertFalse(service.canManageMinistry(READER_MINISTRY_ID));

        verifyNoInteractions(personMinistryRepository);
    }

    private void mockCoordinatorStatus(Long ministryId, boolean canManage) {
        when(personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(PERSON_ID, ministryId))
                .thenReturn(canManage);
    }
}
