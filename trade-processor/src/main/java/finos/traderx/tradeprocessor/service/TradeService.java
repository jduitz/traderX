package finos.traderx.tradeprocessor.service;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TradeService {
  private static final Logger log = LoggerFactory.getLogger(TradeService.class);
  private static final int MAX_TRADE_ID_LENGTH = 50;
  private static final int BOOKING_LOCK_STRIPE_COUNT = 256;
  private static final String TREASURY_PREFIX = "UST-";

  private final TradeRepository tradeRepository;
  private final PositionRepository positionRepository;
  private final Publisher<Trade> tradePublisher;
  private final Publisher<Position> positionPublisher;
  private final InstrumentMetadataClient instrumentClient;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final Object[] bookingLocks = createBookingLockStripes();

  public TradeService(
      TradeRepository tradeRepository,
      PositionRepository positionRepository,
      Publisher<Trade> tradePublisher,
      Publisher<Position> positionPublisher,
      InstrumentMetadataClient instrumentClient,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.tradeRepository = tradeRepository;
    this.positionRepository = positionRepository;
    this.tradePublisher = tradePublisher;
    this.positionPublisher = positionPublisher;
    this.instrumentClient = instrumentClient;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  public TradeBookingResult processTrade(TradeOrder order) {
    String tradeId = order.getId() == null || order.getId().isBlank()
        ? UUID.randomUUID().toString()
        : order.getId().trim();
    if (tradeId.length() > MAX_TRADE_ID_LENGTH) {
      throw new IllegalArgumentException("Trade ID exceeds Trades.ID VARCHAR(50)");
    }
    order.setId(tradeId);

    Object bookingLock = bookingLock(tradeId);
    synchronized (bookingLock) {
      try {
        TradeBookingResult existing = existingResult(tradeId);
        if (existing != null) {
          return existing;
        }
        boolean treasuryKey = isCanonicalTreasuryKey(order.getSecurity());
        // Canonical UST keys fail closed, but reference-data network I/O never
        // runs inside the database transaction. Non-Treasury booking keeps the
        // inherited path and never performs this lookup.
        InstrumentMetadata instrument = treasuryKey
            ? instrumentClient.resolve(order.getSecurity())
            : null;
        /*
         * Lock ordering invariant: the fixed booking stripe is acquired before
         * any database position lock. No path may acquire these in reverse.
         */
        BookingOutcome outcome = transactionTemplate.execute(
            status -> bookInTransaction(order, treasuryKey, instrument));
        if (outcome == null) {
          throw new IllegalStateException("Trade booking transaction returned no result");
        }
        publish(outcome);
        return outcome.result();
      } catch (DataIntegrityViolationException duplicate) {
        TradeBookingResult existing = existingResult(tradeId);
        if (existing != null) {
          return existing;
        }
        throw duplicate;
      }
    }
  }

  private BookingOutcome bookInTransaction(
      TradeOrder order,
      boolean treasuryKey,
      InstrumentMetadata instrument) {
    Trade existing = tradeRepository.findById(order.getId()).orElse(null);
    if (existing != null) {
      return new BookingOutcome(
          new TradeBookingResult(existing, currentPosition(order)),
          false,
          false);
    }

    if (treasuryKey && (instrument == null || !instrument.isTreasury())) {
      return persistRejected(order, "Treasury reference metadata is unavailable");
    }

    boolean treasury = treasuryKey && instrument != null && instrument.isTreasury();
    if (treasury && (order.getQuantity() == null || order.getQuantity() < 100)) {
      return persistRejected(order, "Treasury quantity must be at least 100.");
    }
    if (treasury && order.getQuantity() % 100 != 0) {
      return persistRejected(order, "Treasury quantity must be a multiple of 100.");
    }
    if (treasury && instrumentClient.isMatured(instrument)) {
      return persistRejected(order, "Treasury instrument has matured");
    }

    Position position = treasury
        ? positionRepository.findForUpdate(order.getAccountId(), order.getSecurity()).orElse(null)
        : positionRepository.findByAccountIdAndSecurity(order.getAccountId(), order.getSecurity());
    if (position == null) {
      position = newPosition(order);
    }

    int oldQuantity = position.getQuantity() == null ? 0 : position.getQuantity();
    if (treasury && order.getSide() == TradeSide.Sell && order.getQuantity() > oldQuantity) {
      return persistRejected(order, "You cannot sell more Treasury face amount than you own and have available.");
    }

    Trade trade = newTrade(order);
    tradeRepository.saveAndFlush(trade);

    int signedQuantity = (order.getSide() == TradeSide.Buy ? 1 : -1) * order.getQuantity();
    int newQuantity = oldQuantity + signedQuantity;
    BigDecimal oldAverage = scaled(position.getAverageCostBasis());
    BigDecimal executionPrice = scaled(trade.getPrice());
    position.setQuantity(newQuantity);
    position.setAverageCostBasis(treasury
        ? treasuryAverageCost(order.getSide(), oldQuantity, oldAverage, order.getQuantity(), executionPrice, newQuantity)
        : inheritedAverageCost(oldQuantity, oldAverage, signedQuantity, executionPrice, newQuantity));
    position.setUpdated(Date.from(clock.instant()));
    positionRepository.save(position);

    trade.setState(TradeState.Processing);
    trade.setUpdated(Date.from(clock.instant()));
    trade.setState(TradeState.Settled);
    tradeRepository.save(trade);

    TradeBookingResult result = new TradeBookingResult(trade, position);
    return new BookingOutcome(result, true, true);
  }

  private TradeBookingResult existingResult(String tradeId) {
    return tradeRepository.findById(tradeId)
        .map(trade -> new TradeBookingResult(
            trade,
            positionRepository.findByAccountIdAndSecurity(trade.getAccountId(), trade.getSecurity())))
        .orElse(null);
  }

  private Position currentPosition(TradeOrder order) {
    return positionRepository.findByAccountIdAndSecurity(order.getAccountId(), order.getSecurity());
  }

  private BookingOutcome persistRejected(TradeOrder order, String reason) {
    Trade rejected = newTrade(order);
    rejected.setState(TradeState.Rejected);
    rejected.setRejectionReason(reason);
    rejected.setUpdated(Date.from(clock.instant()));
    tradeRepository.saveAndFlush(rejected);
    return new BookingOutcome(
        new TradeBookingResult(rejected, currentPosition(order)),
        true,
        false);
  }

  private Trade newTrade(TradeOrder order) {
    Trade trade = new Trade();
    trade.setId(order.getId());
    trade.setAccountId(order.getAccountId());
    trade.setSecurity(order.getSecurity());
    trade.setSide(order.getSide());
    trade.setQuantity(order.getQuantity());
    trade.setPrice(scaled(order.getPrice()));
    trade.setSourceOrderId(order.getSourceOrderId());
    Date now = Date.from(clock.instant());
    trade.setCreated(now);
    trade.setUpdated(now);
    trade.setState(TradeState.New);
    return trade;
  }

  private Position newPosition(TradeOrder order) {
    Position position = new Position();
    position.setAccountId(order.getAccountId());
    position.setSecurity(order.getSecurity());
    position.setQuantity(0);
    position.setAverageCostBasis(scaled(BigDecimal.ZERO));
    return position;
  }

  private BigDecimal treasuryAverageCost(
      TradeSide side,
      int oldQuantity,
      BigDecimal oldAverage,
      int tradeQuantity,
      BigDecimal executionPrice,
      int newQuantity) {
    if (newQuantity == 0) {
      return scaled(BigDecimal.ZERO);
    }
    if (side == TradeSide.Sell) {
      return oldAverage;
    }
    BigDecimal totalCleanCost = oldAverage.multiply(BigDecimal.valueOf(oldQuantity))
        .add(executionPrice.multiply(BigDecimal.valueOf(tradeQuantity)));
    return totalCleanCost.divide(BigDecimal.valueOf(newQuantity), 3, RoundingMode.HALF_UP);
  }

  private BigDecimal inheritedAverageCost(
      int oldQuantity,
      BigDecimal oldAverage,
      int signedQuantity,
      BigDecimal executionPrice,
      int newQuantity) {
    if (newQuantity == 0) {
      return scaled(BigDecimal.ZERO);
    }
    return oldAverage.multiply(BigDecimal.valueOf(oldQuantity))
        .add(executionPrice.multiply(BigDecimal.valueOf(signedQuantity)))
        .divide(BigDecimal.valueOf(newQuantity), 3, RoundingMode.HALF_UP);
  }

  private BigDecimal scaled(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
  }

  private boolean isCanonicalTreasuryKey(String security) {
    return security != null && security.startsWith(TREASURY_PREFIX);
  }

  private Object bookingLock(String tradeId) {
    return bookingLocks[Math.floorMod(tradeId.hashCode(), BOOKING_LOCK_STRIPE_COUNT)];
  }

  private static Object[] createBookingLockStripes() {
    Object[] stripes = new Object[BOOKING_LOCK_STRIPE_COUNT];
    for (int index = 0; index < stripes.length; index++) {
      stripes[index] = new Object();
    }
    return stripes;
  }

  private void publish(BookingOutcome outcome) {
    if (!outcome.publishTrade()) {
      return;
    }
    TradeBookingResult result = outcome.result();
    try {
      tradePublisher.publish(
          "/accounts/" + result.getTrade().getAccountId() + "/trades",
          result.getTrade());
      if (outcome.publishPosition() && result.getPosition() != null) {
        positionPublisher.publish(
            "/accounts/" + result.getTrade().getAccountId() + "/positions",
            result.getPosition());
      }
    } catch (PubSubException exc) {
      log.error("Error publishing trade {}", result.getTrade().getId(), exc);
    }
  }

  private record BookingOutcome(
      TradeBookingResult result,
      boolean publishTrade,
      boolean publishPosition) {}
}
