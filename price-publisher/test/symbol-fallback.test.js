'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { ensureTicker, state } = require('../src/main');

test('unknown equities retain lazy fallback while unknown Treasuries fail closed', () => {
  state.prices.clear();
  state.volatilityBands.clear();

  const equity = ensureTicker('xyz');
  assert.equal(equity.ticker, 'XYZ');
  assert.equal(equity.source, 'fallback');
  assert.equal(state.prices.get('XYZ'), equity);
  assert.equal(ensureTicker('UST-20991231'), null);
  assert.equal(state.prices.has('UST-20991231'), false);
});
