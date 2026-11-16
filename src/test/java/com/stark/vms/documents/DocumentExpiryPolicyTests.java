package com.stark.vms.documents;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentExpiryPolicyTests {
	@Test
	void classifiesDhakaDateBoundariesIncludingBothEndsOfThirtyDayWindow() {
		ZoneId dhaka = ZoneId.of("Asia/Dhaka");
		Clock clock = Clock.fixed(Instant.parse("2026-04-30T19:00:00Z"), ZoneId.of("UTC"));
		DocumentExpiryPolicy policy = new DocumentExpiryPolicy(clock, dhaka);
		LocalDate today = LocalDate.of(2026, 5, 1);

		assertEquals(today, policy.today());
		assertEquals(ExpiryState.NO_EXPIRY_DATE, policy.state(null));
		assertEquals(ExpiryState.EXPIRED, policy.state(today.minusDays(1)));
		assertEquals(ExpiryState.EXPIRING_SOON, policy.state(today));
		assertEquals(ExpiryState.EXPIRING_SOON, policy.state(today.plusDays(30)));
		assertEquals(ExpiryState.VALID, policy.state(today.plusDays(31)));
	}
}
