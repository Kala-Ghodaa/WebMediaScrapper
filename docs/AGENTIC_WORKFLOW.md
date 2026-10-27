# Agentic engineering: how this repair was done, and how to repeat it

This guide explains a practical workflow for using a coding agent on **any existing project**. PulseGrab is the worked example, not a mandatory architecture or toolchain.

Start with this guide for the reasoning. Use `AGENTIC_PROJECT_STARTER.md` beside it for copy-ready instructions, skill templates, hook templates, and a task prompt.

## 1. What “agentic” means in this guide

An agent works in an evidence loop rather than only returning suggested code:

```text
User outcome + safety boundaries
             |
             v
Read project instructions, source, tests, and Git state
             |
             v
Map the real feature path and establish a baseline
             |
             v
Reproduce / identify a failure -> make a bounded change
             ^                           |
             |                           v
             +--------- failing checks / visual feedback
                                         |
                                         v
                           Review evidence and limitations
                                         |
                                         v
                      Reusable guidance + checks + commit
```

The important property is **observable feedback**: a tool result, actual file bytes, a rendered screen, or a failing test changes the next action. Generating a large patch is not sufficient evidence of success.

### Different pieces have different jobs

| Piece | Job | What it does not prove |
| --- | --- | --- |
| `AGENTS.md` | Persistent project instructions: commands, architecture boundaries, invariants | It does not execute tests or enforce permissions |
| A skill | Reusable, scoped instructions for a kind of task | A skill is not automatically a separately running agent |
| Shell / browser / image tools | Read or change the relevant environment | A successful tool call does not automatically prove the user's outcome |
| Deterministic script | Execute repeatable checks | It does not make subjective design decisions |
| Git hook | Run a script at a Git event | A local hook is not a server-side policy or an AI reviewer |
| CI | A shared place to run checks, if configured | Local hooks alone do not create CI |
| Subagent | A separate delegated execution context, when available and authorized | Role names or skill files alone do not launch one |
| Evidence record | Commands, observations, remaining gaps | A narrative is not a substitute for the artifacts it describes |

For Codex specifically, official guidance distinguishes project instructions, reusable skills, tool connections, and subagents. Repository skills can live in `.agents/skills`; a skill has a `SKILL.md` with `name` and `description`, plus optional supporting files. See the official references at the end. The rest of the layout in this guide is a suggested project convention, not a required agent framework.

## 2. What actually happened in PulseGrab

The starting request was broad: Telegram upload was unreliable, other functionality was broken, and the UI needed cleanup. The first useful decision was discovering that this was a **native Android/Compose repository**, despite the workspace folder name suggesting a web scraper.

### Actual work, with concrete artifacts

| Stage | What was done here | Where to inspect it |
| --- | --- | --- |
| Discovery | Read build files, manifest, source structure, existing tests, signing rules, and Git status | `app/build.gradle.kts`, `AndroidManifest.xml`, `app/src/` |
| Feature tracing | Followed folder sharing from sheet to ViewModel, URI preparation, and Android share intent | `TelegramFolderShareSheet.kt`, `MainDownloaderViewModel.kt`, `DownloadRepository.kt`, `TelegramMediaShareHelper.kt` |
| Build recovery | Restored the missing Gradle wrapper launchers/JAR; used Java 21 and the requested Android SDK | `gradlew`, `gradlew.bat`, `gradle/wrapper/`, `scripts/check.sh` |
| Correctness fixes | Addressed mixed MIME types, exact batch boundaries, storage errors, range resumes, cancellation ordering, filename collisions, and settings | `app/src/main/java/com/example/data/` and `ui/` |
| Regression coverage | Added pure, Android contract, local-HTTP transfer, and Compose interaction tests | `app/src/test/java/com/example/` |
| Visual work | Simplified duplicate controls, used theme-aware surfaces, and rendered compact/wide light/dark states | `ui/screens/`, `ui/components/`, `UiAuditTest.kt` |
| Visual feedback | Opened generated PNGs and temporary contact sheets; corrected cramped controls found in those images | Generated `app/build/reports/ui-audit/` |
| Reuse | Created repository instructions, reviewer/tester skills, check scripts, and Git hooks | `AGENTS.md`, `.agents/skills/`, `scripts/`, `.githooks/` |
| Honest handoff | Recorded automated results and device-only checks that remained | `docs/VALIDATION.md` |

