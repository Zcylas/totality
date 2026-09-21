# Totality — Branch Integration Audit (2026-09-21)

**READ-ONLY audit.** Nothing was merged, rebased, cherry-picked, committed, staged, pushed, reset or deleted; no ref was created, moved or removed. Commands used: `git status`, `git branch -a -vv`, `git for-each-ref`, `git log --graph --decorate --oneline --all`, `git merge-base`, `git merge-base --is-ancestor`, `git rev-list --count`, `git log a..b`, `git diff --name-only/--shortstat`, `git reflog --all`, `git fsck --unreachable --no-reflogs`, `git ls-tree`, `git ls-remote --heads origin` (network read, no ref change). Repository: `Zcylas/totality`. Current branch: `feature/block-breaking-api` (uncommitted work; see §6).

Recommendation states: **MERGE** · **ALREADY INCLUDED** · **SUPERSEDED / DO NOT MERGE** · **EXPERIMENT / DO NOT MERGE** · **NEEDS MANUAL REVIEW**.

---

## 1. Executive summary

1. **The history is one straight line.** Every feature branch that is not already in `master` is a strict ancestor of the next one: `master ⊂ general-resource-api(origin) ⊂ general-resource-api(local) ⊂ food-system ⊂ soul-gems`. **`feature/soul-gems` (tip `3170e2f`) is the latest superset of everything**: 53 commits ahead of `master`, 0 behind, and it contains the generic Player Resource API, Food 0–100, classes/subclass work and the Soul Gem foundation.
2. **`master` can be brought fully up to date with a fast-forward** to `feature/soul-gems` — no conflicts are possible at that step. Merging `general-resource-api`, `food-system` and `soul-gems` separately would only be three redundant no-ops; do not.
3. `migration/minecraft-26.2` and `feature/provisioner-phase-4` are **already in `master`** (merge commits `a4503f9` and `bc16cc3`).
4. **All the real conflict work is on the Block Breaking side.** `feature/block-breaking-api` was cut from `master` (`bc16cc3`), so it is 53 commits behind `soul-gems`. Five tracked files that Block Breaking modifies are also modified on `soul-gems` (§5). Every Block Breaking *new* file is collision-free.
5. **No rubble / physics-mining experiment branch exists** anywhere in this repository (local refs, remote refs via `ls-remote`, reflog, unreachable commits, commit messages). It cannot be identified from Git — see §4. Nothing was guessed.
6. `general-resource-api` exists locally 9 commits ahead of `origin/feature/general-resource-api`, but those 9 commits are already published because `origin/feature/food-system` contains them. Nothing is stranded.

---

## 2. Branch graph (all refs)

```
* 3170e2f  (origin/feature/soul-gems, feature/soul-gems)      chore: mark Gradle wrapper executable
  ... 22 commits: soul gem foundation, build bump, RPG fixes, sword registrations, assets, creative tabs,
      commands, lang, audits ...
* 59d0c91  feat(soul-gems): add soul gem foundation
* 5ea2bc6  (origin/feature/food-system, feature/food-system) feat(food): authoritative food system + totality food items
* b74b7a6  (feature/general-resource-api  — local only, 9 ahead of origin)  docs(resources): finalize V1 readiness audit
  ... 8 more: health recovery dice, checks fix, Crown motes, spell slots, per-class subclasses, rage, complete foundation ...
* 73380f9  (origin/feature/general-resource-api)  feat: add wearable Shinigami Robe
  ... 20 commits: Player Resource API foundation, adapters (health/food, breath, mana/stamina, spell slots, rage),
      sync contract, client parity, tooltip foundation, HUD migration, stamina depletion, dormant resources ...
*   bc16cc3  (master, origin/master, feature/block-breaking-api @ commit level)  Merge Provisioner Phase 4
|\
| * 00331e5  (feature/provisioner-phase-4, origin/…)  Add final Provisioner Phase 4 review bundle
| ... (cb27327 .. a77ed12)
|/
* 0d2612b  Track class system source packages
*   a4503f9  Merge Minecraft 26.2 migration
|\
| * c673238  (migration/minecraft-26.2, origin/…)  Migrate Totality to Minecraft 26.2
| * 46a3591  WIP: establish Minecraft 26.2 migration baseline
|/
* 953a677  Checkpoint: stable Totality 26.1.2 before 26.2 migration
```

