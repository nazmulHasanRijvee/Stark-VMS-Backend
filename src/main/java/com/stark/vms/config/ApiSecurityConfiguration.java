package com.stark.vms.config;

import com.stark.vms.auth.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ApiSecurityConfiguration {

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins}") String configuredOrigins) {
		List<String> allowedOriginPatterns = Arrays.stream(configuredOrigins.split(","))
				.map(String::strip).filter(origin -> !origin.isEmpty()).toList();
		if (allowedOriginPatterns.isEmpty() || allowedOriginPatterns.stream().anyMatch(origin ->
				origin.contains("*") && !origin.equals("http://localhost:*")
						&& !origin.equals("http://127.0.0.1:*"))) {
			throw new IllegalStateException(
					"Configure exact CORS origins; port wildcards are allowed only for local loopback origins.");
		}

		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(allowedOriginPatterns);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", configuration);
		return source;
	}

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtTokenService tokenService,
			ApiSecurityHandlers securityHandlers, CorsConfigurationSource corsConfigurationSource) throws Exception {
		JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenService, securityHandlers);
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(securityHandlers)
						.accessDeniedHandler(securityHandlers))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/register/",
								"/api/v1/auth/login").permitAll()
						.requestMatchers("/actuator/health", "/error").permitAll()
						.requestMatchers("/api/v1/**").authenticated()
						.anyRequest().permitAll())
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
