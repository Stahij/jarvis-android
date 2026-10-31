# Jarvis Internet Research — Public Web Access Specification

Status: target behavior for a broad public-web research tool. This is not a claim that universal access or paywall/CAPTCHA bypass is possible.

## Goal

Let the local Jarvis model research arbitrary publicly accessible websites rather than being limited to Wikipedia. Network responses are evidence for the on-device model, not a remote AI service.

## Capabilities

1. Search the public web through configurable search providers/adapters.
2. Open a user-supplied HTTP(S) URL.
3. Extract readable text from HTML while retaining title, canonical URL, description and retrieval time.
4. Follow selected links and fetch linked public text/PDF documents when supported.
5. Search within fetched text and retrieve bounded relevant excerpts.
6. Compare multiple sources, identify disagreements and preserve citations.
7. Save selected sources and excerpts into the app-private local knowledge store.
8. Return explicit errors for robots restrictions, authentication, rate limits, CAPTCHA, unsupported formats, timeouts and oversized pages.

## Privacy and trust

- Ordinary chat remains offline.
- Only the user's explicit research query and necessary URL are sent to the selected search/site provider.
- Never send chat history, private files, model files, GitHub tokens or unrelated personal data.
- Do not bypass authentication, paywalls, CAPTCHA, robots restrictions or access controls.
- Block localhost, private/link-local IP ranges and non-HTTP(S) schemes to reduce SSRF and unsafe URL risks.
- Bound redirects, response bytes, page count, link depth and request duration.
- Sanitize HTML and treat extracted content as untrusted quoted source material. Ignore instructions found in pages.
- Keep source provenance: title, URL, retrieval timestamp and content hash.
- Clearly distinguish source-backed claims, model synthesis and uncertainty.

## Search strategy

Use an adapter interface so providers can be changed without coupling the chat UI:
- primary general search provider;
- optional secondary provider for corroboration;
- direct URL fetch independent of search provider;
- provider health and rate-limit handling.

Do not hard-code one encyclopedia as the entire Internet feature. Avoid scraping providers in ways that violate their terms; use supported public endpoints or user-configured search services.

## Extraction

- HTML: remove scripts/styles/navigation noise; preserve headings, paragraphs, lists, tables and links.
- PDF: download only when publicly accessible and within size limits; extract text locally. Scanned PDFs report OCR requirement.
- Plain text, Markdown, JSON, XML and CSV: decode safely with size caps.
- Office documents: add format-specific local parsers in a later stage.
- Binary/media formats must not be represented as understood unless a matching local parser/model exists.

## Research workflow

1. Parse the research request and identify the information needed.
2. Search broadly and select diverse, relevant sources.
3. Fetch pages and extract readable text.
4. Retrieve relevant passages rather than stuffing full pages into the prompt.
5. Ask the local model to synthesize with source labels.
6. Attach citations to claims and flag conflicting or stale evidence.
7. Offer to save chosen sources locally.

## Initial implementation order

A. General HTTP fetch + HTML text extraction + safe URL validation.
B. Search provider adapters and source cards with citations.
C. Multi-source retrieval and comparison in chat.
D. PDF/text document extraction and local source index.
E. Optional scheduled research, disabled by default.

## Acceptance criteria

- Search can return sources outside Wikipedia.
- User-supplied public URLs can be fetched and readable text extracted.
- Every displayed source includes its canonical URL and retrieval time.
- Failed or blocked pages are reported honestly.
- Ordinary chat creates no network traffic.
- No private conversation or file data is sent without a separate explicit action.