**What was not done:** no separate reviewer/tester subagents were launched; no AI-generated raster artwork was created; no real Telegram message was sent; no connected-device test or CI setup was completed. Reviewer and tester **skills** were created, not an autonomous multi-agent system. The hooks run deterministic checks, not a model-based review.

The existing fonts/icons were reused and the UI was changed in Compose code. The PNGs were **screenshots of rendered UI**, not generated design assets.

## 3. Start with discovery, not scaffolding

For a new task, answer these questions before editing:

1. What is the actual application type and primary language?
2. Which entry point owns the behavior the user described?
3. What instructions, tests, scripts, and assets already exist?
4. Which files are already modified, and who owns those changes?
5. What is the smallest command that can establish a baseline?
6. Which steps could mutate real accounts, production data, payments, or external messages?

Useful initial commands, adapted to the repository:

```sh
pwd
git status --short
git log -3 --oneline
find . -name AGENTS.md -not -path './.git/*'
# Then inspect the relevant manifest, dependency file, entry point, and tests.
```

Avoid dumping the entire repository into context. Start broad enough to orient, then read narrow feature slices. A folder name, README claim, or previous agent summary can be wrong; inspect the source and build configuration.

Record pre-existing failures separately from changes introduced by your patch. Do not upgrade dependencies, replace the stack, or rewrite signing configuration just to get a green baseline.

**PulseGrab lesson:** repairing an Android app with a substitute website would have missed the actual URI grants, Room state, MediaStore writes, and Telegram handoff.

## 4. Convert “it doesn't work” into testable contracts

Trace the feature across boundaries rather than assuming the visible button is the only problem.

```text
User action -> UI state -> application logic -> storage/network/platform API
                                                    |
                                                    v
                                          external system / final outcome
```

For every boundary, ask what counts as success and what must never happen.

### Example: sharing an album

- Only requested, completed tasks may enter the batch.
- Missing files must produce a visible error, not a larger substitute batch.
- Streams, ClipData, MIME types, and read grants must agree.
- Expensive file preparation must not freeze Main.
- A second tap while preparing must not launch a duplicate action.
- Opening Telegram is a handoff; it is not proof that a recipient received a message.

### Example: downloading a file

- Resume may append only when the returned byte range matches the partial file.
- A server returning a full response to a range request must restart the file.
- A failed save must not mark the task complete.
- Two unrelated items must not overwrite each other because they share a title.
- Success should be measured against persisted bytes, not only a progress label.

Apply the same pattern elsewhere: an email queued is not an email delivered; an HTTP 200 is not necessarily a valid business result; a payment screen opened is not a payment settled.

## 5. Implement in bounded loops

A useful loop is:

1. Inspect the affected code and call sites.
2. State the invariant and a concrete failure example.
3. Add a reproducing test first where feasible; otherwise capture the failure and add regression coverage with the fix.
4. Change the smallest coherent slice.
5. Run the focused test, then neighboring tests.
6. Inspect the diff before expanding the scope.

This repair was iterative: not every new test preceded every production change. Later checks exposed additional defects and test-harness issues, and those findings drove further revisions. Do not rewrite the history as perfect test-driven development when it was not.

If delegation is explicitly authorized, useful side tasks can include a bounded review or a disjoint implementation slice. Keep urgent blockers local, assign non-overlapping write scopes, and integrate returned work. Do not add agents simply to make a workflow look more “agentic.”

## 6. Build a layered test strategy

Choose tests based on the boundary they can actually observe.

| Layer | Typical question | PulseGrab example |
| --- | --- | --- |
| Pure unit | Is the transformation/decision correct? | Range validation and collision-safe filenames |
| Contract | Are API inputs/outputs wired correctly? | Android action, stream list, MIME types, ClipData, flags |
| Local integration | Do components work together with controlled IO? | Local HTTP server -> pause/resume -> saved URI -> exact byte comparison |
| UI interaction | Does the control cause the intended state/callback? | Empty sharing disabled; 10+1 parts; busy-state protection |
| Rendering | Does the actual UI lay out sensibly? | Themes, widths, long names, empty/loading states |
| Static checks + build | Does the project satisfy its supported APIs and package? | Android lint and debug APK assembly |
| Real environment | Does the final integration work on the actual platform? | Telegram delivery, SAF providers, live WebView sessions |

