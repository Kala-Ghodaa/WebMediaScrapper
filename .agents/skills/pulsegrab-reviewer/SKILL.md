---
name: pulsegrab-reviewer
description: Review PulseGrab Android changes for download integrity, content-URI sharing, storage migrations, scanner lifecycle, and Compose usability. Use for a requested review or pre-release audit of this repository.
---

# PulseGrab reviewer

Read the diff and affected call sites before forming findings. The app is native Android/Compose, not a web frontend. Start with `docs/VALIDATION.md` for the verified baseline and outstanding device checks.

Follow the data path that changed:
- Downloads: `DownloadRepository` → Room `DownloadTaskEntity` → MediaStore or persisted SAF target. A complete task needs actual nonempty saved media. Never synthesize a success placeholder, discard a part before storage succeeds, or destroy history to repair a migration.
- Telegram: `TelegramFolderShareSheet` → ViewModel → IO URI preparation → `TelegramMediaShareHelper`. Exact requested completed IDs must stay scoped; missing files must not cause whole-folder substitution. `EXTRA_STREAM`, `ClipData`, MIME types, and temporary read grants must agree. A launched intent is not an uploaded message.
- Jobs: reserve the worker before launch; old completion must not remove a new worker. Pause/retry/delete need cancellation ordering. Keep blocking IO off Main and preserve cancellation exceptions.
- UI: test compact width, large text, light and dark themes, long names, empty/error/loading states, and disabled actions. Keep uncommon actions discoverable without duplicating all primary controls.

Use the tester skill or `scripts/check.sh` for evidence. Report concrete findings with severity, file/line, reproduction, and impact. Distinguish verified defects from device-dependent risks. Do not send real Telegram messages or crawl private sites just to review a patch.
