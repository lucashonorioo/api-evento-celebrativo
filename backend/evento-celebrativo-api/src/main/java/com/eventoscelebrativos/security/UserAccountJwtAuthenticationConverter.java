package com.eventoscelebrativos.security;

import com.eventoscelebrativos.service.UserAccountAuthenticationService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.math.BigInteger;

/**
 * Converte um {@link Jwt} validado (assinatura/expiracao/claims padrao ja verificados pelo resource
 * server) no principal {@link AuthenticatedUser} efetivamente colocado no {@code SecurityContext}.
 * Nunca confia no claim {@code authorities} do token como fonte final de autorizacao: recarrega
 * conta, pessoa e roles atuais por {@code account_id} a cada requisicao autenticada, garantindo que
 * conta desabilitada, pessoa inativa, username alterado ou role alterada tenham efeito imediato,
 * sem esperar a expiracao do token.
 */
public class UserAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String INVALID_BEARER_TOKEN = "Invalid bearer token";

    private final UserAccountAuthenticationService userAccountAuthenticationService;

    public UserAccountJwtAuthenticationConverter(UserAccountAuthenticationService userAccountAuthenticationService) {
        this.userAccountAuthenticationService = userAccountAuthenticationService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long accountId = extractAccountId(jwt);

        AuthenticatedUser authenticatedUser = userAccountAuthenticationService.loadCurrentUser(accountId)
                .filter(user -> usernameStillMatches(jwt, user))
                .orElseThrow(() -> new InvalidBearerTokenException(INVALID_BEARER_TOKEN));

        return new UsernamePasswordAuthenticationToken(authenticatedUser, null, authenticatedUser.authorities());
    }

    private Long extractAccountId(Jwt jwt) {
        Object rawAccountId = jwt.getClaim("account_id");
        if (rawAccountId == null) {
            throw new InvalidBearerTokenException(INVALID_BEARER_TOKEN);
        }
        Long accountId = switch (rawAccountId) {
            case Byte number -> number.longValue();
            case Short number -> number.longValue();
            case Integer number -> number.longValue();
            case Long number -> number;
            case BigInteger number -> {
                if (number.signum() <= 0 || number.bitLength() > 63) {
                    throw new InvalidBearerTokenException(INVALID_BEARER_TOKEN);
                }
                yield number.longValue();
            }
            default -> throw new InvalidBearerTokenException(INVALID_BEARER_TOKEN);
        };
        if (accountId <= 0) {
            throw new InvalidBearerTokenException(INVALID_BEARER_TOKEN);
        }
        return accountId;
    }

    private boolean usernameStillMatches(Jwt jwt, AuthenticatedUser authenticatedUser) {
        String username = authenticatedUser.username();
        return username.equals(jwt.getSubject()) && username.equals(jwt.getClaimAsString("username"));
    }
}
