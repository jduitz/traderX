package finos.traderx.ordermatcher.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import finos.traderx.messaging.Publisher;
import finos.traderx.ordermatcher.api.InstrumentMetadata;
import finos.traderx.ordermatcher.api.OrderCreateRequest;
import finos.traderx.ordermatcher.api.OrderResponse;
import finos.traderx.ordermatcher.api.PositionSnapshot;
import finos.traderx.ordermatcher.api.TradeBookingResult;
import finos.traderx.ordermatcher.model.OrderRecord;
import finos.traderx.ordermatcher.model.OrderSide;
import finos.traderx.ordermatcher.model.OrderStatus;
import finos.traderx.ordermatcher.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TreasuryOrderMatcherServiceTests {
  private static final String PROCESSOR_URL = "http://processor:18091";
  private static final String TRADE_URL = "http://trade:18092/trade/";
  private static final String REFERENCE_URL = "http://reference:18085";
  private static final String POSITION_URL = "http://position:18090";
  private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

  @Mock
  private OrderRepository repository;

  @Mock
  private Publisher<OrderResponse> publisher;

  @Mock
  private RestTemplate restTemplate;

  private OrderMatcherService service;

  @BeforeEach
  void createService() {
    when(repository.count()).thenReturn(1L);
    when(repository.findAllOrderIds()).thenReturn(List.of());
    when(repository.countByStatus(any())).thenReturn(0L);
    service = createService(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void treasuryFillSizingStaysOnHundredDollarBoundaries() {
    assertThat(OrderMatcherService.treasuryFillQuantity(100)).isEqualTo(100);
    assertThat(OrderMatcherService.treasuryFillQuantity(100_000)).isEqualTo(100_000);
    assertThat(OrderMatcherService.treasuryFillQuantity(100_100)).isEqualTo(50_000);
    assertThat(OrderMatcherService.treasuryFillQuantity(250_300)).isEqualTo(125_100);
  }

  @Test
  void derivedTradeIdHasDocumentedWorstCaseLength() {
    String maximumOrderId = "o".repeat(32);
    String derived = OrderMatcherService.deriveTradeId(maximumOrderId, Integer.MAX_VALUE);
    assertThat(derived).hasSize(48);
    assertThatThrownBy(() -> OrderMatcherService.deriveTradeId("o".repeat(35), Integer.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("VARCHAR(50)");
  }

  @Test
  void treasuryRoutesSynchronouslyToProcessorWhileStockKeepsAsyncTradeRoute() {
    OrderRecord treasury = openOrder("treasury-order", "UST-20360515", 200_000);
    when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(treasury));
    when(repository.findById(treasury.getOrderId())).thenReturn(Optional.of(treasury));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(restTemplate.postForEntity(
        eq(PROCESSOR_URL + "/tradeservice/order"), any(), eq(TradeBookingResult.class)))
        .thenReturn(ResponseEntity.ok(settled("99.250")));

    service.onPriceTick(treasury.getSecurity(), new BigDecimal("99.250"));

    verify(restTemplate).postForEntity(
        eq(PROCESSOR_URL + "/tradeservice/order"), any(), eq(TradeBookingResult.class));
    verify(restTemplate, never()).postForEntity(eq(TRADE_URL), any(), eq(Map.class));
    assertThat(treasury.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(treasury.getRemainingQuantity()).isEqualTo(100_000);

    OrderRecord stock = openOrder("stock-order", "IBM", 10);
    when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
    when(repository.findById(stock.getOrderId())).thenReturn(Optional.of(stock));
    when(restTemplate.postForEntity(eq(TRADE_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of("accepted", true)));

    service.onPriceTick(stock.getSecurity(), new BigDecimal("99.250"));

    verify(restTemplate).postForEntity(eq(TRADE_URL), any(), eq(Map.class));
    assertThat(stock.getStatus()).isEqualTo(OrderStatus.FILLED);
  }

  @Test
  void forceFillCompletesLargeStockAndEtfOrdersWhileNormalFillRemainsPartial() {
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(restTemplate.postForEntity(eq(TRADE_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of("accepted", true)));

    OrderRecord stock = openOrder("stock-force", "IBM", 1_800);
    OrderRecord etf = openOrder("etf-force", "SPY", 1_800);
    OrderRecord automatic = openOrder("stock-auto", "IBM", 1_800);
    when(repository.findById(stock.getOrderId())).thenReturn(Optional.of(stock));
    when(repository.findById(etf.getOrderId())).thenReturn(Optional.of(etf));
    when(repository.findById(automatic.getOrderId())).thenReturn(Optional.of(automatic));

    service.forceFillOrder(stock.getOrderId());
    service.forceFillOrder(etf.getOrderId());
    when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(automatic));
    service.onPriceTick("IBM", new BigDecimal("99.250"));

    assertThat(stock.getStatus()).isEqualTo(OrderStatus.FILLED);
    assertThat(stock.getRemainingQuantity()).isZero();
    assertThat(etf.getStatus()).isEqualTo(OrderStatus.FILLED);
    assertThat(etf.getRemainingQuantity()).isZero();
    assertThat(automatic.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(automatic.getRemainingQuantity()).isEqualTo(900);
  }

  @Test
  void unrelatedCancellationDoesNotWaitForTreasuryReconciliationHttp() throws Exception {
    OrderRecord pending = openOrder("pending-http", "UST-20360515", 100_000);
    pending.setPendingTradeId("pending-http-exec-0");
    pending.setPendingQuantity(100_000);
    pending.setPendingPrice(new BigDecimal("99.250"));
    OrderRecord unrelated = openOrder("unrelated", "IBM", 100);
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch releaseResponse = new CountDownLatch(1);
    when(repository.findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc())
        .thenReturn(List.of(pending));
    when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());
    when(repository.findById(pending.getOrderId())).thenReturn(Optional.of(pending));
    when(repository.findById(unrelated.getOrderId())).thenReturn(Optional.of(unrelated));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(restTemplate.postForEntity(
        eq(PROCESSOR_URL + "/tradeservice/order"), any(), eq(TradeBookingResult.class)))
        .thenAnswer(ignored -> {
          requestStarted.countDown();
          releaseResponse.await(5, TimeUnit.SECONDS);
          return ResponseEntity.ok(settled("99.250"));
        });

    CompletableFuture<Void> reconciliation = CompletableFuture.runAsync(service::runMatcherTick);
    assertThat(requestStarted.await(2, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<OrderResponse> cancellation = CompletableFuture.supplyAsync(
        () -> service.cancelOrder(unrelated.getOrderId()));
    assertThat(cancellation.get(1, TimeUnit.SECONDS).getStatus()).isEqualTo(OrderStatus.CANCELED);
    releaseResponse.countDown();
    reconciliation.get(5, TimeUnit.SECONDS);
  }

  @Test
  void staleAndDuplicateInProcessReconciliationResponsesAreIgnored() throws Exception {
    OrderRecord pending = openOrder("stale-http", "UST-20360515", 100_000);
    pending.setPendingTradeId("stale-http-exec-0");
    pending.setPendingQuantity(100_000);
    pending.setPendingPrice(new BigDecimal("99.250"));
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch releaseResponse = new CountDownLatch(1);
    when(repository.findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc())
        .thenReturn(List.of(pending));
    when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());
    when(repository.findById(pending.getOrderId())).thenReturn(Optional.of(pending));
    when(restTemplate.postForEntity(
        eq(PROCESSOR_URL + "/tradeservice/order"), any(), eq(TradeBookingResult.class)))
        .thenAnswer(ignored -> {
          requestStarted.countDown();
          releaseResponse.await(5, TimeUnit.SECONDS);
          return ResponseEntity.ok(settled("99.250"));
        });

    CompletableFuture<Void> first = CompletableFuture.runAsync(service::runMatcherTick);
    assertThat(requestStarted.await(2, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Void> duplicate = CompletableFuture.runAsync(service::runMatcherTick);
    duplicate.get(1, TimeUnit.SECONDS);

    pending.setPendingTradeId("stale-http-exec-100000");
    pending.setPendingQuantity(50_000);
    pending.setPendingPrice(new BigDecimal("99.500"));
    releaseResponse.countDown();
    first.get(5, TimeUnit.SECONDS);

    verify(restTemplate, times(1)).postForEntity(
        eq(PROCESSOR_URL + "/tradeservice/order"), any(), eq(TradeBookingResult.class));
    assertThat(pending.getPendingTradeId()).isEqualTo("stale-http-exec-100000");
    assertThat(pending.getRemainingQuantity()).isEqualTo(100_000);
  }

  @Test
  void boundedReservationLookupDoesNotBlockUnrelatedCreation() throws Exception {
    InstrumentMetadata instrument = treasuryMetadata("2026-07-31");
    PositionSnapshot position = new PositionSnapshot();
    position.setSecurity("UST-20360515");
    position.setQuantity(1_000);
    CountDownLatch positionRequestStarted = new CountDownLatch(1);
    CountDownLatch releasePosition = new CountDownLatch(1);
    when(restTemplate.getForObject(
        REFERENCE_URL + "/instruments/UST-20360515", InstrumentMetadata.class))
        .thenReturn(instrument);
    when(restTemplate.getForObject(POSITION_URL + "/positions/17017", PositionSnapshot[].class))
        .thenAnswer(ignored -> {
          positionRequestStarted.countDown();
          releasePosition.await(5, TimeUnit.SECONDS);
          return new PositionSnapshot[] {position};
        });
    when(repository.sumOpenTreasurySellReservations(17017, "UST-20360515"))
        .thenReturn(0L);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CompletableFuture<OrderResponse> blockedSell = CompletableFuture.supplyAsync(
        () -> service.createOrder(createRequest(OrderSide.Sell, 100)));
    assertThat(positionRequestStarted.await(2, TimeUnit.SECONDS)).isTrue();
    OrderCreateRequest stock = new OrderCreateRequest();
    stock.setAccountId(17017);
    stock.setSecurity("IBM");
    stock.setSide(OrderSide.Buy);
    stock.setQuantity(10);
    stock.setLimitPrice(new BigDecimal("187.250"));
    CompletableFuture<OrderResponse> unrelated = CompletableFuture.supplyAsync(
        () -> service.createOrder(stock));

    assertThat(unrelated.get(1, TimeUnit.SECONDS).getSecurity()).isEqualTo("IBM");
    releasePosition.countDown();
    assertThat(blockedSell.get(5, TimeUnit.SECONDS).getSecurity()).isEqualTo("UST-20360515");
  }

  @Test
  void concurrentTreasurySellsSerializeReservationValidation() throws Exception {
    InstrumentMetadata instrument = treasuryMetadata("2026-07-31");
    PositionSnapshot position = new PositionSnapshot();
    position.setSecurity("UST-20360515");
    position.setQuantity(1_000);
    AtomicLong reserved = new AtomicLong();
    CyclicBarrier positionBarrier = new CyclicBarrier(2);
    CyclicBarrier reservationBarrier = new CyclicBarrier(2);
    when(restTemplate.getForObject(
        REFERENCE_URL + "/instruments/UST-20360515", InstrumentMetadata.class))
        .thenReturn(instrument);
    when(restTemplate.getForObject(POSITION_URL + "/positions/17017", PositionSnapshot[].class))
        .thenAnswer(ignored -> {
          awaitOrContinue(positionBarrier);
          return new PositionSnapshot[] {position};
        });
    when(repository.sumOpenTreasurySellReservations(17017, "UST-20360515"))
        .thenAnswer(ignored -> {
          long snapshot = reserved.get();
          awaitOrContinue(reservationBarrier);
          return snapshot;
        });
    when(repository.save(any())).thenAnswer(invocation -> {
      OrderRecord saved = invocation.getArgument(0);
      if (saved.getSide() == OrderSide.Sell) {
        reserved.addAndGet(saved.getRemainingQuantity());
      }
      return saved;
    });

    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<Boolean> first = createSellAsync(start, 600);
    CompletableFuture<Boolean> second = createSellAsync(start, 600);
    start.countDown();

    assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(true, false);
    assertThat(reserved.get()).isEqualTo(600);
  }

  @Test
  void prometheusMetricsRestoreInheritedFiniteBuckets() {
    when(repository.findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc()).thenReturn(List.of());
    String metrics = service.prometheusMetrics();

    for (String boundary : List.of("0.01", "0.05", "0.1", "0.25", "0.5", "1", "+Inf")) {
      assertThat(metrics).contains(
          "traderx_order_match_latency_seconds_bucket{le=\"" + boundary + "\"}");
    }
  }

  @Test
  void timeoutRetryUsesIdenticalPendingDataAndReconcilesOutOfTheMoney() {
    OrderRecord order = openOrder("retry-order", "UST-20360515", 250_000);
    List<OrderRecord> current = new ArrayList<>(List.of(order));
    when(repository.findAllByOrderByUpdatedAtDesc()).thenAnswer(ignored -> current);
    when(repository.findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc())
        .thenAnswer(ignored -> order.getPendingTradeId() == null ? List.of() : List.of(order));
    when(repository.findById(order.getOrderId())).thenReturn(Optional.of(order));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(restTemplate.postForEntity(
        eq(PROCESSOR_URL + "/tradeservice/order"), any(), eq(TradeBookingResult.class)))
        .thenThrow(new ResourceAccessException("response lost"))
        .thenReturn(ResponseEntity.ok(settled("99.111")));

    service.onPriceTick(order.getSecurity(), new BigDecimal("99.250"));
    assertThat(order.getPendingTradeId()).isEqualTo("retry-order-exec-0");
    assertThat(order.getPendingQuantity()).isEqualTo(125_000);
    assertThat(order.getPendingPrice()).isEqualByComparingTo("99.250");

    // The retry is driven by pending state, not by the now out-of-the-money market.
    service.onPriceTick(order.getSecurity(), new BigDecimal("105.000"));
    service.runMatcherTick();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> payloads = ArgumentCaptor.forClass(Map.class);
    verify(restTemplate, atLeastOnce()).postForEntity(
        eq(PROCESSOR_URL + "/tradeservice/order"), payloads.capture(), eq(TradeBookingResult.class));
    assertThat(payloads.getAllValues()).hasSize(2);
    assertThat(payloads.getAllValues().get(0))
        .containsEntry("id", "retry-order-exec-0")
        .containsEntry("quantity", 125_000)
        .containsEntry("price", new BigDecimal("99.250"));
    assertThat(payloads.getAllValues().get(1)).isEqualTo(payloads.getAllValues().get(0));
    assertThat(order.getPendingTradeId()).isNull();
    assertThat(order.getRemainingQuantity()).isEqualTo(125_000);
    assertThat(order.getLastExecutionPrice()).isEqualByComparingTo("99.111");
  }

  @Test
  void cancellationConflictsWhileExecutionIsPending() {
    OrderRecord order = openOrder("pending-order", "UST-20360515", 100_000);
    order.setPendingTradeId("pending-order-exec-0");
    order.setPendingQuantity(100_000);
    order.setPendingPrice(new BigDecimal("99.250"));
    when(repository.findById(order.getOrderId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> service.cancelOrder(order.getOrderId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT")
        .hasMessageContaining("reconciliation is pending");
  }

  @Test
  void treasuryQuantityValidationDistinguishesMinimumFromIncrement() {
    InstrumentMetadata instrument = treasuryMetadata("2026-07-31");
    when(restTemplate.getForObject(
        REFERENCE_URL + "/instruments/UST-20360515", InstrumentMetadata.class))
        .thenReturn(instrument);

    assertThatThrownBy(() -> service.createOrder(
        createRequest(OrderSide.Buy, 50)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Treasury quantity must be at least 100.");
    assertThatThrownBy(() -> service.createOrder(
        createRequest(OrderSide.Buy, 150)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Treasury quantity must be a multiple of 100.");
  }

  @Test
  void sellReservationAndMaturityBoundaryAreEnforced() {
    InstrumentMetadata instrument = treasuryMetadata("2026-07-31");
    when(restTemplate.getForObject(
        REFERENCE_URL + "/instruments/UST-20360515", InstrumentMetadata.class))
        .thenReturn(instrument);
    PositionSnapshot position = new PositionSnapshot();
    position.setSecurity("UST-20360515");
    position.setQuantity(100_000);
    when(restTemplate.getForObject(POSITION_URL + "/positions/17017", PositionSnapshot[].class))
        .thenReturn(new PositionSnapshot[] {position});
    when(repository.sumOpenTreasurySellReservations(17017, "UST-20360515"))
        .thenReturn(60_000L);

    assertThatThrownBy(() -> service.createOrder(
        createRequest(OrderSide.Sell, 40_100)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("You cannot sell more Treasury face amount than you own and have available.");

    OrderMatcherService atMaturity = createService(
        Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));
    assertThatThrownBy(() -> atMaturity.createOrder(
        createRequest(OrderSide.Buy, 100)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("has matured");
  }

  private OrderMatcherService createService(Clock clock) {
    return new OrderMatcherService(
        repository,
        publisher,
        restTemplate,
        clock,
        false,
        "http://prices:18100",
        TRADE_URL,
        PROCESSOR_URL,
        REFERENCE_URL,
        POSITION_URL,
        1_000,
        1_000);
  }

  private OrderRecord openOrder(String id, String security, int quantity) {
    OrderRecord order = new OrderRecord();
    order.setOrderId(id);
    order.setAccountId(17017);
    order.setSecurity(security);
    order.setSide(OrderSide.Buy);
    order.setQuantity(quantity);
    order.setRemainingQuantity(quantity);
    order.setLimitPrice(new BigDecimal("100.000"));
    order.setStatus(OrderStatus.NEW);
    order.setCreatedAt(NOW);
    order.setUpdatedAt(NOW);
    return order;
  }

  private TradeBookingResult settled(String price) {
    TradeBookingResult.BookedTrade trade = new TradeBookingResult.BookedTrade();
    trade.setId("booked");
    trade.setState("Settled");
    trade.setPrice(new BigDecimal(price));
    TradeBookingResult result = new TradeBookingResult();
    result.setTrade(trade);
    return result;
  }

  private InstrumentMetadata treasuryMetadata(String maturityDate) {
    InstrumentMetadata instrument = new InstrumentMetadata();
    instrument.setInstrumentKey("UST-20360515");
    instrument.setAssetClass("US_TREASURY");
    instrument.setSecurityType("Debt");
    instrument.setMatured(false);
    InstrumentMetadata.DebtEconomics economics = new InstrumentMetadata.DebtEconomics();
    economics.setMaturityDate(maturityDate);
    instrument.setDebtEconomics(economics);
    return instrument;
  }

  private OrderCreateRequest createRequest(OrderSide side, int quantity) {
    OrderCreateRequest request = new OrderCreateRequest();
    request.setAccountId(17017);
    request.setSecurity("UST-20360515");
    request.setSide(side);
    request.setQuantity(quantity);
    request.setLimitPrice(new BigDecimal("99.250"));
    return request;
  }

  private CompletableFuture<Boolean> createSellAsync(CountDownLatch start, int quantity) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        start.await(2, TimeUnit.SECONDS);
        service.createOrder(createRequest(OrderSide.Sell, quantity));
        return true;
      } catch (ResponseStatusException ex) {
        return false;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return false;
      }
    });
  }

  private void awaitOrContinue(CyclicBarrier barrier) {
    try {
      barrier.await(250, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ignored) {
      barrier.reset();
    } catch (Exception ignored) {
      // A serialized caller reaches this barrier alone by design.
    }
  }
}
