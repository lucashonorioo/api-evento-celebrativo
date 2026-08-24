package com.eventoscelebrativos.security;

import com.eventoscelebrativos.controller.PublicController;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /public/login} e um proxy HTTP puro que chama {@code /oauth2/token} da propria aplicacao
 * via {@code RestTemplate} (loopback real, nao in-process) - por isso, ao contrario de
 * {@link AuthenticationCutoverIntegrationTest}, este teste precisa de um servidor real. Usa
 * {@code RANDOM_PORT}; apos o servidor subir, a URL interna do proxy e ajustada no bean do teste
 * para a porta efemera realmente vinculada pelo Spring Boot, evitando depender de 8080 ou de uma
 * porta fixa concorrente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicLoginProxyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private PublicController publicController;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void configureTokenUrlForRandomPort() {
        ReflectionTestUtils.setField(
                publicController,
                "oauthTokenUrl",
                "http://localhost:%d/oauth2/token".formatted(port)
        );
    }

    @Test
    void shouldProxySuccessfulLoginAndReturnAccessToken() {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-proxy");

        ResponseEntity<Map> response = testRestTemplate.postForEntity(
                "/public/login", loginRequest(phone, "senha-proxy"), Map.class);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("access_token"));
    }

    @Test
    void shouldProxyInvalidCredentialsWithSameUnderlyingError() {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-proxy");

        ResponseEntity<Map> response = testRestTemplate.postForEntity(
                "/public/login", loginRequest(phone, "senha-errada"), Map.class);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
        assertTrue(String.valueOf(response.getBody().get("error")).contains("invalid_grant"));
    }

    private Object loginRequest(String username, String password) {
        return Map.of("username", username, "password", password);
    }

    private void createPersonWithAccount(String phone, String rawPassword) {
        Person person = new Person("Public Login Proxy Person", phone, BIRTHDAY);
        person.setActive(true);
        Person saved = personRepository.saveAndFlush(person);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(saved, phone, passwordEncoder.encode(rawPassword), now, now);
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        userAccountRoleRepository.saveAndFlush(
                new UserAccountRole(savedAccount, roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow()));
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }
}
