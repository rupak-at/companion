# Agent Working Agreement

These rules apply to every automated agent working in this repository.

## Preserve a reversible history

- Inspect `git status` before editing and preserve changes that were already present.
- Make one small, coherent change at a time. Do not mix unrelated work in one commit.
- After each coherent change, run the most relevant available checks.
- Stage only files that belong to that change. Never use broad staging commands such as
  `git add .` or `git add -A`; list each path explicitly.
- Review the staged diff with `git diff --cached` before committing.
- Commit every completed change before beginning the next one. Use a concise commit
  message that explains the result (for example, `feat: add draggable overlay`).
- Do not amend, squash, rebase, reset, force-push, or otherwise rewrite existing history
  unless the user explicitly requests it.
- Do not commit a change when its relevant checks fail. Record unfinished work clearly
  and fix it in a follow-up commit.
- Finish each task with a clean working tree, except for intentionally ignored local files.

## Protected local files

- Never use `git add -f` to bypass ignore rules for protected files.
- Never commit credentials, signing keys, API secrets, machine-specific SDK paths, or
  generated build output.

## Project boundaries

- Build V1 in the milestone order defined by the product brief, beginning with a static
  Android overlay and proving the floating interaction before weather integration.
- Keep context and state-selection logic independent of Android UI code and cover it with
  unit tests.
- Prefer approximate location, graceful offline fallbacks, low-frequency background work,
  and low battery use.
- Keep V2/V3 ideas out of V1 unless the user explicitly expands the scope.
- Treat premium, clean presentation as a product requirement: use a restrained warm palette,
  generous spacing, clear type hierarchy, subtle depth, and one dominant action per screen.
  Avoid default-looking component collections, visual clutter, heavy borders, and excessive
  color or motion.
