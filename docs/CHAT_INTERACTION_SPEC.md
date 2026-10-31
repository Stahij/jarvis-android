# Jarvis Chat Interaction Upgrade

Status: product requirements for the next chat experience; not a claim that these features are already implemented.

## Product principles

- Keep GGUF inference on-device. Ordinary chat must continue to work offline.
- Keep chat history, personal memory, files and retrieval index in app-private storage by default.
- Network access is a separately invoked capability. Never upload private chat history or attachments unless the user explicitly chooses a feature that requires it and confirms the transfer.
- Preserve existing model files, chat history, floating energy-core widget, voice input and APK signing certificate across updates.
- Build incrementally. Do not ship non-working controls or imply unsupported media/model capabilities.
- Use checkpoints, atomic storage updates, tests and rollback. Never change critical security controls automatically.

## Chat interaction features

### 1. Voice: dictation, playback and hands-free
- Retain Android speech recognition for voice input.
- Add playback controls for assistant messages, stop/pause and configurable auto-read.
- Add a push-to-talk / radio-style interaction mode; hands-free continuous listening is opt-in, visibly indicated and has a clear stop control.
- Use Android TTS initially. Voice quality and available voices depend on the installed TTS engine/language packs; do not promise human-level prosody.
- Keep microphone capture off until the user activates it.

### 2. Attachments and multimodal input
- Add an attachment picker for images, PDFs and common text/document formats.
- Extract text locally where practical; show the extracted preview and report unsupported/oversized files clearly.
- Images: add image understanding only when a compatible local vision model is installed; the current text-only Qwen GGUF must not pretend to see an image.
- Audio: support local transcription where an on-device recognizer is available; keep original audio local.
- Video: initially accept a video and extract selected frames plus audio transcript where supported; show which portions were actually analyzed. Do not imply full-video understanding.
- Add file-size, duration and processing limits to protect device memory and battery.

### 3. Persona / role presets
- Add selectable prompt profiles: General assistant, Translator, Programmer, Friendly companion, Writing assistant and Reflective support.
- Profiles modify the system prompt only; they do not change model permissions or safety boundaries.
- Allow custom profile creation, rename, edit and reset.
- “Psychologist” should be presented as supportive reflection, not diagnosis or professional treatment. “Flirt” remains respectful and must not bypass safety rules.

### 4. Personal memory
- Add a user-visible memory manager with saved facts, source conversation, edit and delete controls.
- Ask before saving sensitive or uncertain facts; allow memory to be disabled.
- Inject only relevant memory into prompts, with a clear size limit.
- Provide “remember this”, “do not remember this” and “forget this fact” controls.
- Memory remains local and is not silently included in network requests.

### 5. Conversation branching and editing
- Allow editing a previous user message and regenerating from that point as a new branch.
- Preserve the original branch; never silently overwrite later messages.
- Add branch navigation, rename and delete with confirmation.
- Store parent message / branch identifiers so history can be reconstructed.

### 6. Document analysis
- Support local text extraction from PDF, TXT, Markdown and common office formats where practical (DOCX/XLSX first).
- For large documents, process in chunks and retrieve relevant sections rather than placing the whole document into the prompt.
- Offer summary, question answering, translation and consistency/error review.
- Preserve page/sheet/section references in answers where extraction provides them.
- Clearly report scanned PDFs or unsupported formats that need OCR or conversion.

### 7. Local code and data analysis
- Add a constrained local Python execution environment only after a secure runner is available.
- No unrestricted shell, network, shared-storage or secret access from executed code.
- Run in an isolated temporary workspace with CPU, memory, time and file-size limits.
- Show code and intended file operations; require approval before writing outside the temporary workspace.
- Return text, tables and generated charts/files to the chat.
- If a safe sandbox is not available, provide code without claiming it was executed.

### 8. Live Internet research
- Keep explicit search as a tool invoked by the user or clearly requested in chat.
- Show source title, URL, retrieval time and citations.
- Distinguish sourced facts from model inference; report when search fails.
- Background research remains opt-in, configurable and easy to pause.
- Never send private conversation history or local attachments as search context by default.

### 9. External integrations / plugins
- Add an extensible, permissioned integration layer rather than unrestricted plugin execution.
- Each integration declares required permissions and data sent.
- Ask for confirmation before bookings, purchases, messages, payments or other consequential actions.
- Keep integrations optional; core chat remains usable without them.

### 10. Prompt enhancement
- Add an optional “Improve prompt” action that proposes a clearer, more detailed version.
- Show the proposed prompt before sending; never silently rewrite the user's intent.
- Preserve original text and allow one-tap restore.

### 11. Chat organization
- Add folders/projects, tags, search, archive and bulk selection.
- Keep archive separate from delete; confirm destructive deletion.
- Support sorting by recent activity and date.
- Do not impose arbitrary low chat-count limits.

### 12. Sharing and export
- Add copy-to-clipboard and local export to TXT, PDF and DOCX; add XLSX export only where tabular data is present.
- Export selected conversation or branch, with optional attachments.
- Public share links require an explicit opt-in upload, a clear warning that the content leaves the device, and revoke/expiry controls. No public link is created by default.
- Google Docs export is a later optional integration requiring user authentication and explicit upload approval.

### 13. On-device retrieval (RAG)
- Build a local index over opted-in conversation history, saved knowledge and imported documents.
- Start with SQLite full-text search and metadata filters; add a compact embedding/vector index when a suitable on-device embedding model is available.
- Retrieve only a small set of relevant passages, include source/date/branch metadata and enforce context limits.
- Exclude deleted, archived-if-user-excludes, private or “do not remember” content according to user settings.
- Provide controls to rebuild, pause, clear and inspect indexed sources.
- RAG is retrieval, not model-weight training.

## Suggested implementation order

### Phase A — chat foundations
- Persona presets and prompt enhancement.
- Message TTS playback controls and improved voice input UX.
- Conversation editing/branch data model.
- Copy/export TXT and chat organization foundations.

### Phase B — local memory and knowledge
- Memory manager and explicit save/forget controls.
- SQLite FTS retrieval over selected conversations and saved notes.
- Document text extraction and chunked summarization.
- PDF/DOCX export.

### Phase C — multimodal and analysis
- Image understanding through a compatible local vision model.
- Audio transcription and limited video frame analysis.
- Secure Python runner and chart/file return flow.

### Phase D — connected capabilities
- Source-cited live search improvements.
- Permissioned integrations.
- Explicit public sharing and Google Docs integration.

## Acceptance criteria

- Existing offline chat, model file, chat history, voice input and floating widget still work after update.
- With network disabled, ordinary chat, local memory, local RAG and supported local file analysis still work.
- Every unsupported format/model capability is reported honestly; no fabricated image/audio/video analysis.
- Editing a message creates a separate branch and preserves the old conversation.
- Memory save/delete actions are visible and testable.
- Code execution cannot access arbitrary device files, secrets or network.
- Network transfer, public sharing, external integrations and consequential actions require explicit user consent.
- CI debug and signed release builds pass; APK signer remains:
  c74aac6ea4254cf327dd265a0ff48f7eb7f39a1623f3531b0d73b13607524be1
