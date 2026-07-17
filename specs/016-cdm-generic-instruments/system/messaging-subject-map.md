# Messaging Subject Map (State 016)

Parent state: `009-order-management-matcher`

State 016 changes no messaging subjects. It replaces reference-data's `/stocks`
REST resource with `/instruments`, which is not a messaging surface, and it
leaves the instrument key as ticker text, so `pricing.<TICKER>` keeps its shape
and every payload contract below is unchanged from the parent.

This file is reproduced in full rather than referenced: the subject map is a
per-state contract (mandatory from state 006 onward), and states are published
as self-contained snapshots.

## Subject Families

- `/trades`
  - producer: `trade-service`
  - consumer: `trade-processor`
  - delivery: `point-to-point`
  - wildcard: `no`
  - scope: `global`
  - payload: validated trade order with stamped execution price

- `/accounts/<accountId>/trades`
  - producer: `trade-processor`
  - consumer: frontend trade blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `per-account`
  - payload: processed trade (includes `price`)

- `/accounts/<accountId>/positions`
  - producer: `trade-processor`
  - consumer: frontend position blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `per-account`
  - payload: position snapshot (includes `averageCostBasis`)

- `pricing.<TICKER>`
  - producer: `price-publisher`
  - consumer: frontend valuation streams
  - delivery: `broadcast`
  - wildcard: `yes` (`pricing.*`)
  - scope: `per-ticker`
  - payload: market tick (`price`, `openPrice`, `closePrice`, `asOf`, `source`)

- `/accounts/<accountId>/orders`
  - producer: `order-matcher`
  - consumer: frontend account order blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `per-account`
  - payload: order lifecycle event (`orderId`, `status`, `remainingQuantity`, `limitPrice`, `lastExecutionPrice`)

- `/orders`
  - producer: `order-matcher`
  - consumer: frontend admin order blotter stream
  - delivery: `broadcast`
  - wildcard: `no`
  - scope: `global`
  - payload: order lifecycle event (`orderId`, `accountId`, `status`, `remainingQuantity`, `limitPrice`)
