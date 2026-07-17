# Feature Pack 016: CDM Generic Instruments

![linux/mac support](https://badgen.net/badge/linux%2Fmac/supported/green?icon=linux) ![windows support](https://badgen.net/badge/windows/not%20supported/red?icon=windows)

Status: Implemented
Track: `functional`
Previous state: `009-order-management-matcher`

TraderX had no instrument model. The whole concept was `Security { ticker, companyName }` — two
strings — and everywhere else an instrument travelled as a bare `String security`. That blocks any
asset class that is not a US common stock.

This state replaces it with an instrument model shaped after the **FINOS Common Domain Model (CDM)**,
and proves it with stocks and ETFs.

Primary intent:

- adopt CDM's asset identifier and taxonomy layer, so TraderX names securities the way the industry
  already does instead of inventing a local scheme,
- give every instrument multiple identifiers (`BBGTICKER` + `FIGI`) rather than one ticker string,
- carry a real second `securityType` at runtime by seeding ETFs alongside equities,
- replace `/stocks` with `/instruments` as a declared, non-aliased break,
- keep generation fully spec-first and publish a reproducible generated snapshot branch.

What it deliberately does **not** do: generalize pricing, quantities, or position keys; introduce
surrogate instrument IDs; or touch the UI beyond what the endpoint rename forces. The contribution
here is the model and the standards alignment, not new asset classes. See "Explicitly out of scope"
in `data-model.md`.

Two decisions worth knowing before reading further:

- **Parent is `009`, not the `014` functional tip.** Reaching `014` needs a Kubernetes cluster, and a
  state that cannot be run cannot be verified. This state therefore does not close TD-01402 on the
  C3/FDC3 line. See "Lineage tradeoff" in `spec.md`.
- **The `Asset -> Instrument -> Security` tree in `data-model.md` is documentation.** The runtime
  model is flat. Do not build a discriminated union from it.

Core artifacts:

- `spec.md`
- `requirements/functional-delta.md`
- `requirements/nonfunctional-delta.md`
- `research.md` — CDM scope, OpenFIGI mechanics, rejected alternatives
- `data-model.md` — the adopted CDM subset, with enum literals quoted from source
- `quickstart.md`
- `contracts/contract-delta.md` — the declared `/stocks` -> `/instruments` replacement
- `contracts/reference-data/openapi.yaml` — this state's reference-data contract
- `system/architecture.model.json`
- `system/runtime-topology.md`
- `system/messaging-subject-map.md`
- `generation/generation-hook.md`
- `tests/smoke/README.md`
