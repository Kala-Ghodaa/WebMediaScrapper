# Copy-ready starter for an agent-assisted project

Use this alongside `AGENTIC_WORKFLOW.md`. These are templates to **adapt**, not instructions to overwrite an existing project's tooling or configuration. Replace examples with commands verified in that repository.

## 1. Start with an outcome-oriented task

```text
Inspect this repository and its existing instructions before editing.

Outcome:
[Describe the user-visible behavior that must work.]

Acceptance criteria:
- [Observable successful case.]
- [Important failure/empty/loading case.]
- [State or data that must never be lost or substituted.]

Constraints:
- Preserve unrelated work and the existing stack/design system.
- Do not expose secrets, change production accounts, or perform irreversible
  external actions without explicit authorization.
- Do not launch subagents unless delegation is authorized.

Workflow:
1. Identify the real entry point and trace the feature through its boundaries.
2. Run a baseline; distinguish existing failures from new regressions.
3. Reproduce the defect and add focused regression coverage.
4. Make a bounded patch; run focused and full checks.
5. For UI work, render/inspect actual screens at compact and wide sizes,
   both themes where supported, and at increased text scale.
6. Review the diff and record evidence plus remaining environment/device gaps.
7. Package genuinely repeated reviewer/tester knowledge as scoped skills and
   put deterministic checks behind scripts/hooks if appropriate.
8. Commit or push only if requested; report the resulting commit when made.

Deliverables:
- Working source changes and regression tests.
- Exact validation commands and report/artifact paths.
- A short explanation of what was and was not verified.
```

Narrow the request if only documentation, planning, or review is wanted. Do not let this starter silently authorize implementation, asset generation, CI setup, or real account mutations outside the requested scope.

## 2. Keep the layout small

A possible layout, only where the project needs it:

```text
AGENTS.md
.agents/skills/
  project-reviewer/SKILL.md
  project-tester/SKILL.md
scripts/
  check.sh
.githooks/
  pre-commit
  pre-push
docs/
  VALIDATION.md
```

An existing `Makefile`, package scripts, task runner, or hook manager may already serve these roles. Prefer reusing it rather than creating a second system.

## 3. Minimal project instructions (`AGENTS.md`)

```markdown
# Project instructions

## Architecture and entry points
- Application type: [native Android / web / service / library / other].
- Feature entry points: [verified paths].
- Existing design/assets: [verified locations].

## Commands
- Focused checks: [verified command].
- Full checks/build: [verified command].
- Generated reports: [paths].

## Invariants
- [Concrete data integrity rule.]
- [External side effect that needs a human decision.]
- Preserve unrelated changes and existing credentials/signing setup.

## Review and validation
- Read affected call sites and test observable behavior.
- Inspect actual UI output when presentation changes.
- Record unsupported environments and unverified integrations honestly.
- Use the project's reviewer/tester skills for their scoped tasks.
```

Do not paste a huge universal checklist into every repository. Add the non-obvious rules that change decisions in this codebase.

## 4. Minimal reviewer skill

Create `.agents/skills/project-reviewer/SKILL.md`:

```markdown
---
name: project-reviewer
description: Review changes in this project for its data-integrity, integration, lifecycle, and usability risks. Use for requested code review or release audit.
---

# Project reviewer

Read the diff, affected call sites, existing instructions, and validation evidence.

Trace the changed feature across its actual boundaries. Check:
- [Project-specific invariant and the files that implement it.]
- [Project-specific failure/cancellation/retry risk.]
- [Compatibility or migration risk.]
- [Relevant UI/accessibility states, if applicable.]

Produce concrete findings with severity, file/line, reproduction, and impact.
Distinguish proven defects from environment-dependent risks. Do not claim an
independent review unless a separate reviewer actually performed one.
Do not mutate real external accounts merely to review a patch.
```

Replace the bracketed items before using it. A skill folder does not spawn a reviewer agent. Optional `agents/openai.yaml` may describe its appearance/tool dependencies, but is not needed for a minimal instruction-only skill.

## 5. Minimal tester skill

Create `.agents/skills/project-tester/SKILL.md`:

