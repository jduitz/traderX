# Component List

State: `016-cdm-generic-instruments`

| ID | Label | Kind | Description |
| --- | --- | --- | --- |
| `developer` | Developer | actor | Local developer using this state. |
| `openfigi` | OpenFIGI (build time) | actor | Symbology source consulted once, offline, to bake FIGI identifiers into seed data. Never called at runtime. |
| `app_runtime` | TraderX App Runtime | boundary | State 009 order-management runtime, carried forward with a CDM-shaped instrument model. |
| `obs_runtime` | Observability Runtime | boundary | LGTM + OTel stack inherited from state 007 unchanged. |
| `ingress` | NGINX Ingress | service | Routes UI, API, and order admin traffic. Unchanged. |
| `trade_ui` | Angular Trade UI | service | Trade ticket, blotters, and admin tab. Fetches the instrument list from /instruments. |
| `reference_data` | Reference Data | service | Serves /instruments and `/instruments/{ticker}`. CDM Security-shaped records with BBGTICKER and FIGI identifiers. /stocks is removed, not aliased. |
| `instrument_seed` | Instrument Seed Data | service | data/instruments.csv plus the supplemental seed list. Identifiers and security types baked in offline; equities and ETFs. |
| `trade_service` | Trade Service | service | Validates a trade's security against `/instruments/{ticker}` before publishing it. |
| `order_matcher` | Order Matcher | service | Matches open orders and emits lifecycle and fill events. Unchanged; now exercised with ETFs. |
| `price_publisher` | Price Publisher | service | Publishes `pricing.<TICKER>` ticks. Supported universe extended with five ETFs to keep the inherited alignment contract. |
| `nats` | NATS Broker | service | Realtime transport for pricing, trade, position, and order subjects. No subject changes. |
| `trade_processor` | Trade Processor | service | Consumes fills and persists trades/positions. Unchanged. |
| `prometheus` | Prometheus | service | Scrapes order metrics and blackbox probes. |
| `blackbox` | Blackbox Exporter | service | Probes runtime endpoints. Its reference-data target moves to /instruments. |
| `grafana` | Grafana | service | Inherited dashboards, re-scoped to the traderx-state-016 compose project. |
