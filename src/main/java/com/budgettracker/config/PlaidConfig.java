package com.budgettracker.config;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
@ConfigurationProperties(prefix = "plaid")
@Data
public class PlaidConfig {

    private String clientId;
    private String secret;
    private String environment;
    private String redirectUri;

    @Bean
    public PlaidApi plaidClient() {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", clientId);
        apiKeys.put("secret", secret);

        ApiClient apiClient = new ApiClient(apiKeys);

        // Set the base path based on environment
        String basePath;
        switch (environment.toLowerCase()) {
            case "sandbox":
                basePath = "https://sandbox.plaid.com";
                break;
            case "development":
                basePath = "https://development.plaid.com";
                break;
            case "production":
                basePath = "https://production.plaid.com";
                break;
            default:
                basePath = "https://sandbox.plaid.com";
        }

        apiClient.setPlaidAdapter(basePath);

        return apiClient.createService(PlaidApi.class);
    }
}