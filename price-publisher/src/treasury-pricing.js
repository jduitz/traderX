'use strict';

const TREASURY_PROFILE_BY_TERM = Object.freeze({
  2: { maxStep: 0.005, maxDistance: 0.15 },
  5: { maxStep: 0.010, maxDistance: 0.30 },
  10: { maxStep: 0.020, maxDistance: 0.50 },
  20: { maxStep: 0.035, maxDistance: 0.75 },
  30: { maxStep: 0.050, maxDistance: 1.00 }
});

function round3(value) {
  return Math.round((Number(value) + Number.EPSILON) * 1000) / 1000;
}

function clamp(value, low, high) {
  return Math.max(low, Math.min(high, value));
}

function yearsRemaining(maturityDate, quoteTimestamp) {
  const remainingMs = new Date(`${maturityDate}T00:00:00.000Z`).getTime()
    - new Date(quoteTimestamp).getTime();
  return Math.max(0, remainingMs / 86_400_000 / 365.25);
}

function approximateYtmPercent(couponRatePercent, cleanPrice, maturityDate, quoteTimestamp) {
  const years = yearsRemaining(maturityDate, quoteTimestamp);
  if (years <= 0) {
    return null;
  }
  const clean = Number(cleanPrice);
  return round3(
    ((Number(couponRatePercent) + (100 - clean) / years) / ((100 + clean) / 2)) * 100
  );
}

function isMatured(maturityDate, quoteTimestamp) {
  return new Date(quoteTimestamp).getTime() >= new Date(`${maturityDate}T00:00:00.000Z`).getTime();
}

function updateTreasuryCleanPrice(quote, sharedRoll, localRoll) {
  const profile = TREASURY_PROFILE_BY_TERM[quote.originalTermYears];
  if (!profile) {
    throw new Error(`unsupported Treasury original term: ${quote.originalTermYears}`);
  }
  const seedPrice = Number(quote.seedPrice);
  const currentPrice = Number(quote.price);
  const change = profile.maxStep * (0.8 * sharedRoll + 0.2 * localRoll)
    + 0.02 * (seedPrice - currentPrice);
  return round3(clamp(
    currentPrice + change,
    seedPrice - profile.maxDistance,
    seedPrice + profile.maxDistance
  ));
}

function normalizeTreasuryQuote(ticker, snapshotEntry) {
  const seedPrice = round3(Number(snapshotEntry.runtimeSeedCleanPrice));
  return {
    ticker,
    instrumentKey: ticker,
    assetClass: 'US_TREASURY',
    openPrice: seedPrice,
    closePrice: seedPrice,
    price: seedPrice,
    seedPrice,
    officialCleanPrice: Number(snapshotEntry.officialCleanPrice),
    couponRatePercent: Number(snapshotEntry.couponRatePercent),
    originalTermYears: Number(snapshotEntry.originalTermYears),
    maturityDate: snapshotEntry.maturityDate,
    source: 'simulated-us-treasury-auction-seed',
    simulated: true
  };
}

function toTreasuryPayload(quote, timestamp) {
  return {
    ticker: quote.ticker,
    instrumentKey: quote.instrumentKey || quote.ticker,
    price: quote.price,
    openPrice: quote.openPrice,
    closePrice: quote.closePrice,
    asOf: timestamp,
    source: quote.source,
    assetClass: quote.assetClass,
    cleanPrice: quote.price,
    priceSemantics: 'CLEAN_PERCENT_OF_PAR',
    approximateYtmPercent: approximateYtmPercent(
      quote.couponRatePercent,
      quote.price,
      quote.maturityDate,
      timestamp
    ),
    quoteTimestamp: timestamp,
    maturityDate: quote.maturityDate,
    matured: isMatured(quote.maturityDate, timestamp),
    simulated: true,
    officialSeedCleanPrice: quote.officialCleanPrice
  };
}

module.exports = {
  TREASURY_PROFILE_BY_TERM,
  approximateYtmPercent,
  isMatured,
  normalizeTreasuryQuote,
  round3,
  toTreasuryPayload,
  updateTreasuryCleanPrice,
  yearsRemaining
};
