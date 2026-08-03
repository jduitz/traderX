import * as assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { loadCsvData, TREASURY_SEEDS } from './load-csv-data';

const expected = [
  ['UST-20280630', 'UST 2Y', 'BBG022ZR1Z79', 99.878432, 99.878, 2],
  ['UST-20310630', 'UST 5Y', 'BBG022ZR1Z51', 99.664909, 99.665, 5],
  ['UST-20360515', 'UST 10Y', 'BBG0221YLR31', 99.256552, 99.257, 10],
  ['UST-20460515', 'UST 20Y', 'BBG0226BZH97', 98.481099, 98.481, 20],
  ['UST-20560515', 'UST 30Y', 'BBG0221YLR40', 99.292811, 99.293, 30],
] as const;

after(() => {
  delete process.env.TRADERX_FIXED_UTC_INSTANT;
});

test('loads all five Treasury Debt records with verified FIGIs and price provenance', async () => {
  process.env.TRADERX_FIXED_UTC_INSTANT = '2026-07-30T12:00:00Z';
  const supported = new Set(expected.map(([key]) => key));
  const instruments = await loadCsvData({ supportedTickers: supported });

  assert.equal(instruments.length, 5);
  for (const [key, shortDisplayName, figi, officialPrice, runtimePrice, originalTerm] of expected) {
    const instrument = instruments.find((candidate) => candidate.instrumentKey === key);
    assert.ok(instrument, `${key} is present`);
    assert.equal(instrument.displayName.length > 0, true);
    assert.equal(instrument.shortDisplayName, shortDisplayName);
    assert.equal(instrument.assetClass, 'US_TREASURY');
    assert.equal(instrument.securityType, 'Debt');
    assert.equal(instrument.currency, 'USD');
    assert.equal(instrument.matured, false);
    assert.equal(instrument.debtEconomics?.fixedInterest.rateType, 'Fixed');
    assert.equal(instrument.debtEconomics?.fixedInterest.couponFrequency, 'Semiannual');
    assert.equal(instrument.debtEconomics?.principalRepayment.style, 'Bullet');
    assert.equal(instrument.debtEconomics?.originalTermYears, originalTerm);
    assert.equal(instrument.debtEconomics?.priceProvenance.officialCleanPrice, officialPrice);
    assert.equal(instrument.debtEconomics?.priceProvenance.runtimeSeedCleanPrice, runtimePrice);
    assert.ok(instrument.identifiers.some(
      (identifier) => identifier.identifierType === 'FIGI' && identifier.identifier === figi));
    assert.equal(instrument.identifiers.some(
      (identifier) => ['CUSIP', 'ISIN', 'BBGTICKER'].includes(identifier.identifierType)), false);
  }
});

test('seed table contains only the bounded five-instrument Treasury universe', () => {
  assert.deepEqual(
    TREASURY_SEEDS.map((seed) => seed.instrumentKey),
    expected.map(([key]) => key));
});

test('maturity changes at the UTC maturity boundary', async () => {
  const supported = new Set(['UST-20280630']);
  process.env.TRADERX_FIXED_UTC_INSTANT = '2028-06-29T23:59:59Z';
  const justBefore = await loadCsvData({ supportedTickers: supported });
  assert.equal(justBefore[0].matured, false);

  process.env.TRADERX_FIXED_UTC_INSTANT = '2028-06-30T00:00:00Z';
  const atMaturity = await loadCsvData({ supportedTickers: supported });
  assert.equal(atMaturity[0].matured, true);
});
