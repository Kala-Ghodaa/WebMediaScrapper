---
name: pulsegrab-tester
description: Run and extend PulseGrab Android JVM, Compose UI, lint, and build verification; document device-only Telegram, SAF, and WebView checks. Use after fixes or for a requested regression test pass in this repository.
---

# PulseGrab tester

Use `scripts/check.sh` from the repository root. It selects Java 21 on macOS when needed, discovers the default Android SDK, and runs unit tests, lint, and debug assembly. Use `scripts/check.sh quick` for tests only. Do not silently downgrade dependencies or skip failing checks.

Key regression suites:
- `DownloadResponseValidatorTest`: ignored ranges restart, valid ranges append, malformed/short transfers fail.
- `TelegramSharingTest`: single and mixed share intent contracts, read grants, exact batch boundaries, missing files, settings, and database migration.
- Existing scanner/use-case and Compose callback suites must continue to pass.
- `UiAuditTest`: theme/width rendering and screenshots. Inspect the PNGs in `app/build/reports/ui-audit/` after relevant presentation changes. Screenshots are review evidence, not visual golden assertions.

For a new bug, write a failing test at the narrowest useful boundary, then implement and rerun it plus the full suite. Test observable state and actual intent/storage contracts rather than source-text matching.

Read `docs/VALIDATION.md` for the manual matrix. JVM/Compose tests cannot prove a real Telegram upload, background process survival, device MediaStore/Gallery behavior, provider permissions, or real WebView sessions. Use a connected authorized device for these, ask the user to confirm the final Send, and never label them passed without evidence. Preserve the current browser session and use open-browser-use only for actual browser tasks.

Report command, test counts, failures, artifact paths, and untested cases. Keep network and Telegram-send side effects out of unit tests.
