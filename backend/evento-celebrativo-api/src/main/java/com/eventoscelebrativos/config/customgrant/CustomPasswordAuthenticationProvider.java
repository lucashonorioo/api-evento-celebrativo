package com.eventoscelebrativos.config.customgrant;

import java.security.Principal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.service.UserAccountAuthenticationService;
import com.eventoscelebrativos.service.UserAccountCredentials;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;

public class CustomPasswordAuthenticationProvider implements AuthenticationProvider {

	private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";
	// Hash BCrypt fixo e valido (nao gerado por senha real de producao) usado apenas para igualar o
	// tempo de resposta entre "username inexistente" e "senha incorreta"; nunca recalculado por
	// tentativa.
	private static final String DUMMY_PASSWORD_HASH = "$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW";

	private final OAuth2AuthorizationService authorizationService;
	private final UserAccountAuthenticationService userAccountAuthenticationService;
	private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
	private final PasswordEncoder passwordEncoder;

	public CustomPasswordAuthenticationProvider(OAuth2AuthorizationService authorizationService,
												OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
												UserAccountAuthenticationService userAccountAuthenticationService,
												PasswordEncoder passwordEncoder) {

		Assert.notNull(authorizationService, "authorizationService cannot be null");
		Assert.notNull(tokenGenerator, "TokenGenerator cannot be null");
		Assert.notNull(userAccountAuthenticationService, "UserAccountAuthenticationService cannot be null");
		Assert.notNull(passwordEncoder, "PasswordEncoder cannot be null");
		this.authorizationService = authorizationService;
		this.tokenGenerator = tokenGenerator;
		this.userAccountAuthenticationService = userAccountAuthenticationService;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {

		CustomPasswordAuthenticationToken customPasswordAuthenticationToken = (CustomPasswordAuthenticationToken) authentication;
		OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClientElseThrowInvalidClient(customPasswordAuthenticationToken);
		RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

		String username = customPasswordAuthenticationToken.getUsername();
		String password = customPasswordAuthenticationToken.getPassword();

		Optional<UserAccountCredentials> credentialsOptional = userAccountAuthenticationService.loadCredentialsByUsername(username);
		String hashToCompare = credentialsOptional.map(UserAccountCredentials::passwordHash).orElse(DUMMY_PASSWORD_HASH);
		boolean passwordMatches = passwordEncoder.matches(password, hashToCompare);

		boolean validCredentials = credentialsOptional.isPresent()
				&& passwordMatches
				&& credentialsOptional.get().accountEnabled()
				&& credentialsOptional.get().personActive();

		if (!validCredentials) {
			throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "Invalid credentials", ERROR_URI));
		}

		UserAccountCredentials credentials = credentialsOptional.get();
		AuthenticatedUser authenticatedUser = new AuthenticatedUser(
				credentials.accountId(),
				credentials.personId(),
				credentials.username(),
				credentials.authorities()
		);

		Set<String> requestedScopes = customPasswordAuthenticationToken.getScopes();

		Set<String> availableScopes = authenticatedUser.authorities().stream()
				.map(scope -> scope.getAuthority())
				.collect(Collectors.toSet());

		Set<String> authorizedScopes = new HashSet<>(requestedScopes);
		authorizedScopes.retainAll(registeredClient.getScopes());
		authorizedScopes.retainAll(availableScopes);

		if (authorizedScopes.isEmpty() && !requestedScopes.isEmpty()) {
			throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE, "Invalid scope(s) for user", ERROR_URI));
		}

		UsernamePasswordAuthenticationToken userAuthentication = new UsernamePasswordAuthenticationToken(
				authenticatedUser,
				null,
				authenticatedUser.authorities()
		);

		//-----------TOKEN BUILDERS----------
		DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
				.registeredClient(registeredClient)
				.principal(userAuthentication)
				.authorizationServerContext(AuthorizationServerContextHolder.getContext())
				.authorizedScopes(authorizedScopes)
				.authorizationGrantType(new AuthorizationGrantType("password"))
				.authorizationGrant(customPasswordAuthenticationToken);

		OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
				.attribute(Principal.class.getName(), userAuthentication)
				.principalName(userAuthentication.getName())
				.authorizationGrantType(new AuthorizationGrantType("password"))
				.authorizedScopes(authorizedScopes);

		//-----------ACCESS TOKEN----------
		OAuth2TokenContext tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
		OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);
		if (generatedAccessToken == null) {
			OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
					"The token generator failed to generate the access token.", ERROR_URI);
			throw new OAuth2AuthenticationException(error);
		}

		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(),
				generatedAccessToken.getExpiresAt(), tokenContext.getAuthorizedScopes());

		if (generatedAccessToken instanceof ClaimAccessor) {
			authorizationBuilder.token(accessToken, (metadata) ->
					metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, ((ClaimAccessor) generatedAccessToken).getClaims()));
		} else {
			authorizationBuilder.accessToken(accessToken);
		}

		OAuth2Authorization authorization = authorizationBuilder.build();
		this.authorizationService.save(authorization);

		return new SanitizedAccessTokenAuthenticationToken(registeredClient, userAuthentication, accessToken);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return CustomPasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

	private static OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(Authentication authentication) {

		OAuth2ClientAuthenticationToken clientPrincipal = null;
		if (OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication.getPrincipal().getClass())) {
			clientPrincipal = (OAuth2ClientAuthenticationToken) authentication.getPrincipal();
		}
		if (clientPrincipal != null && clientPrincipal.isAuthenticated()) {
			return clientPrincipal;
		}
		throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
	}

	private static final class SanitizedAccessTokenAuthenticationToken extends OAuth2AccessTokenAuthenticationToken {

		private final AuthenticatedUser authenticatedUser;

		private SanitizedAccessTokenAuthenticationToken(
				RegisteredClient registeredClient,
				UsernamePasswordAuthenticationToken userAuthentication,
				OAuth2AccessToken accessToken
		) {
			super(registeredClient, userAuthentication, accessToken);
			this.authenticatedUser = (AuthenticatedUser) userAuthentication.getPrincipal();
			setDetails(userAuthentication.getDetails());
		}

		@Override
		public Object getPrincipal() {
			return authenticatedUser;
		}

		@Override
		public Object getCredentials() {
			return null;
		}
	}
}
