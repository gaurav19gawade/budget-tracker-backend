package com.budgettracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling   // ← ADD THIS
public class BudgetTrackerBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(BudgetTrackerBackendApplication.class, args);
	}

	// Also add this bean if you don't already have a RestTemplate bean:
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
