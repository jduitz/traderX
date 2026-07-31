# Smoke Test

`scripts/test-state-017-us-treasury-trading.sh` first creates the clean-database
IBM lingering order required by State 009, then chains the complete State 016
smoke (which itself chains State 009). State 017 assertions cover:

- the five Debt instruments, FIGIs, absence of committed CUSIP/ISIN, and clean
  quotes;
- legacy Compose universe names and alignment;
- account/users/seed trades/positions;
- face-increment and derived-reservation validation;
- two-stage synchronous Treasury fill with exact stable trade IDs;
- reconciliation metrics and unified Treasury UI source.

Run against an already-started State 017 stack:

```bash
./scripts/test-state-017-us-treasury-trading.sh
```

`--skip-messaging` skips only the inherited message-bus browser diagnostic.
