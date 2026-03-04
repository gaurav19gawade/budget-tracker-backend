package com.budgettracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
		// Provide a valid JWT secret so JwtTokenProvider initialises
		"jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
		"jwt.expiration=86400000",

		// Blank out Teller mTLS credentials — TellerConfig guard will skip SSL setup
		// and return a plain RestTemplate, allowing the context to load without certs.
		"teller.mtls.keystore-path=",
		"teller.mtls.keystore-base64=",
		"teller.mtls.keystore-password=",
})
class BudgetTrackerBackendApplicationTests {

	@Test
	void contextLoads() {
		// Verifies the Spring ApplicationContext starts without errors.
		// Teller mTLS is intentionally skipped in tests (no real credentials needed).
	}
}