package finos.traderx.tradeservice.controller;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.exceptions.ResourceNotFoundException;
import finos.traderx.tradeservice.model.Account;
import finos.traderx.tradeservice.model.PriceQuote;
import finos.traderx.tradeservice.model.PositionSnapshot;
import finos.traderx.tradeservice.model.Security;
import finos.traderx.tradeservice.model.TradeOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping(value = "/trade", produces = "application/json")
public class TradeOrderController {

  private static final Logger log = LoggerFactory.getLogger(TradeOrderController.class);

  private final Publisher<TradeOrder> tradePublisher;
  private final RestTemplate restTemplate;
  private final Clock clock;

  @Value("${reference.data.service.url}")
  private String referenceDataServiceAddress;

  @Value("${account.service.url}")
  private String accountServiceAddress;

  @Value("${price.service.url}")
  private String priceServiceAddress;

  @Value("${position.service.url}")
  private String positionServiceAddress;

  public TradeOrderController(
      Publisher<TradeOrder> tradePublisher,
      RestTemplate restTemplate,
      Clock clock) {
    this.tradePublisher = tradePublisher;
    this.restTemplate = restTemplate;
    this.clock = clock;
  }

  @Operation(description = "Submit a new trade order")
  @PostMapping("/")
  public ResponseEntity<TradeOrder> createTradeOrder(
      @Parameter(description = "the intended trade order") @RequestBody TradeOrder tradeOrder) {
    log.info("Called createTradeOrder");

    Security instrument = fetchInstrument(tradeOrder.getSecurity());
    if (instrument == null) {
      throw new ResourceNotFoundException(tradeOrder.getSecurity() + " not found in Reference data service.");
    } else if (!validateAccount(tradeOrder.getAccountId())) {
      throw new ResourceNotFoundException(tradeOrder.getAccountId() + " not found in Account service.");
    } else {
      try {
        validateTreasuryOrder(tradeOrder, instrument);
        if (tradeOrder.getId() == null || tradeOrder.getId().isBlank()) {
          tradeOrder.setId(UUID.randomUUID().toString());
        }
        BigDecimal executionPrice = fetchExecutionPrice(tradeOrder.getSecurity());
        tradeOrder.setPrice(executionPrice);
        log.info("Trade is valid. Submitting {}", tradeOrder);
        tradePublisher.publish("/trades", tradeOrder);
        return ResponseEntity.ok(tradeOrder);
      } catch (PubSubException e) {
        throw new RuntimeException("Failed to publish trade order", e);
      }
    }
  }

  private Security fetchInstrument(String instrumentKey) {
    String url = this.referenceDataServiceAddress + "/instruments/" + instrumentKey;
    try {
      ResponseEntity<Security> response = this.restTemplate.getForEntity(url, Security.class);
      Security instrument = response.getBody();
      if (instrument == null || instrument.getDisplayName() == null || instrument.getDisplayName().isBlank()) {
        log.error("Reference data returned incomplete instrument metadata for {}", instrumentKey);
        return null;
      }
      log.info("Validate instrument {}", instrument);
      return instrument;
    } catch (HttpClientErrorException ex) {
      if (ex.getRawStatusCode() == 404) {
        log.info("{} not found in reference data service.", instrumentKey);
      } else {
        log.error(ex.getMessage(), ex);
      }
      return null;
    } catch (RuntimeException ex) {
      log.error("Reference data unavailable for {}", instrumentKey, ex);
      return null;
    }
  }

  private void validateTreasuryOrder(TradeOrder order, Security instrument) {
    if (!instrument.isTreasury()) {
      return;
    }
    if (order.getQuantity() == null || order.getQuantity() < 100) {
      throw new ResponseStatusException(BAD_REQUEST, "Treasury quantity must be at least 100.");
    }
    if (order.getQuantity() % 100 != 0) {
      throw new ResponseStatusException(BAD_REQUEST, "Treasury quantity must be a multiple of 100.");
    }
    if (isMatured(instrument)) {
      throw new ResponseStatusException(BAD_REQUEST, "Treasury instrument has matured");
    }
    if (order.getSide() == finos.traderx.tradeservice.model.TradeSide.Sell) {
      int settled = fetchSettledQuantity(order.getAccountId(), order.getSecurity());
      if (order.getQuantity() > settled) {
        throw new ResponseStatusException(
            CONFLICT,
            "You cannot sell more Treasury face amount than you own and have available.");
      }
    }
  }

  private boolean isMatured(Security instrument) {
    if (Boolean.TRUE.equals(instrument.getMatured())) {
      return true;
    }
    if (instrument.getDebtEconomics() == null || instrument.getDebtEconomics().getMaturityDate() == null) {
      return true;
    }
    LocalDate maturity = LocalDate.parse(instrument.getDebtEconomics().getMaturityDate());
    Instant maturityInstant = maturity.atStartOfDay(ZoneOffset.UTC).toInstant();
    return !clock.instant().isBefore(maturityInstant);
  }

  private int fetchSettledQuantity(Integer accountId, String security) {
    try {
      ResponseEntity<PositionSnapshot[]> response = restTemplate.getForEntity(
          positionServiceAddress + "/positions/" + accountId,
          PositionSnapshot[].class);
      return Arrays.stream(response.getBody() == null ? new PositionSnapshot[0] : response.getBody())
          .filter(position -> security.equalsIgnoreCase(position.getSecurity()))
          .map(PositionSnapshot::getQuantity)
          .filter(quantity -> quantity != null)
          .findFirst()
          .orElse(0);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(CONFLICT, "Settled Treasury position is unavailable", ex);
    }
  }

  private boolean validateAccount(Integer id) {
    String url = this.accountServiceAddress + "/account/" + id;
    try {
      ResponseEntity<Account> response = this.restTemplate.getForEntity(url, Account.class);
      log.info("Validate account {}", response.getBody());
      return true;
    } catch (HttpClientErrorException ex) {
      if (ex.getRawStatusCode() == 404) {
        log.info("Account {} not found in account service.", id);
      } else {
        log.error(ex.getMessage(), ex);
      }
      return false;
    }
  }

  private BigDecimal fetchExecutionPrice(String ticker) {
    String url = this.priceServiceAddress + "/prices/" + ticker;
    try {
      ResponseEntity<PriceQuote> response = this.restTemplate.getForEntity(url, PriceQuote.class);
      PriceQuote quote = response.getBody();
      if (quote == null || quote.getPrice() == null) {
        throw new ResourceNotFoundException("Price quote missing for ticker " + ticker);
      }
      return quote.getPrice().setScale(3, RoundingMode.HALF_UP);
    } catch (HttpClientErrorException ex) {
      if (ex.getRawStatusCode() == 404) {
        throw new ResourceNotFoundException("Price quote unavailable for ticker " + ticker);
      }
      throw ex;
    }
  }
}
