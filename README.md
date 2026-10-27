# PulseGrab

Native Android/Jetpack Compose media discovery, album collection, download management, and Android sharing handoff.

## Develop

Prerequisites: Java 21, Android SDK (the build requests API 36.1), and SDK licenses accepted. Set `ANDROID_HOME` if the SDK is not in the standard macOS location.

```sh
scripts/setup-dev.sh  # create a missing local debug key and enable repository Git hooks
scripts/check.sh     # JVM/Compose tests, Android lint, debug APK
scripts/check.sh quick
```

The Gradle wrapper is checked in. Existing AI Studio signing configuration is retained; development keys remain ignored. No Telegram bot token or account credentials are required: sharing opens the installed Telegram app or Android chooser, and the user chooses a recipient and confirms Send.

## Quality tools

- `.agents/skills/pulsegrab-reviewer`: code/storage/sharing/UI review guidance.
- `.agents/skills/pulsegrab-tester`: test and device-validation workflow.
- `.githooks/pre-commit`: staged whitespace checks and JVM/Compose tests.
- `.githooks/pre-push`: tests, lint, and debug assembly.
- `docs/VALIDATION.md`: observed results, limitations, and manual device checklist.

Output APK: `app/build/outputs/apk/debug/app-debug.apk`.
Test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
UI review PNGs: `app/build/reports/ui-audit/`.

## Storage compatibility

Room v4 databases migrate non-destructively to v5. Each newly queued download retains its selected SAF tree instead of following a later global folder change. Older database schemas (v1–v3) need explicit migrations before an upgrade; the app no longer silently drops their history. Revoked custom-folder access fails visibly rather than silently saving elsewhere.

## Learn and reuse the workflow

- `docs/AGENTIC_WORKFLOW.md`: the actual repair process, testing and visual-feedback loops, skill/hook creation, optional artwork workflow, and limitations.
- `docs/AGENTIC_PROJECT_STARTER.md`: adaptable task prompt, project instructions, reviewer/tester skill templates, hook/check skeletons, and a definition of done for another project.

The guide distinguishes rendered UI screenshots from AI-created artwork and repository skills from separately running agents. Neither artwork generation nor separate reviewer/tester agents were used in this repair.
