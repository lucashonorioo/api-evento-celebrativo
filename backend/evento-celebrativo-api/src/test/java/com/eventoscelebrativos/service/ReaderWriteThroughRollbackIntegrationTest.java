package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class ReaderWriteThroughRollbackIntegrationTest {

    @Autowired
    private ReaderService readerService;

    @Autowired
    private PersonRepository personRepository;

    @MockitoBean
    private PersonMinistryCompatibilityService personMinistryCompatibilityService;

    @Test
    void shouldRollbackReaderCreationWhenMinistryWriteThroughFails() {
        RuntimeException writeThroughFailure = new IllegalStateException("write-through failed");
        doThrow(writeThroughFailure)
                .when(personMinistryCompatibilityService)
                .ensureMinistry(any(Person.class), eq(MinistryType.READER));

        String phoneNumber = uniquePhoneNumber();
        ReaderRequestDTO request = new ReaderRequestDTO(
                "Rollback Reader",
                phoneNumber,
                LocalDate.of(1990, 1, 10),
                "123456"
        );

        RuntimeException result = assertThrows(RuntimeException.class, () -> readerService.createReader(request));

        assertSame(writeThroughFailure, result);
        assertFalse(personRepository.findByPhoneNumber(phoneNumber).isPresent());
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }
}
