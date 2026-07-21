# Reference-Data (Spec-First Generated)

This component is synthesized from the TraderSpec Spec Kit manifest for the baseline pre-containerized runtime.

State `016-cdm-generic-instruments` replaces `/stocks` with `/instruments`, serving an instrument model
shaped after the FINOS Common Domain Model (CDM). The removed paths are not aliased.

## Run

```bash
npm install
npm run start
```

## Runtime Contract

- Default port: `18085` via `REFERENCE_DATA_SERVICE_PORT`
- CORS allowlist: `CORS_ALLOWED_ORIGINS` (default `*`)
- Dataset: `data/instruments.csv` (S&P 500 constituents plus exchange-traded funds)
- Endpoints: `GET /instruments`, `GET /instruments/{ticker}`
- Supported ticker filter: `REFERENCE_DATA_SUPPORTED_TICKERS`, capped by `REFERENCE_DATA_MAX_TICKERS`

## Instrument Identifiers

Identifiers are baked into the seed file offline; this service never calls an external symbology
provider at runtime. Every instrument carries a CDM `BBGTICKER` identifier, and every instrument in
the supported ticker set also carries `FIGI`. Seed rows that no longer resolve against OpenFIGI
(delisted constituents, tickers reissued to a different security) are served with `BBGTICKER` only
and log a warning at load time.
