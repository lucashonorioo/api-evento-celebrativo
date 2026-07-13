package com.eventoscelebrativos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpHeaders;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/public")
@Tag(name = "Autenticação", description = "Endpoints públicos de autenticação")
public class PublicController {

    @Value("${security.client-id}")
    private String clientId;

    @Value("${security.client-secret}")
    private String clientSecret;

    @Value("${security.oauth-token-url}")
    private String oauthTokenUrl;

    private final RestTemplate restTemplate = new RestTemplate();


    public static class LoginProxyRequest {
        public String username;
        public String password;
    }

    @Operation(summary = "Realiza login e retorna token de acesso")
    @PostMapping("/login")
    public ResponseEntity<Map> proxyLogin(@RequestBody LoginProxyRequest request) {

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", request.username);
        body.add("password", request.password);
        body.add("grant_type", "password");

        String authString = clientId + ":" + clientSecret;
        String base64Auth = java.util.Base64.getEncoder().encodeToString(authString.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + base64Auth);

        try {
            org.springframework.http.HttpEntity<MultiValueMap<String, String>> entity =
                    new org.springframework.http.HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(oauthTokenUrl, entity, Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (HttpClientErrorException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Collections.singletonMap("error", ex.getResponseBodyAsString()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Collections.singletonMap("error", "Erro interno ao obter token."));
        }
    }
}
