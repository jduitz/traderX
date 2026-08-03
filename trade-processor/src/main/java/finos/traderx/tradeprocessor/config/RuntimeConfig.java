package finos.traderx.tradeprocessor.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RuntimeConfig {
  @Bean
  Clock traderXClock(@Value("${traderx.fixed-utc-instant:}") String fixedUtcInstant) {
    return StringUtils.hasText(fixedUtcInstant)
        ? Clock.fixed(Instant.parse(fixedUtcInstant.trim()), ZoneOffset.UTC)
        : Clock.systemUTC();
  }

  @Bean
  RestTemplate restTemplate(
      RestTemplateBuilder builder,
      @Value("${reference.data.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${reference.data.read-timeout-ms:5000}") long readTimeoutMs) {
    return builder
        .connectTimeout(java.time.Duration.ofMillis(connectTimeoutMs))
        .readTimeout(java.time.Duration.ofMillis(readTimeoutMs))
        .build();
  }

  @Bean
  TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
    return new TransactionTemplate(manager);
  }
}
