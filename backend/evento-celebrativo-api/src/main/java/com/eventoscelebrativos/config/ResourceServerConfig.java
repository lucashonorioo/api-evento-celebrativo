package com.eventoscelebrativos.config;

import java.util.Arrays;

import com.eventoscelebrativos.security.UserAccountJwtAuthenticationConverter;
import com.eventoscelebrativos.service.UserAccountAuthenticationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

	@Value("${cors.origins}")
	private String corsOrigins;

	@Bean
	@Profile({ "test", "local" })
	@Order(1)
	public SecurityFilterChain h2SecurityFilterChain(HttpSecurity http) throws Exception {

		http.securityMatcher(PathRequest.toH2Console()).csrf(csrf -> csrf.disable())
				.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));
		return http.build();
	}

	@Bean
	@Order(3)
	public SecurityFilterChain rsSecurityFilterChain(
			HttpSecurity http,
			Converter<Jwt, AbstractAuthenticationToken> userAccountJwtAuthenticationConverter
	) throws Exception {
		http.csrf(csrf -> csrf.disable());

		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/public/login").permitAll()
				.requestMatchers(HttpMethod.GET, "/eventos/escala/eucaristia").permitAll()
				.requestMatchers(HttpMethod.GET, "/eventos/escalas").authenticated()
				.requestMatchers(HttpMethod.GET, "/eventos/*/escala/participacoes").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/eventos/*/escala").authenticated()
				.requestMatchers(HttpMethod.GET, "/eventos", "/eventos/{id}").permitAll()
				.requestMatchers(HttpMethod.GET, "/paroquia").permitAll()
				.requestMatchers(HttpMethod.PUT, "/paroquia").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/paroquia/equipe").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/paroquia/equipe/pastor/*").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/paroquia/equipe/pastor/*").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/paroquia/equipe/secretarios/*").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/paroquia/equipe/secretarios/*").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/pessoas/me").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.PUT, "/pessoas/me").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.PUT, "/pessoas/me/conta/senha").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.GET, "/pessoas/me/escalas").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.PUT, "/pessoas/me/escalas/*/participacao").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.GET, "/pessoas/me/indisponibilidades").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.POST, "/pessoas/me/indisponibilidades").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.PUT, "/pessoas/me/indisponibilidades/*").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.DELETE, "/pessoas/me/indisponibilidades/*").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.GET, "/pessoas/indisponibilidades").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/pessoas/*/conta").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.POST, "/pessoas/*/conta").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/pessoas/*/conta/habilitacao").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/pessoas/*/conta/senha").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/pessoas/*/status").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/pessoas", "/pessoas/*").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/pessoas/*").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/pessoas/*/roles").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/pessoas/*/ministries").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/pessoas/*/ministries").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/pessoas/*/ministries/*/coordinator").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/pessoas/*/ministries/*/coordinator").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/pessoas/*/responsabilidades-paroquiais").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.POST, "/notificacoes").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/notificacoes/nao-lidas/contagem").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.PUT, "/notificacoes/leitura/todas").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.PUT, "/notificacoes/*/leitura").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.GET, "/notificacoes", "/notificacoes/*").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
				.requestMatchers(HttpMethod.GET, "/admin/notificacoes/*/destinatarios").hasAuthority("ROLE_ADMIN")
				.requestMatchers(HttpMethod.GET, "/admin/notificacoes", "/admin/notificacoes/*").hasAuthority("ROLE_ADMIN")
				.anyRequest().authenticated());
		http.oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(userAccountJwtAuthenticationConverter)));
		http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
		return http.build();
	}

	/**
	 * Conectado explicitamente em {@link #rsSecurityFilterChain} (nao depende de autodeteccao de
	 * bean pelo {@code JwtConfigurer}): recarrega conta/pessoa/roles atuais por {@code account_id}
	 * a cada requisicao autenticada com bearer token.
	 */
	@Bean
	public Converter<Jwt, AbstractAuthenticationToken> userAccountJwtAuthenticationConverter(
			UserAccountAuthenticationService userAccountAuthenticationService
	) {
		return new UserAccountJwtAuthenticationConverter(userAccountAuthenticationService);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {

		String[] origins = corsOrigins.split(",");

		CorsConfiguration corsConfig = new CorsConfiguration();
		corsConfig.setAllowedOriginPatterns(Arrays.asList(origins));
		corsConfig.setAllowedMethods(Arrays.asList("POST", "GET", "PUT", "DELETE", "PATCH"));
		corsConfig.setAllowCredentials(true);
		corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", corsConfig);
		return source;
	}

	@Bean
	FilterRegistrationBean<CorsFilter> filterRegistrationBeanCorsFilter() {
		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(
				new CorsFilter(corsConfigurationSource()));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return bean;
	}
}