Use local fixtures for routine tests. They should not send Telegram messages, crawl private sites, require production credentials, or depend on a third-party service being available.

### How evidence improved this repair

- Lint found a Deque API call unavailable on older supported Android versions. The code was corrected rather than hiding the warning.
- Tests compared downloaded file contents with source bytes. This is stronger than asserting that a “completed” label appeared.
- A hidden-selection-ID test caught a UI count shortcut that incorrectly treated visible media as fully selected.
- Some Robolectric screenshot captures timed out through the initial capture route. The rendering test used native graphics and drew the actual Compose root view instead.
- Modal sheets added another Compose root; the audit selected the topmost root rather than assuming only one existed.
- FileProvider caches held absolute paths across Robolectric test environments. Cache reset was confined to test setup because that isolation issue was not evidence of a production storage defect.

When a test fails, classify it: product defect, incorrect expectation after an intentional UI change, environment problem, or harness problem. Fix the correct layer. Never change a test merely to force a green result.

## 7. Visual review and image creation are different workflows

### A. Visual QA — used in this repair

```text
Render real component/screen -> save PNG -> inspect at intended size
                -> identify clipping/clutter/contrast problems
                -> change UI code -> render again
```

Here, `UiAuditTest.kt` produced 56 cases: 14 screen/state variants × two themes × two widths. Those PNGs are generated build artifacts, not checked-in source assets. The test suite also checks a sharing interaction at 150% text scale.

Run the project's checks to regenerate them:

```sh
scripts/check.sh quick
# Inspect app/build/reports/ui-audit/
```

The rendering pass does not perform golden-image comparison. Images were manually inspected. A successful render alone cannot prove accessibility, keyboard behavior, native WebView fidelity, or real-device integration.

For a web app, inspect the real browser page with the supported browser tool. For a native app, use appropriate component renders and a device/emulator. Do not substitute one platform's screenshot for another's validation.

### B. AI artwork generation — optional, not used here

Use this when the product actually needs new bitmap artwork: a hero illustration, texture, photograph, sprite, or transparent cutout. Do not generate bitmap buttons, text, or icons when existing vector assets or native UI code are the appropriate implementation.

A reusable asset loop:

1. Define its role, dimensions/aspect ratio, background/alpha needs, palette, file budget, and mobile crop.
2. Check whether an approved asset or design system already exists.
3. Invoke the available image-generation skill/tool according to its instructions. Do not invent a CLI or model name.
4. Generate a small number of purposeful variants; inspect the actual files.
5. Integrate the selected asset in the real UI and inspect responsive crops, contrast, readability, and accessibility text.
6. Record the prompt/provenance and file path; optimize using the project's existing asset pipeline.

Example brief to adapt:

```text
Create a 16:9 editorial illustration for a media-library empty state.
Use the supplied palette and existing visual style. Leave negative space
for UI text; do not put text or logos inside the image. Specify whether
an opaque or transparent background is required. Also plan a narrow crop.
Deliver the bitmap into the project's approved asset location, then inspect
it inside the actual interface rather than only as a standalone image.
```

Do not describe a generated mockup as implemented UI, or a rendered screenshot as newly generated artwork.

## 8. Turn repeated knowledge into a skill

First stabilize the workflow; then package the parts that will save future rediscovery.

For this project:

- `pulsegrab-reviewer` records the important storage/sharing/lifecycle/UI invariants and review expectations.
- `pulsegrab-tester` records check commands, meaningful test boundaries, screenshot evidence, and device-only limitations.

A skill should say **when it applies**, where useful evidence lives, which domain-specific invariants matter, and what output to produce. Keep generic instructions short. Put large reference material in optional `references/`, and deterministic helpers in `scripts/` only when they provide a concrete benefit.

