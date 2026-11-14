# Jarvis Launcher — architecture foundation

Status: design baseline, 2026-11-14

## Product direction

Jarvis is a native Android launcher with an integrated assistant, not a reskin of a third-party launcher. The launcher owns the home surface and app navigation; the assistant is a first-class system within it. Existing Jarvis functionality must be preserved and migrated incrementally.

## Research basis and limits

The reference sample was a user-provided MOD APK (package `appmania.launcher.jarvis`, v11.7). It was decoded statically; it was not installed or executed. Findings describe that sample only and must not be treated as proof of official-release behavior. Reuse architectural patterns, not its source code, branding, visual assets, fonts, or proprietary layouts.

Observed patterns:
- Android HOME-capable activity and a ViewPager2-based multi-page home surface.
- Per-page grid model with item types for apps, shortcuts, folders and widgets; items carry IDs, placement/size and payload data.
- Separate page repository; pages load their own items and rebind widget/app views.
- Android package/launcher APIs for app and shortcut discovery/launch.
- Assistant voice path based on SpeechRecognizer with partial results and system-language selection; TTS for speech output.
- Assistant request includes prompt, language, tier, recent history (up to 12 entries) and an app-name list (up to 300); callable Firebase function `askJarvis`, timeout 25s/40s. Backend model/provider is unknown.
- HUD is built from custom Android Views/Canvas rather than relying only on static images. Components include battery, weather arc, orbit, chrono, comms and sky background.
- Animations use different update intervals and can be gated by visibility/power state.

## Product principles

1. Keep the launcher usable offline; core home/app launch must not depend on AI or network.
2. Keep the assistant modular: voice input, conversation UI, AI transport, tool execution and memory are separate modules.
3. Phone-first: launcher UI, app index, gestures, voice capture, Android integrations and quick commands run locally.
4. Server is optional and replaceable: complex reasoning can use a remote model, but the app must handle offline, timeout, quota and provider failure cleanly.
5. Permissions are incremental and contextual. Do not request SMS, contacts, calls, camera, location or notification access until a user enables a feature that needs it.
6. Performance before spectacle: animations pause when not visible; reduce frame rate/effects under battery saver, thermal pressure or low memory.
7. Safe actions: explain sensitive actions and request confirmation; maintain an auditable action log and allow cancellation where possible.
8. Preserve the current Jarvis app and signing/update path. Build changes in small tested increments; do not replace the existing app wholesale.

## Proposed module boundaries

- `launcher`: HOME entry point, workspace pages, grid, drag/drop, folders, app drawer/search, default-launcher onboarding.
- `appindex`: PackageManager/LauncherApps discovery, shortcuts, icons, package-change updates and launch intents.
- `workspace-data`: page/item models, persistence, migrations, backup/restore and layout validation.
- `widgets`: Android AppWidgetHost integration and Jarvis-native widgets with a common placement contract.
- `assistant-ui`: chat surface, compact response cards, activity/tool timeline and voice affordances.
- `voice`: SpeechRecognizer lifecycle, partial/final results, language preference, TTS and fallback states.
- `ai-client`: provider-neutral request/response contract, local/remote routing, streaming where supported, timeout/retry policy.
- `actions`: capability registry, permission checks, policy/confirmation, execution, cancellation and audit log.
- `hud`: custom Canvas views and animation scheduler with lifecycle/visibility/power-aware throttling.
- `settings`: appearance, workspace, privacy, assistant, model/network and accessibility settings.

These are logical boundaries first; do not split into many Gradle modules until the codebase justifies it.

## Core data contracts (initial)

Workspace page:
- stable page ID, order, optional user label, page-level settings.
Workspace item:
- stable item ID, page ID, kind (app/shortcut/folder/widget/native-widget), grid position, span, serialized kind-specific payload, schema version.
Assistant request:
- request ID, user text, locale, bounded conversation context, capability summaries (not an unfiltered dump of installed apps), network/privacy mode.
Assistant response:
- request ID, user-facing text, optional structured actions/cards, optional speech text, trace ID and safe error state.
Action:
- action ID, capability, typed arguments, risk level, required permissions, confirmation requirement, execution result.

Persist user workspace data in a versioned local database (Room is the preferred Android option if compatible with the current build setup). Never make JSON blobs the only long-term schema; use typed entities and explicit migrations. JSON can remain the interchange/backup format.

## UX direction