`git ls-remote --heads origin` returns exactly six heads (`master`, `migration/minecraft-26.2`, `feature/provisioner-phase-4`, `feature/general-resource-api` @ `73380f9`, `feature/food-system`, `feature/soul-gems`), identical to the local remote-tracking refs. There is no other branch, tag, or stash.

## 3. Branch table

| Branch | Tip | Base (merge-base with `master`) | vs `master` (ahead / behind) | Unique commits (vs its predecessor) | Contains | Already elsewhere? | Canonical / superseded | Recommendation |
|---|---|---|---|---|---|---|---|---|
| `master` = `origin/master` | `bc16cc3` | — | 0 / 0 | — | 26.2 migration, class-system source, Provisioner Phase 4, Trading Screen | — | current *published* baseline, **stale**: 53 commits behind soul-gems | **fast-forward target** (the unified line) |
| `migration/minecraft-26.2` (+origin) | `c673238` | `c673238` | 0 / 9 | 0 (ancestor of `master`) | Minecraft 26.2 port | yes — merged by `a4503f9` | superseded by `master` | **ALREADY INCLUDED** |
| `feature/provisioner-phase-4` (+origin) | `00331e5` | `00331e5` | 0 / 1 | 0 (ancestor of `master`) | Provisioner Phase 4, HUD/input timing fixes, review bundle | yes — merged by `bc16cc3` | superseded by `master` | **ALREADY INCLUDED** |
| `origin/feature/general-resource-api` | `73380f9` | `bc16cc3` | 21 / 0 | 21 | Generic Player Resource API foundation + adapters (health, food, breath, mana, stamina, spell slots, rage), sync contract, client parity, tooltip foundation, HUD migration, stamina depletion, dormant resources, healing potion, Asauchi model, Shinigami Robe | yes — ancestor of local `general-resource-api`, `food-system`, `soul-gems` | stale remote pointer | **ALREADY INCLUDED** (in `soul-gems`) |
| `feature/general-resource-api` (local) | `b74b7a6` | `bc16cc3` | 30 / 0 | +9 over origin tip: complete foundation + mana/stamina migration, rage migration, per-class subclasses & class-tab leveling, class-owned state reconcile, spell-slot migration, Crown motes, ability-check/saving-throw bonus split, health-recovery dice, V1 readiness audit | as above + class/resource migration | yes — the 9 are reachable from `origin/feature/food-system` (published) | intermediate | **ALREADY INCLUDED** (in `soul-gems`) |
| `feature/food-system` (+origin) | `5ea2bc6` | `bc16cc3` | 31 / 0 | +1 over resource-api: authoritative Food 0–100 + `TotalityFoodItem` | as above + Food | yes — ancestor of `soul-gems` | intermediate | **ALREADY INCLUDED** (in `soul-gems`) |
| `feature/soul-gems` (+origin) | `3170e2f` | `bc16cc3` | **53 / 0** | +22 over food-system: Soul Gem foundation, Fabric API 0.160.0 / Loader 0.19.5 bump, `log4j-dev.xml`, RPG fixes (class level, XP carry-over, per-class level-up, Rage pool mirror), sword registrations (Iron/Steel/Zanpakutō), Poison Spray fix, quest-HUD fix, Shinigami Robe armor class, Iron Sword/Platinum Coin/Asauchi assets, creative tabs, `/totality credits give`, removed debug commands, lang regen, audits, `CLAUDE.md`, `.gitignore` additions, wrapper `+x` | **everything above** | — | **latest canonical superset**; 579 files, +100,784 / −1,572 vs `master` | **MERGE** (as a fast-forward of `master`, one step) |
| `feature/block-breaking-api` | `bc16cc3` + **uncommitted** | `bc16cc3` (= `master`) | 0 commits / 0 behind `master`, but 53 behind `soul-gems` | none committed (38 files uncommitted: 10 modified, 28 new incl. 3 audit documents) | Block Breaking V1 (durability/integrity, shared persistent block damage, server mining loop, STR/DEX power & tier, cadence, Power Mining, first-person animations, Combat Text presentation) | — | current work, must be **committed first** | **MERGE** (after it is committed and after `master` is updated; second step) |
| *(rubble / physics-mining experiment)* | — | — | — | — | — | — | **not found in Git** | **NEEDS MANUAL REVIEW** (Stefan must say where it lives; **DO NOT MERGE** whatever it is) |

