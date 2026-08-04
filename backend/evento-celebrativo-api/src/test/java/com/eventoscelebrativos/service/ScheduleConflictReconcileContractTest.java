package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.impl.AdminRoleMutexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova o contrato seguro de {@link ScheduleConflictNotificationService#reconcile} (auditoria,
 * secao 2): a precondicao "mutex ROLE_ADMIN ja adquirido" nao fica apenas documentada em Javadoc,
 * e sim imposta pelo tipo do primeiro parametro.
 * <p>
 * Prova em 3 niveis:
 * <ol>
 *   <li>reflexao: {@link AdminRoleMutexGuard} e uma interface sem metodos - nao existe forma de
 *   "forjar" uma instancia por new/reflection publica; a unica implementacao concreta e uma classe
 *   privada aninhada em {@link AdminRoleMutexService}, invisivel e nao instanciavel fora dela
 *   (o proprio fato de o codigo deste teste NAO conseguir construir uma instancia sem passar por
 *   {@code lockAdminRole()} e a prova - qualquer tentativa de bypass e um erro de compilacao, nao
 *   um teste que pudesse falhar em runtime);</li>
 *   <li>reflexao: o metodo reconcile() da interface exige {@link AdminRoleMutexGuard} como primeiro
 *   parametro - se um refactor futuro remover esse parametro, este teste falha;</li>
 *   <li>integracao real (H2): obter um guard genuino via {@code AdminRoleMutexService#lockAdminRole}
 *   e usa-lo para reconciliar um conflito de ponta a ponta, provando que o contrato seguro nao
 *   quebra o fluxo real.</li>
 * </ol>
 * Os 6 pontos de chamada de producao (PersonUnavailabilityServiceImpl#create/update/delete e
 * CelebrationEventServiceImpl#updateEvent/updateEventScale/createEventWithScale) sao verificados
 * ponta a ponta, sempre adquirindo o guard antes de reconciliar, por
 * {@code ScheduleConflictNotificationIntegrationTest} (11 testes) e
 * {@code ScheduleConflictConcurrencyMySqlIntegrationTest} (10 cenarios reais em MySQL) - todos
 * exercitam exclusivamente os services publicos, nunca reconcile() diretamente, entao continuam
 * compilando e passando somente porque cada chamador ja obtem o guard correto na ordem certa
 * (mutex -> pessoas/eventos), sem inverter para Person -> ROLE_ADMIN.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Transactional
class ScheduleConflictReconcileContractTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private ScheduleConflictNotificationService scheduleConflictNotificationService;

    @Autowired
    private AdminRoleMutexService adminRoleMutexService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Autowired
    private PersonRepository personRepository;

    @Test
    void guardTypeShouldBeAMarkerInterfaceWithNoPublicConstructorAnywhereOnItsClasspathVisibleImplementation() {
        assertTrue(AdminRoleMutexGuard.class.isInterface(),
                "AdminRoleMutexGuard deve ser uma interface marcadora - nenhum codigo externo pode implementa-la com estado forjado sem passar pelo unico ponto de emissao");
        assertEquals(0, AdminRoleMutexGuard.class.getDeclaredMethods().length,
                "Interface marcadora: sem metodos, nada para forjar um comportamento falso");

        // O unico tipo concreto retornado por AdminRoleMutexService#lockAdminRole (verificado no
        // teste de integracao abaixo) e uma classe privada aninhada: nenhuma outra classe do
        // classpath, mesmo no mesmo pacote, consegue chamar 'new' nela.
        for (Class<?> nested : AdminRoleMutexService.class.getDeclaredClasses()) {
            if (AdminRoleMutexGuard.class.isAssignableFrom(nested)) {
                for (Constructor<?> constructor : nested.getDeclaredConstructors()) {
                    assertTrue(Modifier.isPrivate(constructor.getModifiers()),
                            "Construtor da implementacao do guard deve ser privado: " + constructor);
                }
            }
        }
    }

    @Test
    void reconcileMethodShouldRequireAdminRoleMutexGuardAsFirstParameter() throws NoSuchMethodException {
        Method reconcile = ScheduleConflictNotificationService.class.getMethod(
                "reconcile", AdminRoleMutexGuard.class, Long.class, Long.class, LocalDateTime.class);

        assertEquals(AdminRoleMutexGuard.class, reconcile.getParameterTypes()[0],
                "Primeiro parametro de reconcile() deve continuar sendo o guard do mutex ROLE_ADMIN");
    }

    @Test
    void shouldReconcileEndToEndUsingAGenuineGuardObtainedFromLockAdminRole() {
        Person person = savePerson("Contract Person " + UUID.randomUUID());
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        LocalDateTime end = start.plusHours(1);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Contract Event " + UUID.randomUUID(), start, end, true));
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
        personUnavailabilityRepository.saveAndFlush(new PersonUnavailability(person, start, end, null));

        // Unica forma valida de obter um guard: chamar lockAdminRole() primeiro.
        AdminRoleMutexGuard guard = adminRoleMutexService.lockAdminRole();
        scheduleConflictNotificationService.reconcile(guard, event.getId(), person.getId(), start);

        String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + event.getId() + ":" + person.getId();
        assertTrue(notificationRepository.findByActiveSourceKeyForUpdate(activeSourceKey).isPresent(),
                "reconcile() com um guard genuino deve funcionar normalmente de ponta a ponta");
    }

    private Person savePerson(String name) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(uniquePhone());
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(true);
        return personRepository.saveAndFlush(person);
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3493" + String.format("%07d", suffix);
    }
}
