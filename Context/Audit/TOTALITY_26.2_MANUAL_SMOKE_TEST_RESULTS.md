# Totality — MC 26.2 Migration: Manual Smoke Test Results

Performed by: **Stefan (Zcylas)**, manually, in the development client, after the automated migration
work (compile/build/datagen/dedicated-server/bounded-client testing) completed.
Date: 2026-07-16.

Companion to `TOTALITY_26.2_MIGRATION_REPORT.md` — see that report for the full API migration
table, build results, and deferred-issues list. This document lists only the itemized manual
test results referenced from §15.8 of that report.

| # | Test | Result | Performed by | Automated / Manual | Evidence / observation | Deferred follow-up |
|---|---|---|---|---|---|---|
| 1 | Representative Totality GUIs open/close without errors | Passed | Stefan | Manual | No reported errors during normal play | — |
| 2 | Screen transitions during normal play | Passed | Stefan | Manual | No reported wrong-screen flashes or stuck states | Full screen-transition matrix not exhaustively tested (§15.5.3 of report) |
| 3 | Totality HUD hides/reshows with F1 | Passed | Stefan | Manual | Confirmed HUD elements disappear/reappear correctly | — |
| 4 | Provisioner BUY | Passed | Stefan | Manual | Confirmed working | — |
| 5 | Provisioner SELL | Passed | Stefan | Manual | Confirmed working | — |
| 6 | Banking transactions (Bank Teller) | Passed | Stefan | Manual | Confirmed working | — |
| 7 | Rest — start | Passed | Stefan | Manual | Confirmed working | — |
| 8 | Rest — cancel | Passed | Stefan | Manual | Confirmed working | — |
| 9 | Rest — complete | Passed | Stefan | Manual | Confirmed working | — |
| 10 | Dual-wield offhand swing animation | Passed | Stefan | Manual | Confirmed working after the `ItemInHandRendererMixin` retarget (§15.3) | — |
| 11 | Blocking arm pose (incl. offhand) | Passed | Stefan | Manual | Confirmed working after the same Mixin retarget | — |
| 12 | Veinminer (migrated ore-tag handling) | Passed | Stefan | Manual | Confirmed working with `ModTags.VANILLA_*` passthrough tags | — |
| 13 | Energy machine functionality (tested subset) | Passed | Stefan | Manual | Confirmed operational | — |
| 14 | Energy side overlay renders | Passed | Stefan | Manual | Confirmed rendering after the GPU-pipeline rewrite (§15.3) | Not independently stress-tested with 4+ pinned overlays in one frame (same latent ring-buffer pattern as Heat Vision, fixed preemptively — §15.6b) |
| 15 | Electric Furnace interaction and screen | Passed | Stefan | Manual | Confirmed working | — |
| 16 | Fluid Tank functionality and screen | Passed | Stefan | Manual | Confirmed working | Visual quality is a known pre-existing issue, not a migration regression (§15.6e) |
| 17 | Reconnect | Passed | Stefan | Manual | Confirmed working | — |
| 18 | Persistent state after reconnect (character, economy, equipment, quest) | Passed | Stefan | Manual | Confirmed present and correct after reconnect | — |
| 19 | Heat Vision — activation | **Failed → Fixed** | Stefan (found); Claude (root-caused/fixed); Stefan (retested) | Manual | Crashed the client on first activation (`IllegalStateException: Cannot wait on a fence for the current submit`); root-caused as a GPU ring-buffer fence violation, fixed by batching draws, retested clean — see `TOTALITY_26.2_HEATVISION_CRASH_REPORT.txt` and `TOTALITY_26.2_POSTFIX_MANUAL_TEST_CLIENT_LOG.log` | Visual appearance regressed after the fix (§15.6c) — deferred to a dedicated renderer rework |
| 20 | Heat Vision — sustained/repeated use | Passed | Stefan | Manual | No further crash after the fix | Same visual-regression caveat as #19 |
| 21 | Ability/spell hold-to-open-radial vs. hold-to-channel | **Found — not fixed** | Stefan | Manual | Holding Ability/Spell keys opens the respective radial menu even for abilities/spells that want the hold gesture for channeling instead | Deferred — input-system design issue (§15.6d), not solved in this migration |

## Summary

- **21 manual checks performed**, 19 straightforwardly passed, 1 crashed-then-fixed-and-reconfirmed (Heat Vision activation/sustain), 1 identified as a separate pre-existing design gap (hold-input conflict).
- No automated test suite covered any of the above — all 21 rows are manual, human-driven verification. Compilation, build, datagen, dedicated-server startup, and bounded non-interactive client launches were automated and are reported separately in the main migration report.
- Two follow-up items are explicitly deferred, not resolved, and do not block migration completion: the Heat Vision visual regression (§15.6c) and the ability/spell hold-input design gap (§15.6d).
- The Fluid Tank's visual quality issue (row 16) is confirmed pre-existing and unrelated to this migration (§15.6e).