Orphans (unreachable, informational only — no ref points at them): `df28c13` "feat: add wearable Shinigami Robe" (2026-09-10; same subject as `73380f9`, an earlier/rewritten form — the trees differ in 2 files) and `9953647` "Checkpoint: stable Totality 26.1.2 before 26.2 migration" (2026-07-15; earlier form of `953a677`). They are pre-rewrite leftovers; `SUPERSEDED / DO NOT MERGE`. They will be garbage-collected eventually and nothing depends on them.

## 4. The rubble / mining experiment — not identifiable

Searched, all negative:
* **Refs:** local heads (7), remote-tracking refs (6), `git ls-remote --heads origin` (6). No name contains `rubble`, `physics`, `fracture`, `debris`, `mining`, `break`, `experiment`.
* **Commit messages:** `git log --all -i --grep="rubble|fracture|physics|debris|mining|break"` → nothing.
* **Reflog:** every `checkout: moving from … to …` entry involves only the seven known branches (plus the `feature/block-breaking-api` branch created this week). No deleted-branch trace.
* **Unreachable objects:** the only two are the pre-rewrite duplicates above; neither is mining related.
* **Stash:** empty. **Worktrees:** one.
* **Sibling repos** in `~/Documents/Projects/Minecraft` (`Bluthaven`, `Everything Mod`, `Zcylas's Things`) each have only `master`.
* **Text:** the word "rubble" occurs in the repository only in this week's Block Breaking documents (where rubble is listed as *deferred*). `Inspiration Mods/physics-mod-3.2.4-mc-26.2-fabric.jar` is a third-party reference JAR (git-ignored), not a branch.

**Conclusion:** the failed experiment is **not in this repository's Git history**. It may have lived in an uncommitted working tree, a different clone/folder, or a branch that was deleted before the reflog window. **Candidates requiring Stefan's confirmation: none exist to choose from — please tell us where it lives.** Regardless: it is not among the branches recorded above, so *no branch here is the rubble experiment*, and **none of the seven existing branches should be excluded on that basis**. Nothing was deleted, merged or cherry-picked.

## 5. Conflict analysis

Block Breaking files (38): 10 modified tracked files + 28 new (incl. 3 audit documents). `soul-gems` changes 579 files vs `master`.

**Text-conflict candidates — tracked files modified on BOTH sides (5):**

| File | Block Breaking change | soul-gems change | Resolution guidance |
|---|---|---|---|
| `gradle.properties` | `fabric_api_version=0.161.0+26.2` (intentional) | `fabric_api_version=0.160.0+26.2`, `loader_version=0.19.5` | keep **0.161.0+26.2** and take soul-gems' loader **0.19.5**; re-check JEI/dev-run resolution (a JEI jar in `run/mods` needs Fabric API ≥ 0.155) |
| `src/main/resources/totality.mixins.json` | +`mining.ServerPlayerGameModeMixin` (common), +`client.MinecraftMiningMixin`, +`client.MiningHandRenderMixin` | +11 Food/regen common mixins, +5 `client.chat.*` mixins | keep **all** entries; only the JSON list/commas conflict |
| `.../Totality.java` | +`PlayerMiningManager.register()`, +`MiningVerification.register()` | resource/food/soul-gem registrations | append-only both sides — keep both |
| `.../TotalityClient.java` | +`ClientMiningController.register()` | resource HUD/client parity, soul-gem client registration | append-only both sides — keep both |
| `.../networking/TotalityPackets.java` | +`MiningIntentPayload` (C2S), +`MiningSwingPayload` (S2C) | many new resource/food payloads | append-only both sides — keep both; watch payload id uniqueness |

