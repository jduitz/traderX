# Component List

State: `017-us-treasury-trading`

| ID | Label | Kind | Description |
| --- | --- | --- | --- |
| `trader` | Trader | actor | Uses unified tickets and blotters. |
| `ui` | Angular UI | component | Groups stocks, ETFs, and Treasuries. |
| `reference` | Reference Data | service | Serves instrumentKey/displayName and CDM-shaped Debt economics. |
| `pricing` | Price Publisher | service | Publishes simulated clean prices and approximate YTM. |
| `matcher` | Order Matcher | service | Reserves face amount and reconciles pending Treasury executions. |
| `tradeService` | Trade Service | service | Keeps inherited asynchronous stock/ETF submission and early Treasury validation. |
| `processor` | Trade Processor | service | Authoritative idempotent booking with pessimistic position locking. |
| `postgres` | PostgreSQL | database | Stores accounts, orders, pending execution data, trades, and positions. |
| `nats` | NATS | broker | Carries price and account-scoped trade/position/order updates. |
