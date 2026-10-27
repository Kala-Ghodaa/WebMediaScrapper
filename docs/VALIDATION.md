# Validation and release checks

## Scope of this repair — October 27, 2026

The app is native Android, not a browser application. The repair focuses on reproducible sharing/storage/download defects and a theme-aware usability pass over the three main screens and seven supporting sheets/dialogs.

### Repaired behavior

- Mixed photo/video intents no longer claim to contain only images. Single/multiple stream contracts include all attachments in ClipData with read grants.
- Share preparation runs on IO, uses the persisted URI rather than stale full-size cache copies, and only considers completed tasks in the exact requested batch. A missing part never falls back to the whole folder or silently sends fewer items.
- Telegram preparation has a visible busy/error state and duplicate-action protection. Empty folders cannot share. Large folders expose parts of at most 10 files; the sheet remains available after opening Telegram. Copy explains that Telegram controls the actual upload, grouping, supported formats, and final caption.
- Downloads validate range offsets/lengths and final byte counts. An ignored range restarts instead of appending. Storage failures cannot produce a completed row with a null URI, failed writes remove pending MediaStore/SAF output, and real network failures no longer generate placeholder images.
- Same-title downloads reserve distinct filenames instead of overwriting one another.
- Worker reservation/completion ordering is atomic; pause/cleanup waits for cancelled writers before touching partial files. Unexpected worker errors become visible failed tasks.
- Folder, extension/resolution, and duplicate choices persist. New tasks snapshot the SAF destination; Room v4→v5 migration preserves history. Unknown older schemas fail rather than silently deleting data.
- Log export avoids an API-35-only Deque method on older devices.
- Browser callbacks follow the current page URL, avoid duplicate loads during page navigation, and support WebView back history.
- Shared UI surfaces respect light/dark themes; home removes repeated launch/format controls, toolbars wrap at compact widths, selection checks the actual visible IDs, and Telegram actions and captions are progressively disclosed.

## Automated evidence

Final full check: `scripts/check.sh` — **BUILD SUCCESSFUL**.

- **112 tests passed**, zero failures/errors (including 56 light/dark compact/wide UI rendering cases).
- Android lint: **0 errors, 87 advisory warnings** (dependency/version suggestions, unused resources, launcher icon shape, and style suggestions). No lint baseline or suppression was added.
- Debug APK assembled successfully.
- Both project skills passed the skill validator; shell scripts passed `bash -n`; `git diff --check` passed.
- Local Git hooks are enabled using `core.hooksPath=.githooks`.

UI PNGs were visually reviewed; this is not a connected-device certification.

- Pure tests: URL normalization, format filtering, range expansion, path clustering, scraper/media extraction, response/range validation.
- Android contract tests on APIs 24, 29, and 35: share intent MIME/stream/grant contracts, exact batches, missing/partial files, persisted preferences, migration.
- Local-HTTP transfer integration tests on API 24: actual enqueue → partial progress → pause → range resume → saved content URI; saved bytes must exactly match the source. Also covers a server ignoring Range.
- Compose interaction tests: existing download/item callbacks; empty share disabled, exact 10+1 batches, busy protection, large-text reachability, and hidden selection IDs.
- UI rendering: all main screens and sheets in light and dark themes at compact 360dp and wide 840dp widths. PNGs are manual review evidence, not golden-image assertions.
- Android lint and debug APK assembly. Existing advisory warnings are not suppressed.

### Reports

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/ui-audit/`
- `app/build/outputs/apk/debug/app-debug.apk`

## Connected-device checks still required

No Android device/emulator was connected during this repair. Do not interpret JVM tests or a launched intent as proof of any real Telegram upload.

| Flow | Device check / expected result |
| --- | --- |
| Telegram, 1 photo | Install/open Telegram; share a real downloaded photo; choose a test chat; user confirms Send; recipient can open it. |
| Telegram, 1 video | Repeat with a playable MP4; verify the preview and playback. |
| Mixed album | Share a photo/video mix; check both previews, grouping, caption, and recipient playback. Telegram may alter grouping or ignore EXTRA_TEXT. |
| 11+ files | Send part 1, return to PulseGrab, send part 2; verify no repeated or missing attachments. |
| Missing/deleted file | Delete a file externally; attempt its exact part; see a missing-file message and no substituted whole-folder share. |
| Telegram absent | Android chooser opens, with no crash or claim that an upload completed. |
| Scoped storage | Check save/read/open on API 29+; verify MediaStore indexing on the actual device. Gallery bucket discovery is not guaranteed by a share intent. |
| SAF | Choose internal/SD/cloud provider tree; restart; download; change destination while queued; verify each file retains its original target. Revoke access and verify visible failure. |
| Browser | Browse an authorized real site, follow links, press Back, rotate, detect media, and verify the current page is the referer. Real WebView/cookies/renderer behavior is not proven by JVM rendering. |
| Transfers/lifecycle | Pause/resume/retry, no storage space, loss of network, app background/process kill and relaunch. Current in-process jobs stop when their owning ViewModel is cleared and pause/recover after process death; this repair does not add a persistent background download service. |
| UI/accessibility | Real device keyboard/insets, TalkBack focus, 150–200% text, landscape and tablet navigation rail, long translated names, dialogs and scrolling. |

## Known boundaries

- This is Android sharing, not a Telegram Bot API uploader. No message is sent automatically, no recipient is preselected, and upload success cannot be inferred from `startActivity`.
- Existing v1–v3 Room schemas were not supplied; explicit migration work is required for them. v4→v5 is covered.
- The current app's site-specific extraction and anti-hotlink heuristics cannot be guaranteed across every live site or protected media URL.
- No dependency upgrade sweep, release signing, private-site crawl, or real account/message mutation was performed.