`general-resource-api` (origin, local), `food-system` and `soul-gems` all touch the same four Java/JSON files, so the conflicts are the same whichever intermediate branch is used; they occur once when `soul-gems`' content meets Block Breaking.

**No file-name collisions:** none of Block Breaking's 28 new files exists on `soul-gems`. Block Breaking's other modified files (`CombatTextEntry/Manager/Renderer/ClientHandler`, `CombatTextPayload`, `CodecSavedData`) are **not** modified on `soul-gems`.

**Untracked-file overwrite hazard:** the 163 untracked paths in this worktree (`.claude/`, `logs/`, 35 old review bundles, `src/main/generated/.cache/`, …) do **not** exist as tracked files on `soul-gems` (0 overlaps), so a merge will not be blocked by them; `soul-gems`' `.gitignore` additionally ignores `logs/`, `generated/.cache`, `.claude/settings.local.json`, `Context/Audit/Review Bundles/` and `Context/Audit/Review/*.zip`.

**Semantic (non-textual) risks to re-verify after merging** (compile-time API used by Block Breaking is unchanged on `soul-gems`: `StatsComponents.getStats`, `PlayerStats.getScore/getModifier/recalculate/setSpentPointsDirectly` all still exist):
1. `PlayerStats.java` (+43 lines) and `PlayerResourceRecalculator` changed: the mining tests assume BASE score 10 and `setSpentPointsDirectly(score, value − 10)`; re-run `MiningVerification` (STR/DEX cases).
2. `api/rpg/combat/*` changed in 8 files: the bare-hand **body-strain** self-damage goes through vanilla `hurtServer` and Totality's `VanillaDamageInterceptor`/`TotalityDamage` — re-test orange (2 HP) / red (6 HP) live.
3. Food authority mixins (`FoodDataExhaustionAuthorityMixin`, …) and Food 0–100: `Block.playerDestroy` adds vanilla food exhaustion on a break; confirm mining does not misbehave with the new food authority. The Food `20/20` HUD observation disappears once `soul-gems` is in (HUD reads the 0–100 resource).
4. `TotalityHudRenderer` changed on `soul-gems`; Block Breaking draws its Power meter as a separate `HudElementRegistry` element and does not touch it — verify layering.
5. Client mixins: `ItemInHandRendererMixin` (dual-wield, unchanged on soul-gems) and Block Breaking's `MiningHandRenderMixin` both target `ItemInHandRenderer`; their `@ModifyArg` ordinals (0 = main hand, 1 = off hand) must keep working — re-check dual-wield/offhand rendering.
6. `VerificationReporter`, `TotalityFakePlayer` (used by `MiningVerification`) are unchanged on soul-gems.

## 6. Current worktree state

`feature/block-breaking-api` points at `bc16cc3` (`master`). Its work is **entirely uncommitted**: 10 modified tracked files and 28 new files (`api/mining/*`, `client/mining/*`, 3 mixins, 2 mining payloads, and 3 audit documents including this one). It is unpublished (no remote branch). Because nothing is committed, it cannot be merged yet, and it cannot be lost by a fast-forward of `master` either (its base is `master`).

## 7. Proposed integration order (NOT executed)

Goal: **one unified current Totality codebase = `master`**, from which every new feature branch starts. Principle: resolve each conflict area exactly once, on the smaller side, validate after each step, never merge redundant intermediates, never rewrite published history.

