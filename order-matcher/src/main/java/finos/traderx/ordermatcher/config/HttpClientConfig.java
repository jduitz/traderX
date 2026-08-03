package finos.traderx.ordermatcher.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@Configuration
public class HttpClientConfig {
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Bean
    Clock traderXClock(@Value("${traderx.fixed-utc-instant:}") String fixedUtcInstant) {
        return StringUtils.hasText(fixedUtcInstant)
            ? Clock.fixed(Instant.parse(fixedUtcInstant.trim()), ZoneOffset.UTC)
            : Clock.systemUTC();
    }
}
