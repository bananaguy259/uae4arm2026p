# Amiberry Upstream Sync

## Source Of Truth

- Upstream repository: `https://github.com/BlitterStudio/amiberry.git`
- Upstream remote name: `upstream`
- Tracked upstream branch: `master`
- Comparison helper: `scripts/git/compare-amiberry-upstream.ps1`

## Current Status

Verified on 2026-04-20.

| Field | Value |
|------|-------|
| Upstream branch head | `9e55746655cc947e7cfe31d81dc8262a2ed7b2c2` |
| Local branch checked | `main` |
| Relationship to upstream | Disconnected histories |
| Merge base | None |
| Raw `git rev-list --left-right --count HEAD...upstream/master` | `23 7844` |

## What Disconnected Histories Means

This repository is not a normal git fork of Amiberry. Even though the source tree is derived from Amiberry, the current git history does not share a merge base with `upstream/master`.

Because of that:

- `ahead` and `behind` counts are raw commit counts across unrelated histories, not fork-style sync numbers
- `git merge upstream/master` is not a clean future-sync model
- Future upstream updates should be tracked as explicit snapshot imports tied to a specific Amiberry commit

## Required Workflow For Future Syncs

1. Refresh upstream state:

```powershell
git fetch upstream --prune
pwsh ./scripts/git/compare-amiberry-upstream.ps1 -Fetch
```

2. Create a dedicated sync branch named after the imported upstream commit:

```powershell
git switch -c sync/amiberry-<shortsha>
```

3. Import the upstream snapshot as a single traceable commit with a message in this format:

```text
Import Amiberry upstream <fullsha>
```

4. Apply UAE4Arm-specific fixes and product-layer adjustments as follow-up commits, not mixed into the raw import commit.

5. Update the ledger below with the imported Amiberry commit and the corresponding UAE4Arm commit.

## Sync Ledger

| Date | Amiberry commit | UAE4Arm commit | Notes |
|------|-----------------|----------------|-------|
| 2026-04-20 | `9e55746655cc947e7cfe31d81dc8262a2ed7b2c2` | Pending next sync import | Upstream remote added and comparison model documented |