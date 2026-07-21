# Component Diagram

State: `016-cdm-generic-instruments`

```mermaid
flowchart LR
  developer["Developer"]
  openfigi["OpenFIGI (build time)"]
  app_runtime["TraderX App Runtime"]
  obs_runtime["Observability Runtime"]
  ingress["NGINX Ingress"]
  trade_ui["Angular Trade UI"]
  reference_data["Reference Data"]
  instrument_seed["Instrument Seed Data"]
  trade_service["Trade Service"]
  order_matcher["Order Matcher"]
  price_publisher["Price Publisher"]
  nats["NATS Broker"]
  trade_processor["Trade Processor"]
  prometheus["Prometheus"]
  blackbox["Blackbox Exporter"]
  grafana["Grafana"]

  openfigi -->|Bakes FIGI + security type (offline, once)| instrument_seed
  instrument_seed -->|Loaded and mapped onto CDM at startup| reference_data
  developer -->|Uses app and admin UI| ingress
  ingress -->|Serves UI| trade_ui
  ingress -->|Routes /reference-data/instruments| reference_data
  trade_ui -->|Lists instruments for the tickets| reference_data
  trade_service -->|Validates ticker via /instruments/{ticker}| reference_data
  order_matcher -->|Submits fills as trades| trade_service
  trade_service -->|Publishes validated trades| nats
  order_matcher -->|Publishes fills and status| nats
  price_publisher -->|Publishes pricing.<TICKER> ticks| nats
  nats -->|Delivers trades for persistence| trade_processor
  prometheus -->|Scrapes probe metrics| blackbox
  blackbox -->|HTTP probes /instruments| reference_data
  prometheus -->|Scrapes /metrics| order_matcher
  developer -->|Views inherited observability| grafana
  grafana -->|Queries metrics| prometheus
```
