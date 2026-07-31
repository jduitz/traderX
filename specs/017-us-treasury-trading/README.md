# Feature Pack 017: U.S. Treasury Trading

![linux/mac support](https://badgen.net/badge/linux%2Fmac/supported/green?icon=linux) ![windows support](https://badgen.net/badge/windows/not%20verified/grey?icon=windows)

Status: Implemented
Track: `functional`
Previous state: `016-cdm-generic-instruments`

This pack adds five fixed-rate U.S. Treasuries, simulated clean prices,
approximate YTM, long-only face-amount trading, retry-safe synchronous order
booking, and unified multi-asset UI behavior.

Durable behavior is held in:

- `generation/patches/0001-state-overlay.patch`
- `generation/frontend-overrides/`
- `pipeline/generate-state-017-us-treasury-trading.sh`
- `pipeline/render-state-017-us-treasury-trading.sh`
- `scripts/*state-017*`

No `.ps1` lifecycle equivalents are supplied because Windows support remains
unverified for this Compose state.

Generate and verify with `quickstart.md`. Publication metadata is populated but
the generated snapshot branch and tag are not created by this implementation.
