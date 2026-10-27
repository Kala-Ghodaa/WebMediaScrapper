# PulseGrab contributor instructions

This repository is a native Android/Compose app. Inspect affected call sites before editing and use the project wrapper; do not build a substitute web UI.

For requested review or regression work, use `.agents/skills/pulsegrab-reviewer/SKILL.md` and `.agents/skills/pulsegrab-tester/SKILL.md` as applicable. Run `scripts/check.sh` and inspect affected screenshots before declaring a change verified. Do not suppress failing checks or replace downloaded media with demo success placeholders.

Preserve these invariants:
- A completed task has nonempty persisted bytes and a usable URI. Keep cancellation and save failures distinct from success.
- A Telegram share contains only the requested completed files. Use content URIs, matching ClipData/streams, correct mixed MIME types, and temporary read grants. Launching Telegram is not proof of upload.
- Keep disk/network work off Main, retain per-task destinations, and do not destroy Room history to handle an unknown migration.
- Keep AI Studio signing configuration unchanged and credentials/development keys out of Git.

For browser tasks, use open-browser-use as the primary tool. Check `browser_status` first, retain `globalThis.browser` and `globalThis.tab`, continue the current task tab, and create the first new tab with its target URL rather than `about:blank`. Use `browser.yieldControl()` / `browser.resumeControl()` for human handoff. If setup is stale, run the user's `~/.obu/bin/obu verify --agent=<agent-id> --browser=<browser> --channel=<channel> --extension-id=<extension-id>` with discovered values.

Do not send real Telegram messages during tests without explicit authorization. Record connected-device gaps honestly in `docs/VALIDATION.md`.
