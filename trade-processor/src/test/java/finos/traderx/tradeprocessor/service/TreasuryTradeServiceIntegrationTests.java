package finos.traderx.tradeprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import finos.traderx.messaging.Publisher;
import finos.traderx.messaging.Subscriber;
import finos.traderx.tradeprocessor.model.InstrumentMetadata;
import finos.traderx.tradeprocessor.model.Position;
import finos.traderx.tradeprocessor.model.Trade;
import finos.traderx.tradeprocessor.model.TradeBookingResult;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.model.TradeSide;
import finos.traderx.tradeprocessor.model.TradeState;
import finos.traderx.tradeprocessor.repository.PositionRepository;
import finos.traderx.tradeprocessor.repository.TradeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:treasury-trades;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "nats.address=nats://unused:4222",
    "reference.data.service.url=http://unused:18085",
    "traderx.fixed-utc-instant=2026-07-30T12:00:00Z"
})
class TreasuryTradeServiceIntegrationTests {
  private static final int ACCOUNT_ID = 17017;
  private static final String TREASURY = "UST-20360515";

  @Autowired
  private TradeService tradeService;

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private PositionRepository positionRepository;

  @MockitoBean(name = "tradePublisher")
  private Publisher<Trade> tradePublisher;

  @MockitoBean(name = "positionPublisher")
  private Publisher<Position> positionPublisher;

  @MockitoBean
  private InstrumentMetadataClient instrumentClient;

  @MockitoBean(name = "tradeFeedHandler")
  private Subscriber<TradeOrder> tradeFeedHandler;

  @BeforeEach
  void resetDatabaseAndMocks() {
    tradeRepository.deleteAll();
    positionRepository.deleteAll();
    reset(tradePublisher, positionPublisher, instrumentClient);
    InstrumentMetadata instrument = new InstrumentMetadata();
    instrument.setInstrumentKey(TREASURY);
    instrument.setDisplayName("U.S. Treasury Note 4.375% due May 15, 2036");
    instrument.setAssetClass("US_TREASURY");
    instrument.setSecurityType("Debt");
    when(instrumentClient.resolve(TREASURY)).thenReturn(instrument);
    when(instrumentClient.isMatured(instrument)).thenReturn(false);
  }

  @Test
  void treasuryBuyUsesFaceWeightedCleanPriceAndDuplicateIdDoesNotBookTwice() throws Exception {
    savePosition(100_000, "99.257");
    TradeOrder order = order("stable-id", TradeSide.Buy, 50_000, "99.500");

    TradeBookingResult first = tradeService.processTrade(order);
    TradeBookingResult duplicate = tradeService.processTrade(order);

    assertThat(first.getTrade().getState()).isEqualTo(TradeState.Settled);
    assertThat(first.getPosition().getQuantity()).isEqualTo(150_000);
    assertThat(first.getPosition().getAverageCostBasis()).isEqualByComparingTo("99.338");
    assertThat(duplicate.getTrade().getId()).isEqualTo("stable-id");
    assertThat(positionRepository.findByAccountIdAndSecurity(ACCOUNT_ID, TREASURY).getQuantity())
        .isEqualTo(150_000);
    assertThat(tradeRepository.count()).isOne();
    verify(tradePublisher).publish(any(), any());
    verify(positionPublisher).publish(any(), any());
  }

  @Test
  void stockBookingDoesNotDependOnTreasuryReferenceData() {
    TradeOrder stock = orderFor("stock-id", "IBM", TradeSide.Buy, 10, "187.250");

    TradeBookingResult result = tradeService.processTrade(stock);

    assertThat(result.getTrade().getState()).isEqualTo(TradeState.Settled);
    verify(instrumentClient, never()).resolve(any());
  }

  @Test
  void treasuryMetadataIsResolvedBeforeTheBookingTransactionStarts() {
    InstrumentMetadata instrument = new InstrumentMetadata();
    instrument.setInstrumentKey(TREASURY);
    instrument.setDisplayName("Treasury");
    instrument.setAssetClass("US_TREASURY");
    instrument.setSecurityType("Debt");
    when(instrumentClient.resolve(TREASURY)).thenAnswer(ignored -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return instrument;
    });
    when(instrumentClient.isMatured(instrument)).thenReturn(false);
    savePosition(100, "99.257");

    TradeBookingResult result = tradeService.processTrade(
        order("outside-transaction", TradeSide.Buy, 100, "99.300"));

