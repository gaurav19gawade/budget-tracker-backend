package com.budgettracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

// src/main/java/com/budgettracker/config/TellerKeystoreConfig.java
@Configuration
public class TellerKeystoreConfig {

    @Value("${teller.mtls.keystore-base64:}")
    private String keystoreBase64;

    @Value("${teller.mtls.keystore-path:}")
    private String keystorePath;

    @Value("${teller.mtls.keystore-password:}")
    private String keystorePassword;

    /**
     * Resolves the keystore path:
     * - If base64 is set (production/Railway): decode to a temp file
     * - If path is set (local dev): use it directly
     */
    @Bean
    public String resolvedKeystorePath() throws IOException {
        if (keystoreBase64 != null && !keystoreBase64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(keystoreBase64);
            Path tempFile = Files.createTempFile("teller-keystore-", ".p12");
            Files.write(tempFile, decoded);
            tempFile.toFile().deleteOnExit();
            return tempFile.toAbsolutePath().toString();
        }
        return keystorePath; // local dev fallback
    }
}
