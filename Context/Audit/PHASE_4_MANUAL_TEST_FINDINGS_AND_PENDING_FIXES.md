TOTALITY — PHASE 4 MANUAL TEST FINDINGS AND PENDING FIXES
(Checkpoint document, 2026-07-14. Recorded ahead of a usage reset —
implementation code was NOT modified while producing this document. The
full correction prompt will be supplied after the reset; this file is
the handoff record for that pass, not the fix itself.)

================================================================================
CHECKPOINT CONTEXT
================================================================================
Git commit: efd89f2 ("Phase 4 checkpoint: Trading Screen redesign +
Provisioner textures (pre-correction-pass)") — a clean snapshot of all
Phase 4 implementation work, committed specifically so this session can
resume cleanly after the usage reset. `gradlew compileJava` succeeds
against this commit (confirmed at checkpoint time, UP-TO-DATE build).

Do NOT begin the Phase 4 correction pass until the full correction
prompt is supplied after the reset. Do NOT build
`Context/Audit/Review Bundles/TOTALITY_PHASE_4_REVIEW_BUNDLE.zip` until
the correction pass is complete.

================================================================================
CONFIRMED WORKING (Stefan's manual test)
================================================================================
  - Male/female Provisioner textures work, after Stefan corrected the
    filenames himself.
  - Dialogue -> Trade works.
  - Merchant name and both Credit balances (merchant + player) display
    correctly using ₵.
  - BUY quantity respects stock, affordability, and the 100 cap.
  - BUY removes the exact selected quantity from stock.
  - SELL supports multiple units from one selected stack.
  - BUY and SELL both update balances correctly.
  - BUYBACK is visibly disabled.
  - Closing the screen releases the NPC lock.
  - Different Provisioners have independent stock and Credits.
  - Iron-tool and Healing-Potion assortment chances work.

================================================================================
UNRESOLVED ISSUES — TO FIX IN THE CORRECTION PASS
================================================================================

--------------------------------------------------------------------------------
CRITICAL
--------------------------------------------------------------------------------
  1. Provisioners disappear after the player dies or moves far away.
     Dedicated Provisioners must be persistence-required and must NOT
     distance-despawn — a generated Provisioner's rolled identity,
     assortment, and Credits are meant to be permanent for that NPC
     instance, not lost to ordinary despawn rules meant for generic
     mobs.

  2. A player without an opened bank account receives SELL payout into
     the Wallet/account balance instead of physical Credits. This is
     backwards for a player who has no account yet — the payout must
     reach them as something they can actually hold/use before an
     account exists.

--------------------------------------------------------------------------------
GUI
--------------------------------------------------------------------------------
  3. The current GUI has overlapping and cramped controls at GUI scale 4.
  4. BUY needs explicit SOLD OUT text/state (current sold-out
     presentation is insufficient/unclear).
  5. Rejected SELL items need clear hover/click reasons (the current
     inventory-grid dimming alone isn't enough feedback).
  6. The next GUI revision should use, in preference order:
       Preferred: trade_screen_buy_v1, trade_screen_sell_v1
       Fallback:  trade_screen_buy_v2, trade_screen_sell_v2

--------------------------------------------------------------------------------
SEPARATE COMBAT ISSUE (unrelated to the Economy/Provisioner work)
--------------------------------------------------------------------------------
  7. Power Attack activates while holding left click to chop blocks. It
     should require a valid combat-entity target and must not consume
     Stamina while looking at blocks or air.

================================================================================
NEXT STEPS
================================================================================
The full correction prompt covering the issues above will be supplied
after the usage reset. Do not begin implementing fixes from this
document alone — treat it as the recorded findings/handoff, not the
work order.