```markdown
---
name: project-tester
description: Run and extend the project's regression tests, UI verification where relevant, and build checks. Use after fixes or for requested validation.
---

# Project tester

Use the verified project check command: [command].

Relevant suites and boundaries:
- [Pure logic suite and invariant.]
- [Contract/integration suite and invariant.]
- [UI or platform test, if relevant.]

For a new defect, capture a reproducing case and add meaningful regression
coverage. Use controlled/local fixtures rather than live external accounts.

Inspect generated reports and affected UI output. Report the command, counts,
failures, artifact paths, and gaps. A launched external action is not proof
that the final external outcome occurred.

Do not hide failures, silently skip unsupported checks, or label device-only
behavior passed without device evidence.
```

Validate the frontmatter and referenced paths. When an installed skill creator provides a validator, use it according to its instructions; also try a realistic task to assess whether the skill is useful.

## 6. One deterministic check entry point

Example Bash skeleton—not runnable until its placeholder functions are implemented:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Define these functions using the project's actual existing commands.
run_fast_checks() {
  echo "Configure the project's fast checks first" >&2
  return 1
}
run_full_checks() {
  echo "Configure the project's full checks first" >&2
  return 1
}

case "${1:-full}" in
  quick) run_fast_checks ;;
  full)  run_full_checks ;;
  *) echo "Usage: $0 [quick|full]" >&2; exit 2 ;;
esac
```

Possible command mappings; inspect the repository before choosing:

| Stack | Examples to adapt |
| --- | --- |
| This Android project | Quick: `./gradlew testDebugUnitTest`; full adds `lintDebug assembleDebug`; see the real `scripts/check.sh` for environment setup |
| JavaScript/TypeScript | Existing package scripts for tests, lint, type checking, and build; do not assume their names |
| Python | The project's environment/runner with its test, lint, and type-check configuration; `python -m pytest` only when pytest is actually used |
| Go | Existing build conventions; commonly `go test ./...`, `go vet ./...`, and the appropriate build target |

Keep environment setup explicit. Do not store tokens in the script, install arbitrary tools on every commit, or make a test pass by weakening assertions.

## 7. Git hook templates

Use an existing hook manager if the repository already has one. Otherwise, after implementing `scripts/check.sh`, these are small examples.

`.githooks/pre-commit`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git diff --cached --check
scripts/check.sh quick
```

`.githooks/pre-push`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
scripts/check.sh
```

Inspect any existing hook configuration before intentionally installing these:

```sh
git config --local --get core.hooksPath || true
chmod +x scripts/check.sh .githooks/pre-commit .githooks/pre-push
git config --local core.hooksPath .githooks
bash -n scripts/check.sh .githooks/pre-commit .githooks/pre-push
```

Test the scripts against a disposable fixture that deliberately fails and check that the failure is propagated. Do not manufacture a source regression in someone's working tree just to test a hook. Verify real successful checks too. These are local Git hooks, not agent-lifecycle hooks, model-based reviews, CI, or a guarantee that others cannot bypass them.

## 8. Validation record template

```markdown
# Validation

## Scope
- User outcome:
- Supported environments:
- Changes made:

## Automated evidence
| Command | Result/counts | Report/artifact |
| --- | --- | --- |
| ... | ... | ... |

## Visual evidence, if applicable
- Actual UI rendered:
- Viewports/themes/text scales:
- Files inspected:
- Problems corrected:
- Visual comparison method: manual / golden diff / other.

## External/device/manual checks
| Outcome | Evidence or remaining blocker |
| --- | --- |
| ... | ... |

## Boundaries
- Tests not run and why:
- Known compatibility limits:
- Real account mutations performed (or explicitly none):
- Generated artwork used (or explicitly none):
- Separate reviewers/subagents used (or explicitly none):
```

The record should agree with actual reports, not a planned test matrix. Keep reproducible commands and limitations in source; decide separately whether generated evidence belongs in version control.

## 9. Definition of done

- [ ] Acceptance criteria are observable, not just “looks fixed.”
- [ ] Relevant behavior has regression coverage and all required checks pass.
- [ ] Changed UI/assets were inspected in the actual target context.
- [ ] Persistent instructions/skills describe real, repeated project knowledge.
- [ ] Hook scripts are executable, fail correctly, and do not auto-edit source.
- [ ] No secrets, build caches, signing keys, or unrelated changes are staged.
- [ ] Remaining real-environment checks are explicit.
- [ ] A commit/push is performed only when requested, and its result is reported.