- Home: clean adaptive workspace, editable pages, folders and widgets; one clear entry to the assistant.
- Assistant: concise answer in chat; long-running tool work appears in a separate activity/timeline surface, not as dozens of chat messages.
- Voice: explicit listening/thinking/speaking/error states; user can interrupt speech and cancel a running task.
- HUD: optional visual layer, not the primary navigation. Every visual effect must have a reduced-motion/low-power mode.
- Search: one entry point that can search apps, workspace items and (when enabled) assistant knowledge, with clear result types.

## Delivery plan

### Phase 0 — protect and map existing app
- Record current default branch, package ID, signing/update constraints and working features.
- Establish baseline build and smoke test before launcher refactor.
- Keep this architecture document on a separate branch until reviewed.

### Phase 1 — launcher skeleton
- Add HOME intent filter to the existing launcher entry activity (or a dedicated HomeActivity after checking current navigation).
- Implement default-home onboarding and fallback to the current app screen.
- Display installed apps and launch them; handle package changes and app labels/icons.
- Add one simple workspace page with persistent app tiles.

### Phase 2 — workspace engine
- Add multiple pages, stable item IDs, grid placement, drag/drop, folders and persistence.
- Add schema versioning, migration tests and restore tests.
- Integrate Android AppWidgetHost only after native tiles and lifecycle work reliably.

### Phase 3 — assistant integration
- Preserve current assistant/chat behavior.
- Add provider-neutral AI transport and robust offline/error handling.
- Add voice lifecycle, TTS, interrupt/cancel and permission UX.
- Add capability registry and confirmation-gated actions.

### Phase 4 — visual system
- Establish original Jarvis design tokens, typography and iconography.
- Implement a small number of custom HUD views and lifecycle-aware animation scheduling.
- Test on low/mid-range hardware, battery saver and thermal conditions.

### Phase 5 — hardening and release
- Accessibility, localization, backup/restore, privacy disclosures and permission review.
- UI/instrumentation tests, crash/ANR checks, performance profiling and upgrade tests.
- Release APK signed with the same key as the current installed app.

## First implementation acceptance criteria

1. Existing app still builds and launches.
2. User can select Jarvis as the default Home app and return to the previous launcher.
3. Installed apps are listed and launch correctly.
4. One workspace page can place, move, save and restore app tiles after process death/reboot.
5. No AI/network permission or server is required for basic launcher operation.
6. Tests cover grid bounds, collisions, persistence round-trip and missing/uninstalled packages.

## Open decisions before coding the HOME surface

- Current Gradle/AndroidX setup and minimum/target SDK need inspection before selecting APIs.
- Decide whether the existing MainActivity becomes HOME or a dedicated HomeActivity is safer.
- Confirm existing app package/signing key and baseline APK install/update path.
- Define target devices and minimum acceptable launcher performance.


## Existing repository baseline (inspected before implementation)

The current default branch is a very small native Kotlin MVP, not yet a launcher:
- Gradle: Android Gradle Plugin 8.7.3, Kotlin Android 2.0.21, Java/Kotlin target 17.
- App: namespace/applicationId `com.stahij.jarvis`, compileSdk 35, minSdk 26, targetSdk 35, versionCode 2 / versionName 0.1.1.
- Current `MainActivity` is the chat screen and starts an optional floating `OrbService`; it is registered with MAIN/LAUNCHER only, not MAIN/HOME.
- Current chat stores up to 40 messages in SharedPreferences JSON and calls an OpenAI-compatible endpoint at `http://127.0.0.1:8080/v1/chat/completions` (llama-server/Termux in the current implementation). It has a 120-second read timeout and explicitly reports a Termux/llama-server connection error.
- The manifest currently requests INTERNET, overlay, boot and foreground-service permissions and declares the floating service/boot receiver.

### Architectural decision for migration

Do **not** turn the current chat `MainActivity` into the HOME activity in the first step. Keep it as the known-working assistant entry point and introduce a dedicated `HomeActivity` with HOME/DEFAULT intent filters. This isolates launcher lifecycle/default-home behavior from chat and overlay code, makes rollback easier, and avoids breaking the existing entry flow. The launcher can open the existing assistant screen until the new assistant surface is ready.

The current local-model HTTP transport is an implementation detail behind an AI client interface, not a permanent dependency on Termux. First preserve it behind that interface so the app still works; then add an embedded local runtime or configurable remote provider as a separate tested backend. Do not remove the existing path until a replacement has passed device tests.

Before adding Room or a large dependency set, inspect the full source tree and CI build workflow, then choose the smallest compatible dependency changes. The first launcher milestone should be a dedicated HOME activity, app listing/launch, and a persisted single-page grid; no HUD, AI migration or full widget host in that first milestone.