    assertThat(result.getTrade().getState()).isEqualTo(TradeState.Settled);
  }

  @Test
  void canonicalTreasuryKeyStillRequiresAuthoritativeDebtMetadata() {
    InstrumentMetadata equityMetadata = new InstrumentMetadata();
    equityMetadata.setInstrumentKey(TREASURY);
    equityMetadata.setDisplayName("Not debt");
    equityMetadata.setAssetClass("EQUITY");
    equityMetadata.setSecurityType("CommonStock");
    when(instrumentClient.resolve(TREASURY)).thenReturn(equityMetadata);

    TradeBookingResult result = tradeService.processTrade(
        order("metadata-fail-closed", TradeSide.Buy, 100, "99.300"));

    assertThat(result.getTrade().getState()).isEqualTo(TradeState.Rejected);
    assertThat(result.getTrade().getRejectionReason())
        .isEqualTo("Treasury reference metadata is unavailable");
  }

  @Test
  void concurrentSameTradeIdUsesOneFixedStripeAndBooksOnce() throws Exception {
    savePosition(100_000, "99.257");
    TradeOrder order = order("same-id-concurrent", TradeSide.Buy, 100, "99.300");
    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<TradeBookingResult> first = CompletableFuture.supplyAsync(() -> {
      await(start);
      return tradeService.processTrade(order);
    });
    CompletableFuture<TradeBookingResult> second = CompletableFuture.supplyAsync(() -> {
      await(start);
      return tradeService.processTrade(order);
    });
    start.countDown();

    assertThat(first.get(5, TimeUnit.SECONDS).getTrade().getId())
        .isEqualTo("same-id-concurrent");
    assertThat(second.get(5, TimeUnit.SECONDS).getTrade().getId())
        .isEqualTo("same-id-concurrent");
    assertThat(tradeRepository.count()).isOne();
    assertThat(positionRepository.findByAccountIdAndSecurity(ACCOUNT_ID, TREASURY).getQuantity())
        .isEqualTo(100_100);
    verify(instrumentClient, times(1)).resolve(TREASURY);
  }

  @Test
  void rejectedSellIsPersistedWithoutPositionMutationOrPositionPublication() throws Exception {
    savePosition(500, "99.257");

    TradeBookingResult result = tradeService.processTrade(
        order("rejected-sell", TradeSide.Sell, 600, "99.200"));

    assertThat(result.getTrade().getState()).isEqualTo(TradeState.Rejected);
    assertThat(result.getTrade().getRejectionReason())
        .isEqualTo("You cannot sell more Treasury face amount than you own and have available.");
    assertThat(positionRepository.findByAccountIdAndSecurity(ACCOUNT_ID, TREASURY).getQuantity())
        .isEqualTo(500);
    verify(tradePublisher).publish(any(), any());
    verify(positionPublisher, never()).publish(any(), any());
  }

  @Test
  void concurrentIndividuallyPlausibleSellsCannotCreateNegativePosition() throws Exception {
    savePosition(1_000, "99.257");

    CompletableFuture<TradeBookingResult> first = CompletableFuture.supplyAsync(
        () -> tradeService.processTrade(order("sell-a", TradeSide.Sell, 600, "99.200")));
    CompletableFuture<TradeBookingResult> second = CompletableFuture.supplyAsync(
        () -> tradeService.processTrade(order("sell-b", TradeSide.Sell, 600, "99.200")));

    List<TradeBookingResult> results = List.of(
        first.get(10, TimeUnit.SECONDS),
        second.get(10, TimeUnit.SECONDS));

    assertThat(results).extracting(result -> result.getTrade().getState())
        .containsExactlyInAnyOrder(TradeState.Settled, TradeState.Rejected);
    assertThat(positionRepository.findByAccountIdAndSecurity(ACCOUNT_ID, TREASURY).getQuantity())
        .isEqualTo(400);
    assertThat(tradeRepository.count()).isEqualTo(2);
  }

  @Test
  void sellKeepsAverageCleanPriceAndZeroPositionResetsIt() {
    savePosition(500, "99.257");

    TradeBookingResult partial = tradeService.processTrade(
        order("sell-partial", TradeSide.Sell, 400, "98.900"));
    TradeBookingResult flat = tradeService.processTrade(
        order("sell-flat", TradeSide.Sell, 100, "98.800"));

    assertThat(partial.getPosition().getAverageCostBasis()).isEqualByComparingTo("99.257");
    assertThat(flat.getPosition().getQuantity()).isZero();
    assertThat(flat.getPosition().getAverageCostBasis()).isEqualByComparingTo("0.000");
  }

  @Test
  void enforcesTradeIdColumnBoundaryAndTreasuryFaceIncrement() {
    String fiftyCharacters = "x".repeat(50);
    assertThat(tradeService.processTrade(
        order(fiftyCharacters, TradeSide.Buy, 100, "99.257")).getTrade().getId())
        .hasSize(50);
    assertThatThrownBy(() -> tradeService.processTrade(
        order("x".repeat(51), TradeSide.Buy, 100, "99.257")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("VARCHAR(50)");

    TradeBookingResult belowMinimum = tradeService.processTrade(
        order("below-minimum-face", TradeSide.Buy, 50, "99.257"));
    assertThat(belowMinimum.getTrade().getState()).isEqualTo(TradeState.Rejected);
    assertThat(belowMinimum.getTrade().getRejectionReason())
        .isEqualTo("Treasury quantity must be at least 100.");

    TradeBookingResult invalidIncrement = tradeService.processTrade(
        order("invalid-face-increment", TradeSide.Buy, 150, "99.257"));
    assertThat(invalidIncrement.getTrade().getState()).isEqualTo(TradeState.Rejected);
    assertThat(invalidIncrement.getTrade().getRejectionReason())
        .isEqualTo("Treasury quantity must be a multiple of 100.");
  }

  private void savePosition(int quantity, String averageCostBasis) {
    Position position = new Position();
    position.setAccountId(ACCOUNT_ID);
    position.setSecurity(TREASURY);
    position.setQuantity(quantity);
    position.setAverageCostBasis(new BigDecimal(averageCostBasis));
    positionRepository.saveAndFlush(position);
  }

  private TradeOrder order(
      String id,
      TradeSide side,
      int quantity,
      String price) {
    TradeOrder order = new TradeOrder(id, ACCOUNT_ID, TREASURY, side, quantity);
    order.setPrice(new BigDecimal(price));
    order.setSourceOrderId("order-017");
    return order;
  }

  private TradeOrder orderFor(
      String id,
      String security,
      TradeSide side,
      int quantity,
      String price) {
    TradeOrder order = new TradeOrder(id, ACCOUNT_ID, security, side, quantity);
    order.setPrice(new BigDecimal(price));
    order.setSourceOrderId("order-017");
    return order;
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await(2, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