**Step 0 — preparation (no merge)**
1. Stefan live-tests the corrected normal bare-hand punch and accepts Block Breaking V1.
2. Commit Block Breaking on `feature/block-breaking-api` in a few logical commits (suggestion: core API/persistence · mixins+networking · client animation · Combat Text/`CodecSavedData` · docs/audits; keep the Fabric API bump in its own commit). Confirm the tree is otherwise clean.
3. Optionally push `feature/block-breaking-api` for backup.

**Step 1 — bring `master` to the latest line**
* **Source:** `feature/soul-gems` (contains `general-resource-api` + `food-system`). **Target:** `master`.
* **Unique work brought in:** 53 commits: generic Player Resource API + adapters, Food 0–100, class/subclass system fixes, Soul Gem foundation, Fabric 0.160.0/Loader 0.19.5, HUD/tooltip foundation, assets, commands, audits.
* **Method:** fast-forward only (`git merge --ff-only`) — `master` is an ancestor, so **no conflicts are possible**.
* **Expected conflicts:** none.
* **Validate immediately:** `bash gradlew build` (wrapper is executable on this line); dev server start; dev client join a world; the existing suites (`FoodSystemVerification`, resource/`PowerAttackVerification`/`OffhandAttackVerification` as they were on `soul-gems` — note the latter two had pre-existing failures); Food HUD shows 0–100; an old save loads (resource/food migration).
* **Do NOT** separately merge `general-resource-api`, `food-system` or the `origin/` variants.

**Step 2 — bring Block Breaking onto the new `master`**
* **Source:** `master` (now = soul-gems). **Target:** `feature/block-breaking-api`. Use a merge (not a rebase) so no commits are rewritten.
* **Unique work brought in:** the 53 commits above (Block Breaking's own commits stay as they are).
* **Expected conflicts:** the five files in §5: `gradle.properties`, `totality.mixins.json`, `Totality.java`, `TotalityClient.java`, `networking/TotalityPackets.java` — all additive, keep both sides; `gradle.properties` → Fabric API **0.161.0+26.2** with Loader **0.19.5**.
* **Validate immediately:** `bash gradlew build`; `MiningVerification` (166+ checks; watch the STR/DEX cases because of `PlayerStats` changes); dev server mixin start; dev client join; live smoke of: normal tool mining, bare-hand punch and Power, orange/red strain, damage-text colours, persistence Save & Quit, Power Attack / dual-wield / shield / eating / bow rendering, Food HUD 100.

**Step 3 — publish the unified line**
* **Source:** `feature/block-breaking-api`. **Target:** `master`. Because Step 2 merged `master` into it, this is a fast-forward (or a PR merge at Stefan's preference). **No conflicts.**
* **Validate:** the same build/tests/dev-run as Step 2 on `master`.
* New feature branches start from this `master`.

**Step 4 — housekeeping (only with Stefan's approval, after Step 3)**
`migration/minecraft-26.2`, `feature/provisioner-phase-4`, `feature/general-resource-api`, `feature/food-system` and `feature/soul-gems` become pure ancestors of `master` and can then be retired; nothing should be deleted before that is verified with `git branch --merged master`. Do not delete anything as part of these steps unless explicitly asked.

**Rejected alternatives**
* *Merge Block Breaking into `master` first, then `soul-gems`:* resolves the same five conflicts on the larger side and briefly leaves `master` without the resource/food work. Worse.
* *Rebase `soul-gems` onto Block Breaking:* rewrites 53 published commits. Rejected.
* *One octopus merge of every branch:* every non-conflicting branch is an ancestor, so it is pointless and hides the five real conflicts.

## 8. Open items for Stefan

1. **Where is the rubble/physics-mining experiment?** No branch, ref, reflog entry or unreachable commit exists for it in this repo (§4). It cannot be excluded or included by name because it is not here; tell us where it lives if it must be handled.
2. Confirm that `feature/soul-gems` (not an older intermediate) is indeed the intended latest line, and that nothing exists on another machine/clone that is not in `origin` (all six remote heads are accounted for).
3. Decide commit granularity for Block Breaking (§7 Step 0) and whether Step 3 is a fast-forward or a PR merge.
