package com.eventoscelebrativos.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injeta um {@link AuthenticatedUser} real no {@code SecurityContext} do teste, para exercitar
 * fluxos que dependem de {@link AuthenticatedUserResolver} (principal completo, nao o
 * {@code UserDetails} legado que {@code @WithMockUser} produz). Use {@code @WithMockUser} apenas em
 * testes que verificam somente autorizacao (403/401) e nao chamam o resolver.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockAuthenticatedUserSecurityContextFactory.class)
public @interface WithMockAuthenticatedUser {

    long accountId() default 1L;

    long personId() default 1L;

    String username() default "34999999999";

    String[] authorities() default {"ROLE_OPERATOR"};
}
