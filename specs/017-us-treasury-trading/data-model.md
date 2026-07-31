# Data Model

## Instrument

`instrumentKey` is the public reference key. Stocks/ETFs use tickers;
Treasuries use `UST-YYYYMMDD`. `displayName` is asset-neutral. Treasury
`securityType=Debt` carries fixed interest, bullet par repayment, issuer,
currency, dates, original term, and auction-price provenance.

Transactional rows continue to use the existing `Security VARCHAR` columns.
No surrogate key or FIGI migration is introduced.

## Trades

Existing price is `DECIMAL(18,3)`. State 017 adds:

- state value `Rejected`;
- nullable `RejectionReason VARCHAR(255)`;
- nullable `SourceOrderId VARCHAR(32)`.

Treasury `Quantity` is USD face amount. Treasury `Price` is clean percent of
par. A stable order execution ID fits `ID VARCHAR(50)`.

## Positions

The inherited `Quantity INTEGER` and `AverageCostBasis DECIMAL(18,3)` remain.
For Treasuries, average cost basis means average clean purchase price:

`newAverage = (oldFace × oldAverage + buyFace × buyPrice) ÷ newFace`

Sells keep the average unchanged; a zero position resets it to zero.

`costValue = face × averageCleanPrice ÷ 100`
`marketValue = face × currentCleanPrice ÷ 100`
`pnl = marketValue − costValue`

## OrderBook

The public lifecycle remains `NEW`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`,
or `REJECTED`. State 017 adds internal nullable fields:

- `PendingTradeId VARCHAR(50)`
- `PendingQuantity INTEGER`
- `PendingPrice DECIMAL(18,3)`
- `PendingSubmittedAt TIMESTAMP`

Sell reservation is derived by summing `RemainingQuantity` for other open
Treasury sell orders; no reservation table is added.
