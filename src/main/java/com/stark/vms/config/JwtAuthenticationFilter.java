package com.stark.vms.config;

import com.stark.vms.auth.AuthenticatedUser;
import com.stark.vms.auth.JwtTokenService;
import com.stark.vms.auth.ParsedAccessToken;
import com.stark.vms.common.error.InvalidAccessTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private final JwtTokenService tokenService;
	private final ApiSecurityHandlers securityHandlers;

	public JwtAuthenticationFilter(JwtTokenService tokenService, ApiSecurityHandlers securityHandlers) {
		this.tokenService = tokenService;
		this.securityHandlers = securityHandlers;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String tokenValue = authorization.substring(BEARER_PREFIX.length()).trim();
		try {
			ParsedAccessToken token = tokenService.parseAndValidate(tokenValue);
			AuthenticatedUser principal = new AuthenticatedUser(token.userId(), token.email(), token.role());
			UsernamePasswordAuthenticationToken authentication =
					new UsernamePasswordAuthenticationToken(principal, null, List.of());
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		} catch (InvalidAccessTokenException exception) {
			SecurityContextHolder.clearContext();
			securityHandlers.commence(request, response, new BadCredentialsException("Invalid bearer token."));
			return;
		}
		filterChain.doFilter(request, response);
	}
}
