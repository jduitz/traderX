package finos.traderx.tradeprocessor.service;

import finos.traderx.tradeprocessor.model.InstrumentMetadata;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class InstrumentMetadataClient {
  private final RestTemplate restTemplate;
  private final Clock clock;
  private final String referenceDataUrl;

  public InstrumentMetadataClient(
      RestTemplate restTemplate,
      Clock clock,
      @Value("${reference.data.service.url}") String referenceDataUrl) {
    this.restTemplate = restTemplate;
    this.clock = clock;
    this.referenceDataUrl = referenceDataUrl;
  }

  public InstrumentMetadata resolve(String instrumentKey) {
    try {
      InstrumentMetadata instrument = restTemplate.getForObject(
          referenceDataUrl + "/instruments/" + instrumentKey,
          InstrumentMetadata.class);
      if (instrument == null || !StringUtils.hasText(instrument.getDisplayName())) {
        return null;
      }
      return instrument;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  public boolean isMatured(InstrumentMetadata instrument) {
    if (instrument == null || Boolean.TRUE.equals(instrument.getMatured())) {
      return true;
    }
    if (instrument.getDebtEconomics() == null
        || !StringUtils.hasText(instrument.getDebtEconomics().getMaturityDate())) {
      return true;
    }
    return !clock.instant().isBefore(
        LocalDate.parse(instrument.getDebtEconomics().getMaturityDate())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant());
  }
}
