# Repository consolidation plan — 2026-11-06

## Active development lanes

The repository should be operated with one canonical app line, one release line, and short-lived feature work:

1. `main` — canonical/default branch. **Do not delete.** At this audit it is still the legacy 0.1.1 line with a different package identity from the 1.5.x preview line. It must be reconciled deliberately after package/signing migration is resolved; do not force-reset it to 1.5.5.
2. `release/v1.5.5` — current 1.5.5 release source line. It now also contains the historical audit archive at `docs/archive/claude-audit-2026-11/`.
3. `feature/model-transfer-export` — the only current feature branch to retain for the GGUF export task. Draft PR #8 targets `release/v1.5.5`. CI run 42257354941 passed both debug and release jobs.

Once PR #8 is reviewed and merged, this feature branch can be deleted. Do not publish its APK as an in-app update until package ID, installed certificate, version and installed-app compatibility are checked.

## Historical archives (not active app lines)

- `archive/claude-audit-2026-11` — exact snapshot of the Claude audit branch, including its original documents, source snapshots and binary stage ZIP files. This is a preservation branch, not a build/release branch.
- `archive/jarvis-lite-2026-11` — exact snapshot of the Lite branch. Lite is retired as an installable product, but its source is preserved here in case a component is later worth recovering.

The full Claude review package and selected unique code snapshots were also copied into `release/v1.5.5/docs/archive/claude-audit-2026-11/`. The archive contains the original review, roadmap, test checklist, architecture notes, stage-4 explanation, patch manifest, and selected historical Kotlin/C++/manifest sources.

## Branch-by-branch disposition

| Branch | Finding | Safe disposition |
|---|---|---|
| `feature/deepseek-team-bridge` | Only adds a Termux DeepSeek console, installer, tests, docs and CI on top of the old 1.5.3 line. No app runtime dependency. The experiment is obsolete; API balance was unavailable during the user's test. | Delete after confirming there is no desire to resume that separate experiment. |
| `feature/jarvis-1.5.1-model-load-guard` | The guarded `NativeLlm.kt` content is byte-identical to the 1.5.5 release copy; branch is an old divergent snapshot. | Safe to delete. |
| `feature/jarvis-1.5.2-prefill` | Contains historical ARM/prompt-prefill C++ and Kotlin changes. Unique source snapshots were copied to the Claude audit archive directory. | Safe to delete after archive commit is visible. |
| `feature/jarvis-1.5.3-output-speed-updater` | Ancestor/behind the 1.5.5 release line; no unique diff against release. | Safe to delete. |
| `feature/jarvis-1.5.4-qwen-3b` | Contains Qwen model integrity improvements not present in current 1.5.5 source: expected file size 2,104,932,768 bytes and SHA-256 `626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d`. Full historical MainActivity snapshot was archived. | Safe to delete only after confirming the archived snapshot is present. These checks should be reviewed before the next model-download change. |
| `feature/jarvis-1.5.5-prefill-updater` | Identical commit to `release/v1.5.5`. | Safe to delete. |
| `feature/local-brain-and-updater` | Behind the release line; no unique diff. | Safe to delete. |
| `feature/qwen-2.5-3b-instruct` | Behind the release line; no unique diff. | Safe to delete. |
| `feature/model-transfer-export` | Contains the GGUF export work. | Keep until PR #8 is merged/rejected. |
| `field-test/jarvis-lite` | Separate application ID and test-only product. Exact branch snapshot preserved as `archive/jarvis-lite-2026-11`. | Delete original after checking archive branch exists. |
| `field-test/phase-1-hardening` | Contains duplicated Claude-stage docs/source drop files; same historical material is preserved in the Claude archive. | Safe to delete after archive branch check. |
| `preview/0.1.1-safe-install` | Old preview snapshot, behind current release line. | Safe to delete. |
| `release/v1.5.5` | Current 1.5.5 release line, now with audit archive. | Keep. |
| `release-signing-bootstrap` | Old signing setup snapshot, behind current release line. | Safe to delete; keep the signing documentation and secrets in repository settings. |
| `review/claude-audit` | Full audit branch retained as `archive/claude-audit-2026-11`; key documents/source snapshots also copied into release docs. | Delete original after checking archive branch and archive directory. |
| `main` | Default branch; old legacy app identity. | Keep. Do not reset or delete during this cleanup. |

## Important items not to lose

- Existing signing secrets are repository settings, not branch files. Never delete or rotate them as part of branch cleanup.
- Signing certificate fingerprint in historical specs is not consistent with the fingerprint in `docs/RELEASE_SIGNING_SETUP.md`. Resolve this against the installed APK and CI output before promising in-place updates.
- The 1.5.5 release source uses `applicationId = com.stahij.jarvis.preview`; the legacy main branch has a different identity. This is a major reason not to merge/reset branches blindly.
- Current 1.5.5 model download uses the Bartowski URL and does not contain the exact expected-size/SHA-256 guard from the 1.5.4 branch. That improvement is archived but not yet ported.
- Claude's audit marked several items as uncompiled or untested on-device. Its status symbols are historical review claims, not current proof.

## Verification status

- Model-export feature branch CI run 42257354941: debug and signed release jobs both succeeded.
- Draft PR #8 is open and not merged.
- No original branches have been deleted by this audit.
