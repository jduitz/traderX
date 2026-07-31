# Runtime Topology

State 017 keeps State 016's Docker Compose topology and adds service
dependencies:

- trade processor → reference data, for authoritative Treasury classification
  and maturity;
- trade service → position service, for early settled-face validation;
- order matcher → reference data and position service, for order validation;
- order matcher → trade processor, only for synchronous Treasury fills.

Stocks and ETFs continue through order matcher → trade service → `/trades` →
trade processor. Treasury order fills go order matcher → trade processor HTTP →
PostgreSQL, followed by normal account trade/position publications.

Compose project: `traderx-state-017`
State directory: `us-treasury-trading/`
Database volume: `postgres_state_017_data`

The literal `REFERENCE_DATA_SUPPORTED_TICKERS` and `PRICE_TICKERS` variables
contain general instrument keys for compatibility.
