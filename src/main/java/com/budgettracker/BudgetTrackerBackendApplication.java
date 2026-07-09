package com.budgettracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@SpringBootApplication
@EnableScheduling   // ← ADD THIS
public class BudgetTrackerBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(BudgetTrackerBackendApplication.class, args);
	}

	// This bean is what AnthropicCategorizationService actually gets injected
	// with (Spring resolves the ambiguity between this and TellerConfig's
	// "tellerRestTemplate" bean by matching the constructor parameter name
	// "restTemplate" to this bean's name).
	//
	// Explicit timeouts matter here: without them, a slow/hung external API
	// (Anthropic, or anything else using this bean) blocks the calling
	// request indefinitely instead of failing with a clear error — which is
	// exactly what caused the "Categorizing..." step to spin forever.
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder
				.setConnectTimeout(Duration.ofSeconds(10))
				.setReadTimeout(Duration.ofSeconds(45))
				.build();
	}
}
