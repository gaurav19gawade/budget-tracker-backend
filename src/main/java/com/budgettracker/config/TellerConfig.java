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

@Configuration
@ConfigurationProperties(prefix = "teller")
@Data
public class TellerConfig {

    private String baseUrl;

    private Mtls mtls = new Mtls();

    @Data
    public static class Mtls {
        private String keystorePath;
        private String keystorePassword;
        private String keyPassword;
    }

    @Bean(name = "tellerRestTemplate")
    public RestTemplate tellerRestTemplate() {
        // If not configured, create a normal RestTemplate so the app/tests can boot.
        if (!StringUtils.hasText(mtls.keystorePath) || !StringUtils.hasText(mtls.keystorePassword)) {
            return new RestTemplate();
        }

        try {
            char[] keyPass = (mtls.keyPassword != null ? mtls.keyPassword : mtls.keystorePassword).toCharArray();

            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(
                            new File(mtls.keystorePath),
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
