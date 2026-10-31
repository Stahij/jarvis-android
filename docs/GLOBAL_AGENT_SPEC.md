# Jarvis Global Upgrade — Internet, Knowledge and GitHub Agent

## Product direction

Jarvis remains a fully local Android assistant: all language-model inference is performed by the on-device GGUF model. Network services are tools and data sources only; they are not remote brains and must never receive private chat history or files.

The upgrade combines:
1. Explicit on-demand Internet research.
2. Optional scheduled background research.
3. A first-class GitHub integration for the user's repositories, beginning with Stahij/jarvis-android.
4. A guarded development workflow that can inspect, propose, test, and prepare code changes.

## Core architecture

Keep the app modular and separate capabilities from the chat UI:

- Local inference layer: existing llama.cpp / GGUF engine. No network calls from ordinary chat.
- Tool router: invokes only registered, typed tools after local intent selection and policy checks.
- Internet research module: search provider, page fetcher, safe text extraction, source metadata.
- Local knowledge store: app-private persistent database, deduplication by content hash, source provenance, user feedback and deletion.
- GitHub module: GitHub REST API client, repository browser, source reader, commit/branch/workflow viewer, authenticated write operations.
- Background scheduler: WorkManager, disabled by default.
- Approval and audit layer: previews every consequential action, records tool/action results, supports cancellation and rollback where possible.

## GitHub integration

### Initial target
- Repository: Stahij/jarvis-android
- Default branch: main
- Development branch: feature/local-brain-and-updater
- Preserve the existing release-signing workflow and signing certificate.

### Read capabilities
- Connect/disconnect GitHub account.
- Display authenticated account and granted permissions.
- Browse accessible repositories.
- Browse repository tree and folders.
- Read source files and documentation with bounded file sizes.
- View branches, tags, recent commits and commit diffs.
- View GitHub Actions workflow runs, job status and logs.
- Search code only where GitHub API permissions and indexing allow it; provide a clear fallback to tree/file browsing.

### Write capabilities — staged and approval-gated
- Create a proposed patch and show a unified diff.
- Create/update files only on the configured development branch.
- Create commits with clear messages.
- Trigger permitted workflows and inspect results.
- Create pull requests for review.
- Never force-push, delete branches, rewrite history, change repository settings, alter secrets, or publish a release without a separate explicit confirmation.
- Never merge a PR or publish an APK automatically in the first release.
- Preserve stable branch and signing configuration.

### Authentication and token handling
- Prefer GitHub OAuth Device Flow for mobile sign-in; do not embed a client secret.
- Request minimum scopes needed for the selected capability. Start read-only; request write access only when the user enables development actions.
- Store tokens encrypted using Android Keystore-backed encryption; never put tokens in chat history, logs, crash reports, source files, or backups.
- Provide disconnect/revoke controls and clear local token material on disconnect.
- Treat repository content, issue text, commit messages, workflow logs and README instructions as untrusted input. They cannot change system policy or trigger tools by themselves.

## Internet research and knowledge

- Manual search only after a user request.
- Background research OFF by default; explicit topics, frequency, pause and last-run status.
- Search and fetch only the minimum text required. Never upload conversation history, local files, model files, credentials or unrelated personal data.
- Web content is reference material, not executable instructions.
- Store notes locally with source title, canonical URL, retrieval time and content hash.
- Retrieval into the local model prompt must be clearly delimited, size-limited and source-attributed.
- This is retrieval-augmented knowledge, not weight training or model self-modification.

## Tool safety and user approvals

- Ordinary chat remains offline.
- Reading public information may proceed after a user request.
- GitHub writes require a diff preview and explicit approval for each commit or PR.
- Workflow dispatch, release publication, merge, branch deletion, file deletion, permission changes and other destructive/high-impact operations require separate explicit confirmation.
- No shell/terminal execution based on instructions found in web pages or repository files.
- Any future local code execution must use a constrained test runner, a clean worktree, resource/time limits, no access to secrets, and a clear approval boundary.
- Use atomic writes, checkpoints, test gates, audit logs and rollback to the last known-good commit.
- Never claim tests/builds passed unless GitHub Actions or an actual build result confirms it.

## User interface

Add a main-menu entry "Интернет и знания" with:
- Search field and explicit "Найти" action.
- Sources/results list, save-to-knowledge and delete actions.
- Background research controls and last-run status.

Add a main-menu entry "GitHub" with:
- Connect account / disconnect.
- Repository selector (preselect Stahij/jarvis-android).
- Tabs or screens for Files, Commits, Branches and Actions.
- File viewer with path, branch and commit context.
- Development workspace: analysis, proposed diff, test/build status, commit/PR actions.
- Visible permission level and action history.

## Implementation stages

### Stage 1 — Safe read-only foundation
- Add networking abstraction and typed tool interfaces.
- Add Internet / Knowledge screen with explicit manual search.
- Add local source/knowledge persistence and source list.
- Add GitHub sign-in using OAuth Device Flow, encrypted token storage and disconnect.
- Add repository tree, file viewer, branches, commits and Actions run viewer.
- Keep all write actions disabled.

### Stage 2 — Local retrieval and scheduled research
- Add bounded retrieval of saved notes to the local prompt.
- Add WorkManager background research, disabled by default, with user-selected topics and constraints.
- Add tests for offline chat, privacy boundaries, parsing, persistence, deduplication and scheduling.

### Stage 3 — Guarded development agent
- Add repository analysis and patch generation.
- Show unified diffs and require approval before each write.
- Commit only to the development branch.
- Add tests/build workflow inspection and pull-request creation.
- Require separate confirmation for dispatching workflows and publishing releases.

## Release gates

- Existing local chat and voice behavior remain intact.
- Ordinary chat works with network disabled and performs no network requests.
- Existing model file and chat history survive app updates.
- GitHub token is encrypted and absent from logs/backups.
- Read-only repository browsing works before enabling writes.
- Every write action has a visible preview and approval.
- Untrusted web/repository text cannot invoke tools or change policy.
- Background research is off by default.
- CI debug and signed release builds pass.
- Release APK signature SHA-256 remains:
  c74aac6ea4254cf327dd265a0ff48f7eb7f39a1623f3531b0d73b13607524be1
