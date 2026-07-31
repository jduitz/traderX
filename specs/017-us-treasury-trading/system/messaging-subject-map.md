# Messaging Subject Map (State 017)

Parent state: `016-cdm-generic-instruments`

State 017 keeps the inherited NATS topology while widening instrument keys and
payloads for Treasury trading. Treasury order acknowledgement is internal
synchronous REST (`order matcher -> POST /tradeservice/order -> trade
processor`), so no new booking-result subject is introduced. Treasury `UST-*`
keys contain no period and remain one NATS subject token.

## Subject Families

- `pricing.<instrumentKey>`
  - producer: `price-publisher`
  - consumer: frontend valuation streams and `order-matcher`
  - delivery: `broadcast`
  - wildcard: `yes` (`pricing.*`)
  - scope: `per-instrument`
  - payload: market tick with one `asOf` instant, clean `price`, `priceType`, approximate `yieldToMaturityPercent`, and maturity status for Treasuries

- `/trades`
  - producer: `trade-service`
  - consumer: `trade-processor`
  - delivery: `point-to-point`
  - wildcard: `no`
  - scope: `global`
  - payload: inherited asynchronous stock/ETF trade order only; Treasury orders use synchronous REST

- `/accounts/<accountId>/trades`
  - producer: `trade-processor`
  - consumer: frontend trade blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `per-account`
  - payload: settled or rejected trade, including rejection reason and source order ID when applicable

- `/accounts/<accountId>/positions`
  - producer: `trade-processor`
  - consumer: frontend position blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `per-account`
  - payload: settled position update with average clean cost; rejection emits no position update

- `/accounts/<accountId>/orders`
  - producer: `order-matcher`
  - consumer: frontend account order blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `per-account`
  - payload: order lifecycle with remaining quantity, pending trade ID, pending fill quantity, and pending execution price

- `/orders`
  - producer: `order-matcher`
  - consumer: frontend admin order blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `global`
  - payload: all-account order lifecycle with the same reconciliation state
