# Software Architecture

State: `017-us-treasury-trading`
Title: `U.S. Treasury Trading`

## Architecture Summary

Adds five fixed-rate Treasuries, clean pricing, long-only validation, and retry-safe synchronous order booking while preserving inherited stock and ETF flows.

## Entrypoints



## Notes



## Diagram

See [Component Diagram](./component-diagram.md).

## Detailed Architecture (Spec Extract)

# U.S. Treasury Trading

Adds five fixed-rate Treasuries, clean pricing, long-only validation, and retry-safe synchronous order booking while preserving inherited stock and ETF flows.

- Generated from: `system/architecture.model.json`
- Canonical flows: `system/end-to-end-flows.md`

## Architecture Diagram

```mermaid
flowchart LR
  trader["Trader"]
  ui["Angular UI"]
  reference["Reference Data"]
  pricing["Price Publisher"]
  matcher["Order Matcher"]
  tradeService["Trade Service"]
  processor["Trade Processor"]
  postgres["PostgreSQL"]
  nats["NATS"]
  trader -->|"trades"| ui
  ui -->|"discovers instruments"| reference
  pricing -->|"pricing.instrumentKey"| nats
  ui -->|"limit order"| matcher
  matcher -->|"stocks and ETFs"| tradeService
  matcher -->|"Treasury sync booking"| processor
  tradeService -->|"/trades"| nats
  nats -->|"async inherited trades"| processor
  processor -->|"Treasury metadata"| reference
  processor -->|"locked transaction"| postgres
  matcher -->|"orders and pending data"| postgres
  processor -->|"trade and position updates"| nats
```

## Node Catalog

| Node | Kind | Label | Notes |
| --- | --- | --- | --- |
| `trader` | actor | Trader | Uses unified tickets and blotters. |
| `ui` | component | Angular UI | Groups stocks, ETFs, and Treasuries. |
| `reference` | service | Reference Data | Serves instrumentKey/displayName and CDM-shaped Debt economics. |
| `pricing` | service | Price Publisher | Publishes simulated clean prices and approximate YTM. |
| `matcher` | service | Order Matcher | Reserves face amount and reconciles pending Treasury executions. |
| `tradeService` | service | Trade Service | Keeps inherited asynchronous stock/ETF submission and early Treasury validation. |
| `processor` | service | Trade Processor | Authoritative idempotent booking with pessimistic position locking. |
| `postgres` | database | PostgreSQL | Stores accounts, orders, pending execution data, trades, and positions. |
| `nats` | broker | NATS | Carries price and account-scoped trade/position/order updates. |

