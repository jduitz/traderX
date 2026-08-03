'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  approximateYtmPercent,
  isMatured,
  normalizeTreasuryQuote,
  toTreasuryPayload,
  updateTreasuryCleanPrice
} = require('../src/treasury-pricing');

test('Treasury changes are maturity-sensitive, correlated, bounded, and rounded', () => {
  const short = {
    seedPrice: 99.878,
    price: 99.878,
    originalTermYears: 2
  };
  const long = {
    seedPrice: 99.293,
    price: 99.293,
    originalTermYears: 30
  };
  const shortNext = updateTreasuryCleanPrice(short, 1, 1);
  const longNext = updateTreasuryCleanPrice(long, 1, 1);
  assert.equal(shortNext, 99.883);
  assert.equal(longNext, 99.343);
  assert.ok(longNext - long.price > shortNext - short.price);

  let current = { ...long };
  for (let i = 0; i < 500; i += 1) {
    current = {
      ...current,
      price: updateTreasuryCleanPrice(current, 1, 1)
    };
  }
  assert.ok(current.price <= long.seedPrice + 1);
  assert.equal(current.price, Number(current.price.toFixed(3)));
});

test('approximate YTM uses the same quote instant and maturity boundary', () => {
  const before = '2028-06-29T23:59:59.000Z';
  const at = '2028-06-30T00:00:00.000Z';
  assert.equal(isMatured('2028-06-30', before), false);
  assert.equal(isMatured('2028-06-30', at), true);
  assert.equal(approximateYtmPercent(4.125, 99.878, '2028-06-30', at), null);
  assert.ok(approximateYtmPercent(4.125, 99.878, '2028-06-30', '2027-06-30T00:00:00.000Z') > 4);
});

test('Treasury payload carries one timestamp and clean-price semantics', () => {
  const quote = normalizeTreasuryQuote('UST-20280630', {
    assetClass: 'US_TREASURY',
    officialCleanPrice: 99.878432,
    runtimeSeedCleanPrice: 99.878,
    couponRatePercent: 4.125,
    originalTermYears: 2,
    maturityDate: '2028-06-30'
  });
  const timestamp = '2027-06-30T12:00:00.000Z';
  const payload = toTreasuryPayload(quote, timestamp);

  assert.equal(payload.price, 99.878);
  assert.equal(payload.cleanPrice, 99.878);
  assert.equal(payload.priceSemantics, 'CLEAN_PERCENT_OF_PAR');
  assert.equal(payload.asOf, timestamp);
  assert.equal(payload.quoteTimestamp, timestamp);
  assert.equal(payload.simulated, true);
  assert.equal(payload.officialSeedCleanPrice, 99.878432);
  assert.ok(payload.approximateYtmPercent > 4);
});
