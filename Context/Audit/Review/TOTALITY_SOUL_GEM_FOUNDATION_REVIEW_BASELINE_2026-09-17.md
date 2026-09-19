# Soul Gem Foundation — Baseline Snapshot (2026-09-17, before any implementation edits)

## Branch / HEAD

- `git branch --show-current` → `feature/food-system`
- `git rev-parse HEAD` → `5ea2bc6d087db1d30134c81643b1909f1aead564`
- Matches the task's expected values exactly. `git log -1 --oneline` confirms the last commit is
  `feat(food): add authoritative food system and totality food items`.

## Dirty tree at session start

`git status --porcelain` returned **111** lines, not the expected 109. Breakdown by status code:

| Code | Count | Meaning |
|---|---|---|
| ` M` | 42 | unstaged modification, nothing staged |
| `??` | 67 | untracked |
| `A ` | 2 | staged addition, no further worktree change |

42 + 67 = 109, matching the documented post-Food baseline exactly. The extra 2 are the two newly
staged files below — **unexpectedly staged**, not something this session did.

## Unexpectedly staged content found at baseline

```
git diff --cached --stat
 .../totality/models/item/common_soul_gem.json      | 113 +++++++++++++++++++++
 .../totality/textures/item/common_soul_gem.png     | Bin 0 -> 5072 bytes
 2 files changed, 113 insertions(+)
```

Both are the user's new Common Soul Gem model/texture. This matches the exact same
external-tool auto-staging anomaly observed repeatedly during the Food task (the recurring
`pizza_margherita.png` staging behavior) — some external tool/editor appears to re-save-and-stage
newly added asset files. Per the established convention from those prior sessions, this was
**safely unstaged** via `git restore --staged` (non-destructive — the working-tree file content is
untouched, only the index entry is cleared) immediately after recording this baseline, so the
Soul Gem work in this task starts from a clean, fully-unstaged index and the final "nothing should
be staged" safety check has a true, non-anomalous baseline to compare against.

## Petty / Common Soul Gem files already present

Found via `find . -iname "*soul_gem*"`:

| File | Git status | Notes |
|---|---|---|
| `Context/Audit/TOTALITY_PETTY_SOUL_GEM_IMPLEMENTATION_REPORT_2026-09-15.md` | `??` untracked | Prior session's report: Petty registered 2026-09-15 as a **plain `Item`** (no custom class, no component, no gameplay) in `MagicItems.java` |
| `src/main/generated/assets/totality/items/petty_soul_gem.json` | `??` untracked | Datagen output for Petty |
| `src/main/resources/assets/totality/models/item/petty_soul_gem.json` | `??` untracked | User's hand-authored Petty model — texture ref already correct (`totality:item/petty_soul_gem`) |
| `src/main/resources/assets/totality/textures/item/petty_soul_gem.png` | `??` untracked | User's Petty texture |
| `src/main/resources/assets/totality/models/item/common_soul_gem.json` | `A ` staged (see above) | User's hand-authored Common model — texture ref already correct (`totality:item/common_soul_gem`), no correction needed |
| `src/main/resources/assets/totality/textures/item/common_soul_gem.png` | `A ` staged (see above) | User's Common texture |

Current registration state (`MagicItems.java`): `PETTY_SOUL_GEM` is registered as a plain `Item`
(`Item::new`) with `ItemRarity.COMMON`, `ItemType.MAGICAL`, lore, and `TooltipProfileComponent`.
No `COMMON_SOUL_GEM` registration exists yet. No `SoulGemItem` class, no `CapturedSoul`/component,
no `SoulCategory`, no acceptance-rule architecture, no capture service exist anywhere in the
codebase yet — this task starts from a genuinely blank slate for all Soul Gem *behavior*.

## MobRank — current state (pre-change)

`src/main/java/zcylas/totality/api/mob/stats/MobRank.java`:

```java
public enum MobRank {
    E(0xFF888888), D(0xFF44AA44), C(0xFF4488CC), B(0xFFAA44CC), A(0xFFCCAA00), S(0xFFCC4400);
    ...
}
```

Six ranks (E–S), no F, no Z, no Rank 0. Full consumer audit — see the implementation report for
detail; summary:

- `MobStatBlock.getFixedRank()` — **name/string-based** (`MobRank.valueOf(rank.toUpperCase())`,
  catch → `MobRank.E`). Safe against reordering; only sensitive to renamed/removed constants.
- `MobCombatStats` — stores the resolved `MobRank`, and **encodes `this.rank.ordinal()`** into
  `MobStatsSyncPayload` for network broadcast. **Ordinal-dependent (risk).**
- `MobStatsSyncPayload` / `MobStatsClientCache` — carry the ordinal as a raw `int` (`rankOrdinal`)
  over the wire and into the client-side cache. Transient (rebuilt fresh every broadcast/tick, never
  persisted to disk), but still fragile to enum-declaration-order changes.
- `MobHealthBarHud.buildName()` — **decodes via `MobRank.values()[Math.min(rankOrdinal, ...)]`**.
  **Ordinal-dependent (risk).**
- `TotalityHudCleanupSourceRegressionTest` (line ~546) — source-regression guard asserting the
  literal string `"MobRank.values()["` is present in `MobHealthBarHud.java`. This test will need to
  be updated in lockstep with the ordinal fix (it currently pins the exact pattern being replaced).

No mob-stat-block JSON datapack files were found needing a rank-value change — the only fallback
literal is the `"E"` default already baked into `MobStatBlock`.

## Mixed-content risk files identified before editing

- `src/main/java/zcylas/totality/init/items/MagicItems.java` — already dirty vs. HEAD (unrelated,
  since `MagicItems.register()` and other Magic-tab items are still uncommitted from earlier
  sessions); the Petty conversion + Common registration will land inside this same already-dirty
  file. **Not a staging concern for this task** (no commit happens), but noted for the review ZIP,
  which must include this file's relevant diff/section clearly.
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java`, `.../ModEnglishLangProvider.java`,
  `src/main/java/zcylas/totality/init/ModGroups.java` — all three are already dirty vs. HEAD with a
  large amount of unrelated in-progress content (Iron Sword model, Soul Gem's own prior Petty
  wiring, various lang entries, other creative-tab items). The Soul Gem edits in this task will add
  a few lines to each. Again, not a staging concern (no commit), but the review ZIP will include
  full files rather than a partial patch for clarity, per the task's own instruction for mixed files.
- `src/main/java/zcylas/totality/Totality.java` — **clean at baseline** (unlike the files above); this
  task adds exactly two `register()` call lines for the new Soul Gem component/verification classes,
  so its resulting diff is entirely this task's own, not mixed.

No `git reset --hard`, `git clean`, `git checkout -- .`, or `git restore .` (whole-tree) was used at
any point. Only `git restore --staged <2 specific paths>` (index-only, non-destructive) to correct
the unexpected pre-existing staged state described above.
