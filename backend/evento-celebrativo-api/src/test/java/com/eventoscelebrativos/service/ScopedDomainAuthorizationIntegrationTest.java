package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.ParishStaffAssignment;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com beans e banco reais (sem mocks de repository), que MinistryAuthorizationService e
 * ParishAuthorizationService decidem sempre sobre o estado atual do banco: revogar a responsabilidade
 * no meio do teste muda a proxima decisao, sem recriar JWT, sem cache e sem snapshot no principal.
 * Confirma tambem que os beans resolvem pelos nomes estaveis usados em {@code @PreAuthorize} futuro.
 */
@SpringBootTest
class ScopedDomainAuthorizationIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MinistryAuthorizationService ministryAuthorizationService;

    @Autowired
    private ParishAuthorizationService parishAuthorizationService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ministryAuthorizationServiceBeanHasStableName() {
        assertNotNull(applicationContext.getBean("ministryAuthorizationService", MinistryAuthorizationService.class));
    }

    @Test
    void parishAuthorizationServiceBeanHasStableName() {
        assertNotNull(applicationContext.getBean("parishAuthorizationService", ParishAuthorizationService.class));
    }

    @Test
    void revokingCoordinatorImmediatelyRemovesMinistryManagementCapability() {
        Person person = savePerson("Integration Coordinator Person", "34971000401");
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);
        Long ministryId = ministry.getMinistry().getId();

        authenticateAs(person.getId(), "ROLE_OPERATOR");
        assertTrue(ministryAuthorizationService.canManageMinistry(ministryId));

        ministry.revokeCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertFalse(ministryAuthorizationService.canManageMinistry(ministryId));
    }

    @Test
    void revokingSecretaryAssignmentImmediatelyRemovesSecretaryOperationsCapability() {
        Person person = savePerson("Integration Secretary Person", "34971000402");
        ParishStaffAssignment assignment = new ParishStaffAssignment(person, ParishResponsibilityType.PARISH_SECRETARY);
        parishStaffAssignmentRepository.saveAndFlush(assignment);

        authenticateAs(person.getId(), "ROLE_OPERATOR");
        assertTrue(parishAuthorizationService.canPerformSecretaryOperations());

        assignment.deactivate();
        parishStaffAssignmentRepository.saveAndFlush(assignment);

        assertFalse(parishAuthorizationService.canPerformSecretaryOperations());
    }

    private void authenticateAs(Long personId, String... authorities) {
        Set<GrantedAuthority> granted = new LinkedHashSet<>();
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, personId, "34999999999", 0L, granted);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, granted));
    }

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person(name, phoneNumber, LocalDate.of(1990, 1, 10));
        return personRepository.saveAndFlush(person);
    }
}
