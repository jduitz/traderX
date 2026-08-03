package finos.traderx.ordermatcher.service;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.ordermatcher.api.InstrumentMetadata;
import finos.traderx.ordermatcher.api.OpenCountResponse;
import finos.traderx.ordermatcher.api.OrderCreateRequest;
import finos.traderx.ordermatcher.api.OrderResponse;
import finos.traderx.ordermatcher.api.PositionSnapshot;
import finos.traderx.ordermatcher.api.TradeBookingResult;
import finos.traderx.ordermatcher.model.OrderRecord;
import finos.traderx.ordermatcher.model.OrderSide;
import finos.traderx.ordermatcher.model.OrderStatus;
import finos.traderx.ordermatcher.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OrderMatcherService {
    private static final Logger log = LoggerFactory.getLogger(OrderMatcherService.class);
    private static final String APP_NAME = "traderx-order-matcher";
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("^ord-013-(\\d{4,})$");
    private static final String ALL_ORDERS_TOPIC = "/orders";
    private static final String TREASURY_PREFIX = "UST-";
    private static final int LOCK_STRIPE_COUNT = 256;
    private static final Set<OrderStatus> OPEN_STATUSES =
        Set.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED);

    private final OrderRepository orderRepository;
    private final Publisher<OrderResponse> orderPublisher;
    private final RestTemplate restTemplate;
    private final Clock clock;
    private final boolean seedEnabled;
    private final String priceServiceUrl;
    private final String tradeServiceUrl;
    private final String tradeProcessorUrl;
    private final String referenceDataUrl;
    private final String positionServiceUrl;
    private final int fillFullThreshold;
    private final long matcherTickMs;

    private final Instant startedAt;
    private final AtomicInteger nextOrderSequence = new AtomicInteger(1);
    private final AtomicLong matcherTicks = new AtomicLong();
    private final AtomicLong autoFillAttempts = new AtomicLong();
    private final AtomicLong autoFillSuccess = new AtomicLong();
    private final AtomicLong tradeSubmitFailures = new AtomicLong();
    private final AtomicLong reconciliationRetries = new AtomicLong();
    private final AtomicLong reconciliationSuccesses = new AtomicLong();
    private final AtomicLong reconciliationRejections = new AtomicLong();
    private final Map<String, AtomicLong> eventCounters = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();
    /*
     * Locking invariant:
     * - creation and Treasury sell reservation use only an account/security stripe;
     * - fill, cancellation, and reconciliation use only an order-ID stripe;
     * - no current path holds both lock types. If nesting is ever required, the
     *   account/security stripe must always be acquired before the order-ID stripe.
     */
    private final ReentrantLock[] orderLocks = createLockStripes();
    private final ReentrantLock[] reservationLocks = createLockStripes();
    private final Set<String> reconciliationsInFlight = ConcurrentHashMap.newKeySet();
    private volatile Instant lastTickAt;

    public OrderMatcherService(
        OrderRepository orderRepository,
        Publisher<OrderResponse> orderPublisher,
        RestTemplate restTemplate,
        Clock clock,
        @Value("${order.matcher.seed-enabled:true}") boolean seedEnabled,
        @Value("${order.matcher.price-service-url:http://price-publisher:18100}") String priceServiceUrl,
        @Value("${order.matcher.trade-service-url:http://trade-service:18092/trade/}") String tradeServiceUrl,
        @Value("${order.matcher.trade-processor-url:http://trade-processor:18091}") String tradeProcessorUrl,
        @Value("${order.matcher.reference-data-url:http://reference-data:18085}") String referenceDataUrl,
        @Value("${order.matcher.position-service-url:http://position-service:18090}") String positionServiceUrl,
        @Value("${order.matcher.tick-ms:1000}") long matcherTickMs,
        @Value("${order.matcher.fill-full-threshold:1000}") int fillFullThreshold
    ) {
        this.orderRepository = orderRepository;
        this.orderPublisher = orderPublisher;
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.seedEnabled = seedEnabled;
        this.priceServiceUrl = trimTrailingSlash(priceServiceUrl);
        this.tradeServiceUrl = tradeServiceUrl;
        this.tradeProcessorUrl = trimTrailingSlash(tradeProcessorUrl);
        this.referenceDataUrl = trimTrailingSlash(referenceDataUrl);
        this.positionServiceUrl = trimTrailingSlash(positionServiceUrl);
        this.matcherTickMs = Math.max(100, matcherTickMs);
        this.fillFullThreshold = Math.max(1, fillFullThreshold);
        this.startedAt = clock.instant();
        initializeCounters();
        initializeData();
    }

    @Scheduled(fixedDelayString = "${order.matcher.tick-ms:1000}")
    public void runMatcherTick() {
        for (OrderRecord pending : orderRepository.findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc()) {
            reconcilePending(pending.getOrderId(), true);
        }
        List<OrderRecord> openOrders = orderRepository.findAllByOrderByUpdatedAtDesc().stream()
            .filter(this::isOpen)
            .filter(order -> order.getPendingTradeId() == null)
            .filter(order -> order.getRemainingQuantity() != null && order.getRemainingQuantity() > 0)
            .toList();
        for (OrderRecord order : openOrders) {
            BigDecimal marketPrice = lastPrices.get(order.getSecurity());
            if (marketPrice != null) {
                tryAutoFill(order.getOrderId(), marketPrice);
            }
        }
        matcherTicks.incrementAndGet();
        lastTickAt = clock.instant();
    }

    public void onPriceTick(String ticker, BigDecimal marketPrice) {
        if (!StringUtils.hasText(ticker) || marketPrice == null) {
            return;
        }
        String key = ticker.trim().toUpperCase(Locale.ROOT);
        BigDecimal price = roundPrice(marketPrice);
        lastPrices.put(key, price);
        for (String orderId : orderRepository.findAllByOrderByUpdatedAtDesc().stream()
            .filter(this::isOpen)
            .filter(order -> order.getPendingTradeId() == null)
            .filter(order -> order.getRemainingQuantity() != null && order.getRemainingQuantity() > 0)
            .filter(order -> key.equals(order.getSecurity()))
            .map(OrderRecord::getOrderId)
            .toList()) {
            tryAutoFill(orderId, price);
        }
    }

    public List<OrderResponse> listOrders(String statusFilter, Integer accountIdFilter) {
        String status = StringUtils.hasText(statusFilter)
            ? statusFilter.trim().toLowerCase(Locale.ROOT)
            : "open";
        return new ArrayList<>(orderRepository.findAllByOrderByUpdatedAtDesc()).stream()
            .filter(order -> filterByStatus(order, status))
            .filter(order -> accountIdFilter == null || order.getAccountId().equals(accountIdFilter))
            .sorted(Comparator.comparing(OrderRecord::getUpdatedAt).reversed())
            .map(order -> OrderResponse.from(order, lastPrices.get(order.getSecurity())))
            .toList();
    }

    public OrderResponse getOrder(String orderId) {
        OrderRecord order = findOrder(orderId);
        return OrderResponse.from(order, lastPrices.get(order.getSecurity()));
    }

    public OrderResponse createOrder(OrderCreateRequest request) {
        validateCreateRequest(request);
        String security = request.getSecurity().trim().toUpperCase(Locale.ROOT);
        // Reference data is network I/O and does not participate in reservation serialization.
        InstrumentMetadata instrument = resolveTreasuryMetadata(security);
        if (instrument != null && request.getSide() != OrderSide.Sell) {
            validateTreasuryCreate(request, instrument, security);
        }

        ReentrantLock reservationLock = reservationLock(request.getAccountId(), security);
        reservationLock.lock();
        try {
            if (instrument != null && request.getSide() == OrderSide.Sell) {
                // This bounded position-service call intentionally stays inside the
                // same-account/same-security stripe so two sells cannot over-reserve.
                validateTreasuryCreate(request, instrument, security);
            }
            return persistNewOrder(request, security);
        } finally {
            reservationLock.unlock();
        }
    }

    public OrderResponse cancelOrder(String orderId) {
        ReentrantLock orderLock = orderLock(orderId);
        orderLock.lock();
        try {
            OrderRecord order = findOrder(orderId);
            if (order.getPendingTradeId() != null) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Treasury execution reconciliation is pending");
            }
            if (isOpen(order)) {
                order.setStatus(OrderStatus.CANCELED);
                order.setRemainingQuantity(0);
                order.setUpdatedAt(clock.instant());
                orderRepository.save(order);
                incrementEvent("cancel");
            }
            return OrderResponse.from(order, lastPrices.get(order.getSecurity()));
        } finally {
            orderLock.unlock();
        }
    }

    public OrderResponse forceFillOrder(String orderId) {
        OrderRecord order = findOrder(orderId);
        if (!isOpen(order) || order.getRemainingQuantity() == null || order.getRemainingQuantity() <= 0) {
            return OrderResponse.from(order, lastPrices.get(order.getSecurity()));
        }
        BigDecimal marketPrice = Optional.ofNullable(lastPrices.get(order.getSecurity()))
            .orElse(order.getLimitPrice());
        tryAutoFill(orderId, marketPrice, true);
        OrderRecord refreshed = findOrder(orderId);
        if (refreshed.getPendingTradeId() != null) {
            throw new ResponseStatusException(BAD_GATEWAY, "Treasury execution requires reconciliation");
        }
        return OrderResponse.from(refreshed, lastPrices.get(refreshed.getSecurity()));
    }

    public OpenCountResponse openCounts() {
        return new OpenCountResponse(
            orderRepository.countByStatusIn(OPEN_STATUSES),
            orderRepository.countByStatusInAndRemainingQuantityGreaterThan(OPEN_STATUSES, 0));
    }

    public Map<String, Object> health() {
        OpenCountResponse counts = openCounts();
        Map<String, Object> matcher = new LinkedHashMap<>();
        matcher.put("tickMs", matcherTickMs);
        matcher.put("ticks", matcherTicks.get());
        matcher.put("lastTickAt", lastTickAt);
        matcher.put("autoFillAttempts", autoFillAttempts.get());
        matcher.put("autoFillSuccess", autoFillSuccess.get());
        matcher.put("tradeSubmitFailures", tradeSubmitFailures.get());
        matcher.put("reconciliationRetries", reconciliationRetries.get());
        matcher.put("reconciliationSuccesses", reconciliationSuccesses.get());
        matcher.put("reconciliationRejections", reconciliationRejections.get());
        matcher.put("pendingExecutions", orderRepository.findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc().size());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("service", APP_NAME);
        payload.put("uptimeSeconds", Math.max(0, clock.instant().getEpochSecond() - startedAt.getEpochSecond()));
        payload.put("priceServiceUrl", priceServiceUrl);
        payload.put("tradeServiceUrl", tradeServiceUrl);
        payload.put("tradeProcessorUrl", tradeProcessorUrl);
        payload.put("matcher", matcher);
        payload.put("openOrders", counts.getOpenOrders());
        payload.put("unfilledOrders", counts.getUnfilledOrders());
        return payload;
    }

    public String prometheusMetrics() {
        long open = orderRepository.countByStatusIn(OPEN_STATUSES);
        long unfilled = orderRepository.countByStatusInAndRemainingQuantityGreaterThan(OPEN_STATUSES, 0);
        long buy = orderRepository.countByStatusInAndSide(OPEN_STATUSES, OrderSide.Buy);
        long sell = orderRepository.countByStatusInAndSide(OPEN_STATUSES, OrderSide.Sell);
        long pending = orderRepository.findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc().size();
        StringBuilder sb = new StringBuilder();
        gauge(sb, "traderx_orders_open_total", "Total open orders.", open);
        gauge(sb, "traderx_orders_unfilled_total", "Orders with remaining quantity.", unfilled);
        sb.append("# HELP traderx_orders_pending_by_side Pending orders grouped by side.\n")
            .append("# TYPE traderx_orders_pending_by_side gauge\n")
            .append("traderx_orders_pending_by_side{side=\"Buy\"} ").append(buy).append('\n')
            .append("traderx_orders_pending_by_side{side=\"Sell\"} ").append(sell).append('\n');
        counter(sb, "traderx_order_matcher_ticks_total", "Matcher ticks.", matcherTicks.get());
        counter(sb, "traderx_order_autofill_attempts_total", "Auto-fill attempts.", autoFillAttempts.get());
        counter(sb, "traderx_order_autofill_success_total", "Successful fills.", autoFillSuccess.get());
        counter(sb, "traderx_order_trade_submit_failures_total", "Trade submission failures.", tradeSubmitFailures.get());
        counter(sb, "traderx_treasury_reconciliation_retries_total", "Treasury reconciliation retries.", reconciliationRetries.get());
        counter(sb, "traderx_treasury_reconciliation_successes_total", "Treasury reconciliation successes.", reconciliationSuccesses.get());
        counter(sb, "traderx_treasury_reconciliation_rejections_total", "Treasury reconciliation rejections.", reconciliationRejections.get());
        gauge(sb, "traderx_treasury_pending_executions", "Pending Treasury executions.", pending);
        sb.append("# HELP traderx_order_events_total Order lifecycle events.\n")
            .append("# TYPE traderx_order_events_total counter\n");
        for (String event : List.of("create", "partial_fill", "fill", "cancel", "reject", "force_fill")) {
            sb.append("traderx_order_events_total{event=\"").append(event).append("\"} ")
                .append(counterValue(event)).append('\n');
        }
        sb.append("# HELP traderx_order_match_latency_seconds Matcher latency histogram.\n")
            .append("# TYPE traderx_order_match_latency_seconds histogram\n")
            .append("traderx_order_match_latency_seconds_bucket{le=\"0.01\"} 0\n")
            .append("traderx_order_match_latency_seconds_bucket{le=\"0.05\"} 0\n")
            .append("traderx_order_match_latency_seconds_bucket{le=\"0.1\"} 0\n")
            .append("traderx_order_match_latency_seconds_bucket{le=\"0.25\"} 0\n")
            .append("traderx_order_match_latency_seconds_bucket{le=\"0.5\"} 0\n")
            .append("traderx_order_match_latency_seconds_bucket{le=\"1\"} 0\n")
            .append("traderx_order_match_latency_seconds_bucket{le=\"+Inf\"} ").append(matcherTicks.get()).append('\n')
            .append("traderx_order_match_latency_seconds_sum 0\n")
            .append("traderx_order_match_latency_seconds_count ").append(matcherTicks.get()).append('\n');
        return sb.toString();
    }

    private OrderResponse persistNewOrder(OrderCreateRequest request, String security) {
        Instant now = clock.instant();
        OrderRecord order = new OrderRecord();
        order.setOrderId(nextOrderId());
        order.setAccountId(request.getAccountId());
        order.setSecurity(security);
        order.setSide(request.getSide());
        order.setQuantity(request.getQuantity());
        order.setRemainingQuantity(request.getQuantity());
        order.setLimitPrice(roundPrice(request.getLimitPrice()));
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        OrderRecord saved = orderRepository.save(order);
        incrementEvent("create");
        return OrderResponse.from(saved, lastPrices.get(saved.getSecurity()));
    }

    private void tryAutoFill(String orderId, BigDecimal marketPrice) {
        tryAutoFill(orderId, marketPrice, false);
    }

    private void tryAutoFill(String orderId, BigDecimal marketPrice, boolean forceFill) {
        ReentrantLock orderLock = orderLock(orderId);
        orderLock.lock();
        try {
            OrderRecord order = orderRepository.findById(orderId).orElse(null);
            if (order == null || !isOpen(order) || order.getPendingTradeId() != null
                || order.getRemainingQuantity() == null || order.getRemainingQuantity() <= 0) {
                return;
            }
            if (!forceFill && !isInTheMoney(order, marketPrice)) {
                return;
            }
            autoFillAttempts.incrementAndGet();
            boolean treasury = isTreasuryKey(order.getSecurity());
            int fillQty = treasury
                ? treasuryFillQuantity(order.getRemainingQuantity())
                : forceFill
                    ? order.getRemainingQuantity()
                    : inheritedFillQuantity(order.getRemainingQuantity());
            if (treasury) {
                preparePending(order, fillQty, marketPrice);
                orderRepository.saveAndFlush(order);
            } else {
                if (!submitAsynchronousTrade(order, fillQty)) {
                    tradeSubmitFailures.incrementAndGet();
                    incrementEvent("reject");
                    order.setUpdatedAt(clock.instant());
                    orderRepository.save(order);
                    return;
                }
                applyFill(order, fillQty, marketPrice, forceFill);
                orderRepository.save(order);
                publishOrderUpdate(order);
                autoFillSuccess.incrementAndGet();
                return;
            }
        } finally {
            orderLock.unlock();
        }
        reconcilePending(orderId, false);
    }

    private void reconcilePending(String orderId, boolean retry) {
        if (!reconciliationsInFlight.add(orderId)) {
            return;
        }
        try {
            PendingExecution pending = snapshotPending(orderId, retry);
            if (pending == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", pending.tradeId());
            payload.put("security", pending.security());
            payload.put("quantity", pending.quantity());
            payload.put("price", pending.price());
            payload.put("accountId", pending.accountId());
            payload.put("side", pending.side().name());
            payload.put("sourceOrderId", pending.orderId());
            try {
                ResponseEntity<TradeBookingResult> response = restTemplate.postForEntity(
                    tradeProcessorUrl + "/tradeservice/order",
                    payload,
                    TradeBookingResult.class);
                TradeBookingResult.BookedTrade trade =
                    response.getBody() == null ? null : response.getBody().getTrade();
                if (trade == null || trade.getState() == null) {
                    return;
                }
                applyReconciliationResponse(pending, trade);
            } catch (HttpClientErrorException nonRetryable) {
                rejectPendingIfCurrent(pending);
            } catch (HttpServerErrorException | ResourceAccessException retryable) {
                tradeSubmitFailures.incrementAndGet();
                touchPendingIfCurrent(pending);
            }
        } finally {
            reconciliationsInFlight.remove(orderId);
        }
    }

    private PendingExecution snapshotPending(String orderId, boolean retry) {
        ReentrantLock orderLock = orderLock(orderId);
        orderLock.lock();
        try {
            OrderRecord order = orderRepository.findById(orderId).orElse(null);
            if (order == null || order.getPendingTradeId() == null) {
                return null;
            }
            if (retry) {
                reconciliationRetries.incrementAndGet();
            }
            return new PendingExecution(
                order.getOrderId(),
                order.getPendingTradeId(),
                order.getSecurity(),
                order.getPendingQuantity(),
                order.getPendingPrice(),
                order.getAccountId(),
                order.getSide());
        } finally {
            orderLock.unlock();
        }
    }

    private void applyReconciliationResponse(
        PendingExecution pending,
        TradeBookingResult.BookedTrade trade) {
        ReentrantLock orderLock = orderLock(pending.orderId());
        orderLock.lock();
        try {
            OrderRecord current = orderRepository.findById(pending.orderId()).orElse(null);
            if (!pendingMatches(current, pending)) {
                return;
            }
            if ("Settled".equals(trade.getState())) {
                applyFill(
                    current,
                    pending.quantity(),
                    trade.getPrice() == null ? pending.price() : trade.getPrice(),
                    false);
                clearPending(current);
                orderRepository.save(current);
                publishOrderUpdate(current);
                autoFillSuccess.incrementAndGet();
                reconciliationSuccesses.incrementAndGet();
            } else if ("Rejected".equals(trade.getState())) {
                rejectPending(current);
            }
        } finally {
            orderLock.unlock();
        }
    }

    private void rejectPendingIfCurrent(PendingExecution pending) {
        ReentrantLock orderLock = orderLock(pending.orderId());
        orderLock.lock();
        try {
            OrderRecord current = orderRepository.findById(pending.orderId()).orElse(null);
            if (pendingMatches(current, pending)) {
                rejectPending(current);
            }
        } finally {
            orderLock.unlock();
        }
    }

    private void touchPendingIfCurrent(PendingExecution pending) {
        ReentrantLock orderLock = orderLock(pending.orderId());
        orderLock.lock();
        try {
            OrderRecord current = orderRepository.findById(pending.orderId()).orElse(null);
            if (pendingMatches(current, pending)) {
                current.setUpdatedAt(clock.instant());
                orderRepository.save(current);
            }
        } finally {
            orderLock.unlock();
        }
    }

    private boolean pendingMatches(OrderRecord order, PendingExecution pending) {
        return order != null
            && pending.tradeId().equals(order.getPendingTradeId())
            && pending.quantity().equals(order.getPendingQuantity())
            && pending.price().compareTo(order.getPendingPrice()) == 0;
    }

    private void rejectPending(OrderRecord order) {
        clearPending(order);
        order.setStatus(OrderStatus.REJECTED);
        order.setUpdatedAt(clock.instant());
        orderRepository.save(order);
        publishOrderUpdate(order);
        reconciliationRejections.incrementAndGet();
        incrementEvent("reject");
    }

    private void preparePending(OrderRecord order, int quantity, BigDecimal price) {
        int filledBefore = order.getQuantity() - order.getRemainingQuantity();
        String tradeId = deriveTradeId(order.getOrderId(), filledBefore);
        order.setPendingTradeId(tradeId);
        order.setPendingQuantity(quantity);
        order.setPendingPrice(roundPrice(price));
        order.setPendingSubmittedAt(clock.instant());
        order.setUpdatedAt(clock.instant());
    }

    public static String deriveTradeId(String orderId, int filledBefore) {
        String id = orderId + "-exec-" + filledBefore;
        if (id.length() > 50) {
            throw new IllegalArgumentException("Derived trade ID exceeds Trades.ID VARCHAR(50)");
        }
        return id;
    }

    static int treasuryFillQuantity(int remaining) {
        if (remaining <= 100_000) {
            return remaining;
        }
        return Math.max(100, (remaining / 2 / 100) * 100);
    }

    private int inheritedFillQuantity(int remaining) {
        return remaining < fillFullThreshold ? remaining : Math.max(1, (remaining + 1) / 2);
    }

    private boolean submitAsynchronousTrade(OrderRecord order, int quantity) {
        try {
            Map<String, Object> payload = Map.of(
                "security", order.getSecurity(),
                "quantity", quantity,
                "accountId", order.getAccountId(),
                "side", order.getSide().name());
            return restTemplate.postForEntity(tradeServiceUrl, payload, Map.class)
                .getStatusCode().is2xxSuccessful();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void applyFill(OrderRecord order, int fillQty, BigDecimal executionPrice, boolean forceFill) {
        int remainingAfter = Math.max(0, order.getRemainingQuantity() - fillQty);
        order.setRemainingQuantity(remainingAfter);
        order.setLastExecutionPrice(roundPrice(executionPrice));
        order.setLastFillQuantity(fillQty);
        order.setUpdatedAt(clock.instant());
        order.setStatus(remainingAfter == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
        incrementEvent(remainingAfter == 0 ? "fill" : "partial_fill");
        if (forceFill) {
            incrementEvent("force_fill");
        }
    }

    private void clearPending(OrderRecord order) {
        order.setPendingTradeId(null);
        order.setPendingQuantity(null);
        order.setPendingPrice(null);
        order.setPendingSubmittedAt(null);
    }

    private InstrumentMetadata resolveTreasuryMetadata(String security) {
        if (!isTreasuryKey(security)) {
            return null;
        }
        try {
            InstrumentMetadata instrument = restTemplate.getForObject(
                referenceDataUrl + "/instruments/" + security,
                InstrumentMetadata.class);
            if (instrument == null || !instrument.isTreasury()) {
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported Treasury instrument");
            }
            return instrument;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Treasury reference metadata is unavailable", ex);
        }
    }

    private void validateTreasuryCreate(
        OrderCreateRequest request,
        InstrumentMetadata instrument,
        String security) {
        if (request.getQuantity() < 100) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Treasury quantity must be at least 100.");
        }
        if (request.getQuantity() % 100 != 0) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Treasury quantity must be a multiple of 100.");
        }
        if (isMatured(instrument)) {
            throw new ResponseStatusException(BAD_REQUEST, "Treasury instrument has matured");
        }
        if (request.getSide() == OrderSide.Sell) {
            int settled = settledQuantity(request.getAccountId(), security);
            long reserved = Optional.ofNullable(
                orderRepository.sumOpenTreasurySellReservations(request.getAccountId(), security))
                .orElse(0L);
            long available = Math.max(0L, (long) settled - reserved);
            if (request.getQuantity() > available) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "You cannot sell more Treasury face amount than you own and have available.");
            }
        }
    }

    private int settledQuantity(Integer accountId, String security) {
        try {
            PositionSnapshot[] positions = restTemplate.getForObject(
                positionServiceUrl + "/positions/" + accountId,
                PositionSnapshot[].class);
            return Arrays.stream(positions == null ? new PositionSnapshot[0] : positions)
                .filter(position -> security.equalsIgnoreCase(position.getSecurity()))
                .map(PositionSnapshot::getQuantity)
                .filter(quantity -> quantity != null)
                .findFirst()
                .orElse(0);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                BAD_GATEWAY,
                "Settled Treasury position is unavailable",
                ex);
        }
    }

    private boolean isMatured(InstrumentMetadata instrument) {
        if (Boolean.TRUE.equals(instrument.getMatured())) {
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

    private boolean isTreasuryKey(String security) {
        return security != null && security.toUpperCase(Locale.ROOT).startsWith(TREASURY_PREFIX);
    }

    public void publishOrderUpdate(OrderResponse order) {
        if (order == null || order.getAccountId() == null) {
            return;
        }
        String accountTopic = "/accounts/" + order.getAccountId() + "/orders";
        try {
            orderPublisher.publish(accountTopic, order);
            orderPublisher.publish(ALL_ORDERS_TOPIC, order);
        } catch (PubSubException ex) {
            log.warn("Unable to publish order update for {}", order.getOrderId(), ex);
        }
    }

    private void publishOrderUpdate(OrderRecord order) {
        publishOrderUpdate(OrderResponse.from(order, lastPrices.get(order.getSecurity())));
    }

    private boolean filterByStatus(OrderRecord order, String statusFilter) {
        if ("open".equals(statusFilter)) return isOpen(order);
        if ("all".equals(statusFilter)) return true;
        try {
            return order.getStatus() == OrderStatus.valueOf(statusFilter.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isOpen(OrderRecord order) {
        return OPEN_STATUSES.contains(order.getStatus());
    }

    private boolean isInTheMoney(OrderRecord order, BigDecimal marketPrice) {
        if (order.getLimitPrice() == null || marketPrice == null || order.getSide() == null) {
            return false;
        }
        return order.getSide() == OrderSide.Buy
            ? marketPrice.compareTo(order.getLimitPrice()) <= 0
            : marketPrice.compareTo(order.getLimitPrice()) >= 0;
    }

    private void validateCreateRequest(OrderCreateRequest request) {
        if (request == null
            || request.getAccountId() == null || request.getAccountId() <= 0
            || !StringUtils.hasText(request.getSecurity())
            || request.getSide() == null
            || request.getQuantity() == null || request.getQuantity() <= 0
            || request.getLimitPrice() == null
            || request.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid order payload");
        }
    }

    private String nextOrderId() {
        return String.format("ord-013-%04d", nextOrderSequence.getAndIncrement());
    }

    private ReentrantLock orderLock(String orderId) {
        return orderLocks[stripeIndex(orderId)];
    }

    private ReentrantLock reservationLock(Integer accountId, String security) {
        return reservationLocks[stripeIndex(accountId + "|" + security)];
    }

    private int stripeIndex(String key) {
        return Math.floorMod(key.hashCode(), LOCK_STRIPE_COUNT);
    }

    private static ReentrantLock[] createLockStripes() {
        ReentrantLock[] stripes = new ReentrantLock[LOCK_STRIPE_COUNT];
        for (int index = 0; index < stripes.length; index++) {
            stripes[index] = new ReentrantLock();
        }
        return stripes;
    }

    private OrderRecord findOrder(String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));
    }

    private BigDecimal roundPrice(BigDecimal input) {
        return input == null ? null : input.setScale(3, RoundingMode.HALF_UP);
    }

    private void initializeData() {
        if (seedEnabled && orderRepository.count() == 0) {
            List<OrderRecord> seed = List.of(
                seedOrder("ord-013-0001", 22214, "IBM", OrderSide.Buy, 1800, 1800, "187.250", OrderStatus.NEW),
                seedOrder("ord-013-0002", 22214, "MSFT", OrderSide.Sell, 900, 650, "412.000", OrderStatus.PARTIALLY_FILLED),
                seedOrder("ord-013-0003", 44044, "JPM", OrderSide.Buy, 1200, 1200, "191.500", OrderStatus.NEW),
                seedOrder("ord-013-0004", 52355, "GS", OrderSide.Sell, 300, 0, "498.000", OrderStatus.FILLED),
                seedOrder("ord-013-0005", 10031, "NVDA", OrderSide.Buy, 450, 450, "905.125", OrderStatus.NEW),
                seedOrder("ord-013-0006", 10031, "C", OrderSide.Sell, 1000, 1000, "61.500", OrderStatus.NEW),
                seedOrder("ord-013-0007", 62654, "META", OrderSide.Sell, 500, 500, "507.880", OrderStatus.NEW));
            orderRepository.saveAll(seed);
        }
        initializeSequence();
        refreshCountersFromDatabase();
    }

    private OrderRecord seedOrder(
        String id, int accountId, String security, OrderSide side,
        int quantity, int remaining, String limit, OrderStatus status) {
        Instant now = clock.instant();
        OrderRecord order = new OrderRecord();
        order.setOrderId(id);
        order.setAccountId(accountId);
        order.setSecurity(security);
        order.setSide(side);
        order.setQuantity(quantity);
        order.setRemainingQuantity(remaining);
        order.setLimitPrice(new BigDecimal(limit));
        order.setStatus(status);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    private void initializeSequence() {
        int maxId = orderRepository.findAllOrderIds().stream()
            .map(ORDER_ID_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(matcher -> Integer.parseInt(matcher.group(1)))
            .max(Integer::compareTo)
            .orElse(0);
        nextOrderSequence.set(maxId + 1);
    }

    private void initializeCounters() {
        for (String event : List.of("create", "partial_fill", "fill", "cancel", "reject", "force_fill")) {
            eventCounters.put(event, new AtomicLong());
        }
    }

    private void refreshCountersFromDatabase() {
        setCounter("create", orderRepository.count());
        setCounter("partial_fill", orderRepository.countByStatus(OrderStatus.PARTIALLY_FILLED));
        setCounter("fill", orderRepository.countByStatus(OrderStatus.FILLED));
        setCounter("cancel", orderRepository.countByStatus(OrderStatus.CANCELED));
        setCounter("reject", orderRepository.countByStatus(OrderStatus.REJECTED));
        setCounter("force_fill", 0);
    }

    private void incrementEvent(String event) {
        eventCounters.computeIfAbsent(event, ignored -> new AtomicLong()).incrementAndGet();
    }

    private long counterValue(String event) {
        return eventCounters.computeIfAbsent(event, ignored -> new AtomicLong()).get();
    }

    private void setCounter(String event, long value) {
        eventCounters.computeIfAbsent(event, ignored -> new AtomicLong()).set(value);
    }

    private String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private void counter(StringBuilder sb, String name, String help, long value) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
            .append("# TYPE ").append(name).append(" counter\n")
            .append(name).append(' ').append(value).append('\n');
    }

    private void gauge(StringBuilder sb, String name, String help, long value) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
            .append("# TYPE ").append(name).append(" gauge\n")
            .append(name).append(' ').append(value).append('\n');
    }

    private record PendingExecution(
        String orderId,
        String tradeId,
        String security,
        Integer quantity,
        BigDecimal price,
        Integer accountId,
        OrderSide side) {}
}
