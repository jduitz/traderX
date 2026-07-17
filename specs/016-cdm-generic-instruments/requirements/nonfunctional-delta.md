# Non-Functional Delta: 016-cdm-generic-instruments

Parent state: `009-order-management-matcher`

## Standards Alignment

- NFR-01601: The adopted CDM subset and the skipped layers are documented in `../data-model.md`, with
  enum literals quoted from CDM source rather than paraphrased. Paraphrasing is how a standard stops
  being a standard: there is no `TICKER` member in `AssetIdTypeEnum`, and a paraphrase would have
  invented one.
- NFR-01602: CDM's `Asset -> Instrument -> Security` choice tree is taxonomy documentation. The
  runtime model stays flat; no discriminated union is built until an asset class needs one.
- NFR-01604: `ISIN` and `CUSIP` remain valid `identifierType` values but stay unpopulated, because
  those symbologies are licensed. `FIGI` is the second identifier because it is openly licensed and
  free to redistribute in seed data.

## Runtime / Operations

- NFR-01603: CDM sub-type conditions are enforced at seed load time, not documented and left to
  drift. A disagreement between `securityType` and the populated sub-type throws at load.
- NFR-01605: Observability, messaging, and runtime topology inherited from `007`–`009` are intact.
  This state changes no messaging subjects.
- NFR-01606: Runtime and topology constraints are captured in `../system/runtime-topology.md`.
- NFR-01607: Architecture updates are encoded in `../system/architecture.model.json`.
- Compose project is `traderx-state-016` and the runtime directory is `cdm-generic-instruments/`.
  Grafana admin credentials and dashboard log filters are state-scoped accordingly, so this state's
  runtime does not collide with a `009` runtime on the same host.

## Security / Compliance

- NFR-01611: No symbology provider is called at runtime. Identifiers are baked into seed data offline,
  so the runtime has no outbound dependency on Bloomberg or OpenFIGI and no credential to hold.
- Seed identifiers are limited to openly licensed symbologies. No licensed reference data is
  redistributed in this repository.

## Performance / Scalability

- Unchanged. The instrument payload grows from two fields to roughly seven per record, over a
  supported set of 25 instruments, loaded once at service start and served from memory.

## Reliability / Observability

- NFR-01608: Generated state branches keep the inherited `C2` build/publish workflow and GHCR
  run-bundle assets.
- NFR-01609: The generated API explorer documents this state's reference-data contract rather than
  the state `001` baseline contract, so the explorer does not advertise a removed resource.
- The Prometheus blackbox probe targets `/instruments`. This is the check most likely to fail
  quietly: a stale probe target surfaces as a container-health timeout rather than a clean error.
- Unresolvable and misclassified seed rows log warnings at load rather than failing startup, so a
  seed data gap never presents as a container that will not boot.