The skill-creation helper was used here to initialize the folders/metadata, then the instructions were authored and validated. A Python YAML dependency was unavailable in one interpreter; a temporary virtual environment was used for validation instead of changing the application's dependencies.

A portable manual skill template is in the starter document. If your installed creator provides initializer/validator scripts, use their documented paths; those helper paths are environment-specific and are not included in this repository.

After writing a skill, check both structure and behavior:

- Valid frontmatter, names, and no unfinished scaffold placeholders.
- Description routes the right task, not every task.
- Instructions preserve user scope and approval boundaries.
- Commands and references actually exist.
- A realistic request leads to useful, evidence-backed output.

A schema validator cannot prove that a skill makes good decisions. Also, calling instructions “reviewer” does not make the review independent. No independent subagent review occurred in this repair.

## 9. Turn reliable commands into hooks

The enforcement path here is deliberately simple:

```text
Git pre-commit -> staged whitespace check + scripts/check.sh quick
Git pre-push   -> scripts/check.sh
                         |
                         +-> tests + lint + debug assembly
```

These are **Git hooks**, not Codex lifecycle hooks, model callbacks, or an automatic AI reviewer. Keep them deterministic and inspectable.

`setup-dev.sh` enables this repository's hook path locally and creates a debug key only if missing. The key remains ignored. Existing release/AI Studio signing configuration was preserved.

```sh
scripts/setup-dev.sh
git config --local --get core.hooksPath
# Expected for this repo: .githooks
```

To reuse the pattern, replace the check commands with the new project's existing commands. Test executable permissions, quoting, working-directory handling, and failure propagation. A failing check should stop the hook; the hook should not repair source or make a commit behind the developer's back.

Local hooks are not a complete shared enforcement boundary. A separate CI configuration and branch policy can run the same checks if the team chooses to add them. This repair did not create that CI layer. Installing a hook path also changes local Git configuration; avoid overwriting another project's existing hook setup without inspecting it.

## 10. Commit only the intended deliverables

Before committing:

```sh
git status --short
git diff --check
git diff --stat
# Stage explicit files or reviewed logical groups, not an uninspected workspace.
git diff --cached --check
git diff --cached --stat
git diff --cached --name-only
# Review the actual staged content, then commit with hooks enabled.
```

Check that credentials, `.env`, signing keys, generated APKs, build caches, screenshots with private data, and unrelated user changes are excluded. Commit source tests and the instructions needed to reproduce evidence; not every generated report needs to be tracked.

Staging this repair exposed another useful failure: the generated Windows wrapper used CRLF line endings, which the staged whitespace check flagged. The earlier unstaged diff check had not covered that untracked file. Targeted `.gitattributes` rules normalized wrapper text in Git while preserving Windows checkout line endings, and the check was rerun. The hook was not bypassed.

After committing, inspect the new commit and working-tree status. A local commit is not a push, release, or deployment. Do not push unless requested.

## 11. Close the loop honestly

A useful handoff includes:

- What changed and why.
- Which commands ran and what passed/failed.
- Where reports and build artifacts live.
- What visual evidence was inspected.
- What was not tested and what would prove it.
- Commit identity, if requested.

For this repair, the recorded full check passed 112 tests, including 56 rendering cases; lint had zero errors and 87 advisory warnings; and a debug APK was built. `VALIDATION.md` remains the detailed case-specific evidence and device checklist.

The critical remaining boundary was real Telegram delivery: no Android device was connected, and no actual message was sent. That is not a small wording detail—it is the difference between a verified share-intent contract and an externally completed upload.

## 12. The transferable lesson

Do not copy every folder or every tool into every project. Copy the reasoning:

> Inspect the real system, define observable contracts, make bounded changes, test at the right boundaries, inspect the actual output, and preserve only the reusable knowledge that earned its place.

### Official Codex references

These support the Codex-specific guidance above, not the project-specific test results. Verified for this guide on October 27, 2026; consult the current documentation when product behavior changes.

```text
Customization: project guidance, skills, tools, and subagents
https://learn.chatgpt.com/docs/customization/overview

Skill structure, creation, invocation, and repository locations
https://learn.chatgpt.com/docs/build-skills

Iterating development workflows; optional conventions vs required files
https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex
```
