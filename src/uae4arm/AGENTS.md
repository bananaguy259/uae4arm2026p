# src/uae4arm/ — UAE4Arm-Owned Native Layer

## OVERVIEW

Small directory for UAE4Arm-owned native helpers, overrides, and merge-safety shims that should not stay embedded inside upstream-tracked WinUAE/Amiberry source files.

## CONVENTIONS

- Keep files here narrow and explicit: one helper or shim per concern.
- Prefer call-site extraction from upstream-tracked files instead of rewriting large upstream flows.
- If logic is truly platform abstraction, it still belongs in `src/osdep/`.
- If logic is product-owned separation glue, keep it here.

## CURRENT CONTENT

- `upstream_overrides.*` — isolated helpers for UAE4Arm-specific behavior that upstream-tracked files call into.