import { Account } from '../model/account.model';
import { User } from '../model/user.model';
import { faker } from '@faker-js/faker';
import { Stock } from '../model/symbol.model';
import { Trade, State, Side, Position } from '../model/trade.model';

export function sleep(delay = 0) {
  return new Promise((re) => setTimeout(re, delay));
}

export function createUser(): User {
  return {
    fullName: faker.person.firstName(),
    email: faker.internet.email(),
    department: faker.commerce.department(),
    logonId: faker.string.alphanumeric(5),
    employeeId: faker.string.alpha(5),
    photoUrl: 'testurl'
  };
}

export function createAccount(): Account {
  return {
    displayName: faker.company.name(),
    id: faker.number.int()
  };
}

export function createStock(): Stock {
  return {
    displayName: faker.company.name(),
    instrumentKey: faker.string.alpha(4).toUpperCase(),
    assetClass: 'Stock',
    currency: 'USD',
    securityType: 'Equity',
    matured: false,
    observedAt: new Date().toISOString()
  };
}

export function createTrade(): Trade {
  return {
    created: faker.date.recent(),
    id: faker.string.alpha(5),
    state: State.Pending,
    side: Side.Buy,
    ...createPosition()
  };
}

export function createPosition(): Position {
  return {
    accountid: faker.number.int(),
    quantity: faker.number.int(100),
    security: faker.string.alpha(4),
    updated: faker.date.recent()
  };
}
