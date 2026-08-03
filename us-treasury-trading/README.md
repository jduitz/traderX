## TraderX State 017 Runtime

This directory defines the Compose runtime for `017-us-treasury-trading`.

State 017 extends State 016 with five fixed-rate U.S. Treasury instruments,
simulated clean prices and approximate YTM, long-only face-amount trading,
authoritative synchronous Treasury booking, retry-safe pending executions, and
unified stock/ETF/Treasury tickets and blotters.

Primary endpoints:

- TraderX UI: `http://localhost:8080`
- API explorer: `http://localhost:8080/api/docs`
- Instruments: `http://localhost:18085/instruments`
- Price publisher: `http://localhost:18100/prices`
- Order matcher health/metrics: `http://localhost:18110/health`, `http://localhost:18110/metrics`
- Grafana: `http://localhost:8080/grafana/`
- Prometheus: `http://localhost:9090`
- NATS monitor: `http://localhost:8222/varz`

Treasury prices are simulated clean percent-of-par prices. Accrued interest,
dirty settlement value, coupons, and maturity repayment are outside this state.
