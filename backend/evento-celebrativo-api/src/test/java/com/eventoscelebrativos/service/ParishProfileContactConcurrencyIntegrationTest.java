package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParishProfileContactUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.ParishProfileUpdateRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Equivalente H2 (sempre executado, sem gating de ambiente externo) de
 * {@link ParishProfileConcurrencyMySqlIntegrationTest#shouldSerializeAdminFullUpdateWithSecretaryContactUpdateWithoutLostUpdate}:
 * prova que o update administrativo completo (PUT /paroquia) e o update restrito de contato da
 * secretaria (PUT /paroquia/contato) disputam o mesmo lock pessimista de
 * {@code findByIdForUpdate(SINGLETON_ID)} e nunca produzem lost update nem estado parcialmente
 * sobrescrito.
 */
@SpringBootTest
class ParishProfileContactConcurrencyIntegrationTest {

    @Autowired
    private ParishProfileService parishProfileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSerializeAdminFullUpdateWithSecretaryContactUpdateWithoutLostUpdate() throws Exception {
        parishProfileService.update(new ParishProfileUpdateRequestDTO(
                "Paróquia Concorrência H2", "Diocese H2", null, null, null, null));

        ParishProfileUpdateRequestDTO adminRequest = new ParishProfileUpdateRequestDTO(
                "Paróquia Concorrência H2", "Diocese H2", "34944440000", null, null, null);
        ParishProfileContactUpdateRequestDTO secretaryRequest = new ParishProfileContactUpdateRequestDTO(
                "34933330000", null, null, null);

        AtomicInteger successes = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable adminTask = () -> {
                ready.countDown();
                await(ready, start);
                parishProfileService.update(adminRequest);
                successes.incrementAndGet();
            };
            Runnable secretaryTask = () -> {
                ready.countDown();
                await(ready, start);
                parishProfileService.updateContact(secretaryRequest);
                successes.incrementAndGet();
            };

            var futureAdmin = executor.submit(adminTask);
            var futureSecretary = executor.submit(secretaryTask);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureAdmin.get(15, TimeUnit.SECONDS);
            futureSecretary.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, successes.get(), "As duas atualizacoes concorrentes devem serializar e concluir com sucesso");

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_profile", Integer.class);
        assertEquals(1, rowCount, "Nunca deve existir mais de uma linha na tabela singleton");

        String finalName = jdbcTemplate.queryForObject("SELECT name FROM tb_parish_profile WHERE id = 1", String.class);
        String finalDiocese = jdbcTemplate.queryForObject("SELECT diocese FROM tb_parish_profile WHERE id = 1", String.class);
        assertEquals("Paróquia Concorrência H2", finalName,
                "updateContact nunca deve alterar o nome, mesmo sob corrida com o update administrativo");
        assertEquals("Diocese H2", finalDiocese,
                "updateContact nunca deve alterar a diocese, mesmo sob corrida com o update administrativo");

        String finalPhone = jdbcTemplate.queryForObject(
                "SELECT institutional_phone FROM tb_parish_profile WHERE id = 1", String.class);
        boolean matchesAdminLast = "34944440000".equals(finalPhone);
        boolean matchesSecretaryLast = "34933330000".equals(finalPhone);
        assertTrue(matchesAdminLast || matchesSecretaryLast,
                "O telefone final deve corresponder integralmente a uma das duas atualizacoes concorrentes, "
                        + "nunca a um estado misto ou corrompido");
    }

    private void await(CountDownLatch ready, CountDownLatch start) {
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
