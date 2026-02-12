package com.budgettracker.config;

import lombok.Data;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Configuration
@ConfigurationProperties(prefix = "teller")
@Data
public class TellerConfig {

    private String baseUrl;

    private Mtls mtls = new Mtls();

    @Data
    public static class Mtls {
        private String keystorePath;
        private String keystoreBase64;   // ← new: set via TELLER_KEYSTORE_BASE64 on Railway
        private String keystorePassword;
        private String keyPassword;
    }

    /**
     * Resolves the keystore file path:
     * 1. If keystoreBase64 is set (Railway/production): decode to a temp file
     * 2. Otherwise fall back to keystorePath (local dev)
     */
    private String resolveKeystorePath() throws IOException {
        if (StringUtils.hasText(mtls.keystoreBase64)) {
            byte[] decoded = Base64.getDecoder().decode(mtls.keystoreBase64.trim());
            Path tempFile = Files.createTempFile("teller-keystore-", ".p12");
            Files.write(tempFile, decoded);
            tempFile.toFile().deleteOnExit();
            return tempFile.toAbsolutePath().toString();
        }
        return mtls.keystorePath;
    }

    @Bean(name = "tellerRestTemplate")
    public RestTemplate tellerRestTemplate() throws IOException {
        String resolvedPath = resolveKeystorePath();

        // If neither Base64 nor path is configured, boot normally (tests / first run)
        if (!StringUtils.hasText(resolvedPath) || !StringUtils.hasText(mtls.keystorePassword)) {
            return new RestTemplate();
        }

        try {
            char[] keyPass = (mtls.keyPassword != null ? mtls.keyPassword : mtls.keystorePassword)
                    .toCharArray();

            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(
                            new File(resolvedPath),
                            mtls.keystorePassword.toCharArray(),
                            keyPass
                    )
                    .build();

            var sslSocketFactory =
                    new org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory(sslContext);

            var connectionManager =
                    org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(sslSocketFactory)
                            .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            HttpComponentsClientHttpRequestFactory requestFactory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            return new RestTemplate(requestFactory);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Teller mTLS RestTemplate", e);
        }
    }
}