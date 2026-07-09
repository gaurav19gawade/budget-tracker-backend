package com.budgettracker.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Configuration
@ConfigurationProperties(prefix = "teller")
@Data
@Slf4j
public class TellerConfig {

    private String baseUrl;
    private Mtls mtls = new Mtls();

    @Data
    public static class Mtls {
        private String keystorePath;
        private String keystoreBase64;
        private String keystorePassword;
        private String keyPassword;
    }

    /**
     * Resolves the keystore file path:
     *   1. keystoreBase64 set (Railway/prod) → decode to a temp file
     *   2. keystorePath set (local dev)       → use directly
     *   3. Neither set                         → return null (no mTLS)
     */
    private String resolveKeystorePath() throws IOException {
        if (StringUtils.hasText(mtls.keystoreBase64)) {
            byte[] decoded = Base64.getDecoder().decode(mtls.keystoreBase64.trim());
            Path tempFile = Files.createTempFile("teller-keystore-", ".p12");
            Files.write(tempFile, decoded);
            tempFile.toFile().deleteOnExit();
            log.info("Teller mTLS: decoded keystore from Base64 to {}", tempFile);
            return tempFile.toAbsolutePath().toString();
        }

        if (StringUtils.hasText(mtls.keystorePath)) {
            log.info("Teller mTLS: using keystore at path {}", mtls.keystorePath);
            return mtls.keystorePath;
        }

        return null; // Neither configured — no mTLS
    }

    @Bean(name = "tellerRestTemplate")
    public RestTemplate tellerRestTemplate() throws IOException {
        String resolvedPath     = resolveKeystorePath();
        String keystorePassword = mtls.getKeystorePassword();

        // Guard: if keystore path or password is missing/blank, skip mTLS entirely.
        // This covers: tests, first-run local dev without Teller credentials, CI.
        if (!StringUtils.hasText(resolvedPath) || !StringUtils.hasText(keystorePassword)) {
            log.warn("Teller mTLS not configured (keystore path or password missing). " +
                    "Using plain RestTemplate — Teller API calls will fail until credentials are set.");
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(45_000);
            return new RestTemplate(factory);
        }

        try {
            char[] keyPass = StringUtils.hasText(mtls.keyPassword)
                    ? mtls.keyPassword.toCharArray()
                    : keystorePassword.toCharArray();

            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(
                            new File(resolvedPath),
                            keystorePassword.toCharArray(),
                            keyPass
                    )
                    .build();

            var sslSocketFactory =
                    new org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory(sslContext);

            var connectionManager =
                    org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(sslSocketFactory)
                            .build();

            var requestConfig = org.apache.hc.client5.http.config.RequestConfig.custom()
                    .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(10))
                    .setResponseTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(45))
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();

            log.info("Teller mTLS RestTemplate initialised successfully.");
            return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));

        } catch (Exception e) {
            // Log the real cause before wrapping, so the startup log is actionable
            log.error("Failed to initialise Teller mTLS RestTemplate: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize Teller mTLS RestTemplate", e);
        }
    }
}