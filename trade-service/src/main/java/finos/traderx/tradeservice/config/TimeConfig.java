package finos.traderx.tradeservice.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TimeConfig {
  @Bean
  Clock traderXClock(@Value("${traderx.fixed-utc-instant:}") String fixedUtcInstant) {
    if (!StringUtils.hasText(fixedUtcInstant)) {
      return Clock.systemUTC();
    }
    return Clock.fixed(Instant.parse(fixedUtcInstant.trim()), ZoneOffset.UTC);
  }

  @Bean
  RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }
}
