package finos.traderx.tradeservice.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SecurityDeserializationTests {
  @Test
  void displayNameDeserializesAndIsNonNull() throws Exception {
    String json = """
        {
          "instrumentKey": "UST-20280630",
          "displayName": "U.S. Treasury Note 4.125% due June 30, 2028",
          "assetClass": "US_TREASURY",
          "securityType": "Debt",
          "matured": false,
          "observedAt": "2026-07-30T12:00:00Z",
          "debtEconomics": {
            "maturityDate": "2028-06-30",
            "originalTermYears": 2,
            "fixedInterest": {"couponRatePercent": 4.125}
          }
        }
        """;
    Security security = new ObjectMapper().readValue(json, Security.class);
    assertThat(security.getDisplayName()).isNotBlank();
    assertThat(security.getInstrumentKey()).isEqualTo("UST-20280630");
    assertThat(security.isTreasury()).isTrue();
  }
}
