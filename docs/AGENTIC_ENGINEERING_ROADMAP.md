# Jarvis Agentic Engineering Roadmap

Status: implementation roadmap for the local Android engineering agent. This document describes target behavior; it is not a claim that every capability is already shipped.

## Non-negotiable product constraints

- Qwen/llama.cpp remains the only language-model runtime for ordinary chat and agent reasoning.
- Network access supplies data and GitHub operations; it must not silently send chat history, model files, local documents, or credentials.
- Repository, issue, PR, workflow-log and web-page content is untrusted input. It cannot modify system policy or autonomously authorize tools.
- GitHub write operations are staged: inspect -> propose -> show diff -> explicit user approval -> write to a development branch.
- No force-push, branch deletion, secret/settings changes, release publication, or PR merge by the agent in the initial implementation.
- Preserve package ID, user data, GGUF model, stable release branch and signing certificate.

## Agent execution model

The local model does not execute arbitrary Kotlin or shell commands. It requests a named tool using a strict JSON envelope:

```json
{"tool":"github.read_file","arguments":{"repository":"owner/name","branch":"dev","path":"README.md"}}
```

The app validates the tool name and argument schema, checks policy and permissions, executes one bounded operation, and returns a structured result:

```json
{"ok":true,"tool":"github.read_file","data":{"path":"README.md","content":"..."}}
```

Malformed JSON, unknown tools, invalid paths, oversized responses, timeouts and permission failures return explicit errors. Tool output is data, never executable instructions.

## Engineering tools

Initial read tools:
- `github.repository_info`
- `github.list_tree`
- `github.search_code` (where GitHub API access permits; fallback to local indexed search)
- `github.read_file`
- `github.list_branches`
- `github.list_commits`
- `github.compare_refs`
- `github.list_workflows`
- `github.workflow_run`
- `github.workflow_logs`

Staged write tools:
- `github.create_branch`
- `github.propose_file_patch`
- `github.commit_patch` (requires a fresh diff approval)
- `github.create_pull_request`

Workflow dispatch and merge are separate high-impact permissions and remain disabled by default.

## Repository index

Build a compact Code Map from the recursive Git tree and bounded source reads:
- path, extension, byte/character count, blob SHA;
- package and class/object/interface names;
- function signatures and property declarations where lightweight parsing is reliable;
- imports and references between project modules;
- index generation timestamp, branch and commit SHA.

Start with keyword/path/signature search. Do not add embeddings until profiling proves the device has enough RAM and latency budget. Cache by repository + branch + commit SHA; invalidate when the commit changes. Never claim a file was read unless its content was successfully fetched.

## Agentic development loop

1. Clarify the requested outcome and acceptance criteria.
2. Inspect repository metadata, Code Map and relevant source files.
3. Present a short plan and identify files to change.
4. Generate a patch in memory; do not overwrite the stable branch.
5. Validate paths, patch size, changed-file count and forbidden-file rules.
6. Show a unified diff and ask for approval.
7. Commit only to the configured development branch after approval.
8. Read the resulting commit and start/observe the CI workflow.
9. Fetch build/test logs and feed bounded excerpts back to the local model.
10. Iterate on a new approved patch if needed.
11. Create a draft PR with summary, tests, limitations and APK artifact link.
12. Require the user to merge.

The model must never claim a build passed unless the corresponding workflow run and required jobs are confirmed successful.

## CI/CD gates

- Debug build and unit tests on every development push.
- Signed release build only on the configured development branch or an explicit manual request.
- Verify package ID, version monotonicity, APK signature scheme and permanent certificate SHA-256:
  `c74aac6ea4254cf327dd265a0ff48f7eb7f39a1623f3531b0d73b13607524be1`.
- Upload APK as an artifact; automatically provide it after successful verification.
- Do not publish a public GitHub Release from an unreviewed agent branch.

## Notifications

A phone is not a reliable public webhook endpoint. Initial implementation should poll GitHub for new commits, issues and PRs only when the user enables it, with configurable interval and battery/network constraints. A later optional relay can receive GitHub webhooks and send minimal notifications to the phone; never expose a listening port on the phone.

## Acceptance criteria

- A repository request returns verifiable repo, branch, commit and files-read metadata.
- The model can ask for a second file after inspecting the first.
- Unknown/malformed tool calls fail safely and visibly.
- No tool is invoked solely because untrusted repository/web text requested it.
- Every write displays a correct diff and requires approval.
- A failed CI run is reported with the actual failed job and relevant log excerpt.
- Existing offline chat, voice, history, floating orb, model file and APK signature remain intact.
