# Tasks: 016-cdm-generic-instruments

## Spec pack

- [x] T01601 Define functional deltas in `requirements/functional-delta.md`.
- [x] T01602 Define non-functional deltas in `requirements/nonfunctional-delta.md`.
- [x] T01603 Document CDM scope, OpenFIGI mechanics, and rejected alternatives in `research.md`.
- [x] T01604 Define the instrument model and CDM adoption scope in `data-model.md`, with enum
      literals quoted from CDM source.
- [x] T01605 Author run instructions in `quickstart.md`.
- [x] T01606 Declare the `/stocks` -> `/instruments` replacement in `contracts/contract-delta.md`,
      and publish this state's contract at `contracts/reference-data/openapi.yaml`.
- [x] T01607 Update `system/architecture.model.json` and regenerate architecture docs.
- [x] T01608 Add `system/messaging-subject-map.md` (mandatory from state `006`; the scaffolder does
      not emit it).

## Seed data

- [x] T01609 Fetch FIGIs and security types from OpenFIGI offline, batched and paced under the
      keyless limits. Recover delisted constituents with `includeUnlistedEquities`.
- [x] T01610 Bake identifiers into `data/instruments.csv` with FIGI and SecurityType columns, plus
      five ETF rows. Retain the CIK column.
- [x] T01611 Bake identifiers into the supplemental seed list for `UBS`, `DB`, `FNMA`, `FNF` — they
      are not CSV rows, so a column alone never reaches them.

## Code (in the generated tree, captured as a patch)

- [x] T01612 Rename `reference-data` `src/stocks/` -> `src/instruments/`, including exported symbols,
      `@Controller('instruments')`, and `app.module.ts`.
- [x] T01613 Map OpenFIGI security types onto CDM in `load-csv-data.ts` and enforce the sub-type
      conditions at load. Preserve the `FB` -> `META` rewrite, preferred company names, ticker dedup,
      and the supported/max ticker filters.
- [x] T01614 Repoint `trade-service` `validateTicker()` at `/instruments/{ticker}`.
- [x] T01615 Add the `cdm-generic-instruments/` compose runtime: project `traderx-state-016`,
      `/instruments` blackbox probe, five ETFs in both ticker environment variables.
- [x] T01616 Add ETF entries to `price-publisher/data/snapshot-prices.json` for the inherited
      alignment contract.
- [x] T01617 Add frontend overrides for `symbols.service.ts` and `state-metadata.service.ts`, based
      on the generated post-`009` tree rather than `templates/web-front-end`.

## Pipeline registration

- [x] T01618 Implement the generation hook and render script.
- [x] T01619 Register `016` in the runtime harness, CI assets, UI status metadata, API explorer
      contract resolution, lineage allowlist, and lifecycle script contract.
- [x] T01620 Implement lifecycle scripts probing `/instruments`, leaving `009`'s scripts untouched.
- [x] T01621 Implement smoke tests: `scripts/test-state-016-cdm-generic-instruments.sh`.
- [x] T01622 Capture the patchset, then regenerate from scratch and verify.
- [ ] T01623 Flip the catalog entry `planned` -> `implemented` and publish the snapshot branch.

## Follow-ups

- [x] T01624 (TD-01601) Generalize state `009`'s smoke harness so `016` can chain it, per the
      `014 -> 012 -> 011` convention. This first required removing the dead `scripts/**` deletion
      hunks from state `004`'s patchset (done, fork PR #1, merge `c3a7a04`), which had frozen the
      byte content of every repo script. See `tests/smoke/README.md`.
- [ ] T01625 (TD-01402) Carry CDM symbology to the C3/FDC3 line, which this state does not close.
- [ ] T01626 (TD-01602) Rename `companyName` when the surrogate-ID migration already touches its 14
      consumers.
- [ ] T01627 (TD-01603) Rename the Angular `Stock` model and `SymbolService.getStocks()`.
