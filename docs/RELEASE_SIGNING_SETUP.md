# Jarvis release signing setup

The release signing key is generated outside the repository. Never commit the keystore, its Base64 representation, or passwords.

Add these **Repository secrets** in GitHub:
- `JARVIS_KEYSTORE_BASE64` — the complete one-line Base64 content from the private signing kit.
- `JARVIS_STORE_PASSWORD`
- `JARVIS_KEY_ALIAS`
- `JARVIS_KEY_PASSWORD`

Then run **Actions → Build Jarvis Android APK → Run workflow** and set `build_release` to true. The workflow restores the keystore only on the temporary runner, builds a release APK, verifies its signature with apksigner, and uploads the APK as an artifact.

Expected signing certificate SHA-256:

`D2:D4:96:E1:05:97:20:CC:2E:4C:79:3D:95:10:03:B7:CD:48:2F:47:45:9C:FC:C1:B0:E8:76:F1:F0:98:C9`

Important migration note: the existing 0.1.1-preview APK is signed with an Android Debug certificate. A release-signed APK cannot update it in place unless its signing certificate matches. Keep the existing app installed until a data-preserving migration path is tested.

The current project still uses Termux/localhost for inference. This signing bootstrap does not claim that the local llama.cpp integration or in-app updater has been implemented yet.
