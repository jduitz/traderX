# Generation Hook

`pipeline/generate-state-017-us-treasury-trading.sh`:

1. generates `016-cdm-generic-instruments`;
2. applies ordered patches from `generation/patches/`;
3. renders State 017's Compose normalization and frontend overrides;
4. regenerates architecture documentation;
5. returns to the canonical generator for runtime, API, CI, and metadata
   installers.

Frontend overrides are based on the fully generated State 016 tree, preserving
its inherited State 008/009/016 behavior. Shared templates are not modified.

The future publication coordinates are:

- branch `code/generated-state-017-us-treasury-trading`
- tag `generated/017-us-treasury-trading/v1`

This feature implementation does not perform publication.
