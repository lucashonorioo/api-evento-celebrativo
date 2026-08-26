package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class MinistryRepositoryTest {

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindMinistry() {
        Ministry ministry = ministryRepository.saveAndFlush(new Ministry("Acólitos"));

        entityManager.clear();

        Ministry reloaded = ministryRepository.findById(ministry.getId()).orElseThrow();
        assertEquals("Acólitos", reloaded.getName());
        assertEquals("ACOLITOS", reloaded.getNormalizedName());
        assertTrue(reloaded.isActive());
    }

    @Test
    void shouldFindByNormalizedName() {
        Optional<Ministry> ministry = ministryRepository.findByNormalizedName("LEITORES");

        assertTrue(ministry.isPresent());
        assertEquals("Leitores", ministry.orElseThrow().getName());
    }

    @Test
    void shouldCheckExistsByNormalizedName() {
        assertTrue(ministryRepository.existsByNormalizedName("LEITORES"));
        assertFalse(ministryRepository.existsByNormalizedName("ACOLITOS"));
    }

    @Test
    void shouldEnforceUniqueNormalizedName() {
        ministryRepository.saveAndFlush(new Ministry("Acólitos"));

        assertThrows(DataIntegrityViolationException.class,
                () -> ministryRepository.saveAndFlush(new Ministry("Acolitos")));
    }

    @Test
    void shouldFillTimestampsWhenSaving() {
        Ministry ministry = ministryRepository.saveAndFlush(new Ministry("Acolhida"));

        assertNotNull(ministry.getCreatedAt());
        assertNotNull(ministry.getUpdatedAt());
    }

    @Test
    void shouldPersistRename() {
        Ministry ministry = ministryRepository.saveAndFlush(new Ministry("Acólitos"));

        ministry.rename("Acolhida");
        ministryRepository.saveAndFlush(ministry);
        entityManager.clear();

        Ministry reloaded = ministryRepository.findById(ministry.getId()).orElseThrow();
        assertEquals("Acolhida", reloaded.getName());
        assertEquals("ACOLHIDA", reloaded.getNormalizedName());
    }

    @Test
    void shouldPersistStatusChanges() {
        Ministry ministry = ministryRepository.saveAndFlush(new Ministry("Acolhida"));

        ministry.deactivate();
        ministryRepository.saveAndFlush(ministry);
        entityManager.clear();

        Ministry inactive = ministryRepository.findById(ministry.getId()).orElseThrow();
        assertFalse(inactive.isActive());

        inactive.activate();
        ministryRepository.saveAndFlush(inactive);
        entityManager.clear();

        assertTrue(ministryRepository.findById(ministry.getId()).orElseThrow().isActive());
    }

    @Test
    void shouldSeedLegacyMinistries() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_ministry", Long.class);

        assertEquals(5L, count);
    }
}
