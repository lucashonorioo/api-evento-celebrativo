package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
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
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class CommentatorWriteThroughRollbackIntegrationTest {

    @Autowired
    private CommentatorService commentatorService;

    @Autowired
    private PersonRepository personRepository;

    @MockitoBean
    private PersonMinistryRepository personMinistryRepository;

    @Test
    void shouldRollbackCommentatorCreationWhenMinistryPersistenceFails() {
        RuntimeException ministryPersistenceFailure = new IllegalStateException("ministry persistence failed");
        when(personMinistryRepository.save(any(PersonMinistry.class))).thenThrow(ministryPersistenceFailure);

        String phoneNumber = uniquePhoneNumber();
        CommentatorRequestDTO request = new CommentatorRequestDTO(
                "Rollback Commentator",
                phoneNumber,
                LocalDate.of(1990, 1, 10),
                "123456"
        );

        RuntimeException result = assertThrows(RuntimeException.class, () ->
                commentatorService.createCommentator(request));

        assertSame(ministryPersistenceFailure, result);
        assertFalse(personRepository.findByPhoneNumber(phoneNumber).isPresent());
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }
}
