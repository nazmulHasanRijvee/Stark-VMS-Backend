package com.stark.vms.documents;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class DocumentExpiryPolicy {
	private final Clock clock;
	private final ZoneId zone;

	public DocumentExpiryPolicy(Clock clock, ZoneId businessTimeZone) {
		this.clock = clock;
		this.zone = businessTimeZone;
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

	public LocalDate expiringThrough() {
		return today().plusDays(30);
	}

	public ExpiryState state(LocalDate expiryDate) {
		if (expiryDate == null) return ExpiryState.NO_EXPIRY_DATE;
		LocalDate today = today();
		if (expiryDate.isBefore(today)) return ExpiryState.EXPIRED;
		if (!expiryDate.isAfter(today.plusDays(30))) return ExpiryState.EXPIRING_SOON;
		return ExpiryState.VALID;
	}
}
