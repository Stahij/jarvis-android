# Claude audit archive — November 2026

This directory preserves the original review package from branch `review/claude-audit` before branch cleanup.

## Preserved documents

- `CODE_REVIEW_2026-11.md` — findings R-00 through R-20, including severity, current-at-review status, and known good safeguards.
- `ROADMAP_OPTIMIZATION.md` — implementation stages, completed claims, remaining work, and proposed order.
- `TESTING.md` — 26 device/security checks plus performance measurements.
- `ARCHITECTURE.md` — intended architecture and invariants for streaming, JNI, cancellation, and cache behavior.
- `EXPLANATION.md` — stage 4 proposal and explicit caveats about code not compiled or device-tested.
- `README_PATCH.md` — stage 3 patch manifest and limitations.

## Important interpretation

These are historical review artifacts, not proof that every listed change exists in the current release branch. The review itself says parts of the proposed work were not compiled or tested on-device. Treat every status marker as the author's assessment at the time of review, then verify against current source and CI before implementation.

The duplicate files with suffix `(2)` were not copied separately because they duplicate/extend the same review package; the unique stage-4 checklist is preserved in `TESTING.md`.

Source branch: `review/claude-audit`
Source commit: `eb9239c347a01b7e675501e8813406d67bb89d3a`
