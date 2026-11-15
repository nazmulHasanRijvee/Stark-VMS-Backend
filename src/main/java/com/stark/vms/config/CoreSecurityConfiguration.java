package com.stark.vms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class CoreSecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(10);
	}

	@Bean
	Clock applicationClock() {
		return Clock.systemUTC();
	}

	@Bean
	ZoneId businessTimeZone(@org.springframework.beans.factory.annotation.Value("${app.business-time-zone:Asia/Dhaka}") String zone) {
		return ZoneId.of(zone);
	}

	@Bean
	UserDetailsService jwtOnlyUserDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("Username/password authentication is not configured.");
		};
	}
}
