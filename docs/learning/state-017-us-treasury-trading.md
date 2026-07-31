---
title: "State 017: U.S. Treasury Trading"
---

# State 017 Learning Guide

## Position In Learning Graph

- Previous state(s): [016-cdm-generic-instruments](/docs/learning/state-016-cdm-generic-instruments)
- Dotted-line parent(s): none
- Next state(s): none

## Convergence Metadata

- Convergence state: `no`
- Convergence level: `none`
- Lineage role: `canonical`
- Nearest previous convergence: `none`
- Nearest next convergence: `none`

## Rendered Code

- Generated branch: [code/generated-state-017-us-treasury-trading](https://github.com/finos/traderX/tree/code/generated-state-017-us-treasury-trading)
- Authoring branch (spec source): [main](https://github.com/finos/traderX/tree/main)

## Code Comparison With Previous State

- Compare against `016-cdm-generic-instruments`: [code/generated-state-016-cdm-generic-instruments...code/generated-state-017-us-treasury-trading](https://github.com/finos/traderX/compare/code%2Fgenerated-state-016-cdm-generic-instruments...code%2Fgenerated-state-017-us-treasury-trading)

## Plain-English Code Delta

- **Functional intent:** Preserve `security` as the transactional/database key; add.
- **Functional intent:** Replace public `companyName` with `displayName`.
- **Functional intent:** Serve the five approved Treasury records and their documented.
- **Functional intent:** Keep the literal historical configuration names.

## Run This State

```bash
./scripts/start-state-017-us-treasury-trading-generated.sh
```

## Canonical Spec Links

- State spec pack: [/specs/us-treasury-trading](/specs/us-treasury-trading)
- Architecture: [/specs/us-treasury-trading/system/architecture](/specs/us-treasury-trading/system/architecture)
- Flows / topology: [/specs/us-treasury-trading/system/runtime-topology](/specs/us-treasury-trading/system/runtime-topology)
- Research: [link](/specs/us-treasury-trading/research)
- Data model: [link](/specs/us-treasury-trading/data-model)
- Quickstart: [link](/specs/us-treasury-trading/quickstart)

