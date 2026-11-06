# Retired experiments — November 2026

## DeepSeek Team Bridge

The branch `feature/deepseek-team-bridge` adds a separate Termux-side console and GitHub bridge:
- `tools/termux/deepseek_team.py`
- `tools/termux/install.sh`
- `tools/termux/test_deepseek_team.py`
- `.github/workflows/team-bridge.yml`
- `docs/AI_TEAM_BRIDGE.md`

It is not part of the Android app's runtime and does not implement the on-device Jarvis model. It was an optional development-team experiment. During the user's test, the DeepSeek API account reported no available balance, so the experiment was not a viable always-on free workflow. The branch can be removed without affecting Jarvis APK builds, local GGUF inference, or the in-app updater.

The source branch remains available until the user completes cleanup; this note records its purpose and reason for retirement.
