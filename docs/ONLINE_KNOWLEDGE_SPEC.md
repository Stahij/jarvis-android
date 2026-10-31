# Jarvis 0.5.0 — Online research and local knowledge

## Goal

Add two opt-in ways to expand Jarvis' useful knowledge while keeping inference local:
1. On-demand web research initiated by the user.
2. Scheduled background research for user-selected topics.

The local GGUF model remains the only language model used for ordinary inference. Network access retrieves source material; it does not send private chat history, files, or prompts to an external AI service.

## User experience

- Add an Internet / Knowledge section to the main menu.
- On-demand research accepts a topic or question, searches public sources, reads a small number of relevant pages, and presents a concise answer with source title, URL, and retrieval date.
- Save useful findings to a private local knowledge store. Allow viewing and deleting individual saved entries.
- Background research is OFF by default. The user must explicitly enable it, select topics, and choose a frequency. Provide pause/disable controls and a last-run status.
- Respect Android battery/network constraints. Background work should use WorkManager constraints (network connected; optionally charging / unmetered) and never run continuously.

## Trust and privacy boundaries

- Web pages are untrusted data, never executable instructions.
- Ignore page content that asks Jarvis to reveal secrets, change its rules, run commands, install software, or access unrelated local data.
- Do not execute code, shell commands, links, downloads, or app actions found in source material.
- Never upload chat history, local files, model files, credentials, or personal data to search providers.
- Send only the minimum user-authored search query needed for the requested research.
- Keep retrieved text and saved notes local. Show source URLs and retrieval timestamps.
- Do not silently change system prompts, model files, permissions, or app code as a result of research.
- Background research can use only topics explicitly configured by the user; provide a visible pause/disable setting.

## Knowledge records

Each saved record should include:
- stable local ID
- topic / query
- concise extracted note
- source title and canonical URL
- retrieved-at timestamp
- optional user feedback (useful / incorrect)
- content hash for duplicate detection

The store must be app-private and survive app updates. Writes should be atomic. Provide per-entry deletion and a clear-all action with confirmation.

## Retrieval

For a user question, retrieve only a small set of relevant local notes and pass them to the local model as clearly marked reference material. Keep source metadata available for citations in the answer. If no relevant note exists and the user did not authorize online lookup, answer from the model's existing knowledge and be transparent about freshness.

## Initial implementation sequence

1. Add a dedicated online research screen and explicit on-demand search.
2. Implement a narrow, reliable public-source search provider and safe page extraction; handle HTTP errors, timeouts, redirects, malformed pages, and offline mode.
3. Add local knowledge persistence, source display, duplicate detection, and deletion.
4. Add retrieval of relevant notes into the local model prompt, with strict size limits.
5. Add opt-in scheduled research with WorkManager, user-selected topics, network/battery constraints, and visible controls.
6. Add tests for parsing, persistence, duplicate detection, prompt isolation, and background scheduling.
7. Increment app version only after implementation; build and verify release APK signature against the established signing certificate before sharing.

## Release acceptance criteria

- Ordinary chat still works offline and does not make network requests.
- On-demand research works only after explicit user action and shows sources.
- No private conversation or file contents are transmitted.
- Saved knowledge remains after app restart and APK update.
- Background research is disabled by default and can be paused or disabled.
- Untrusted page instructions cannot trigger device actions or modify app policy.
- Existing GGUF model file is preserved.
- Release APK installs as an update using the existing signing certificate.
