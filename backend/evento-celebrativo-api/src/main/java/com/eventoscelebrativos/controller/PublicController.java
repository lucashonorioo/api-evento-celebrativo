package com.eventoscelebrativos.controller;

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
@CrossOrigin(origins = "http://localhost:4200")
public class PublicController {

    @Value("${security.client-id}")
    private String clientId;

    @Value("${security.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();


    public static class LoginProxyRequest {
        public String username;
        public String password;
    }

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
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://localhost:8080/oauth2/token", entity, Map.class);
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