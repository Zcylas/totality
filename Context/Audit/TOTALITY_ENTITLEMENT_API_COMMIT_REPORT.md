# TOTALITY — Entitlement API R4 Commit Report

**Date:** 2026-10-01
**Commit message:** `feat: implement server-authoritative Entitlement API`
**Parent:** `ed5f8cdea3a71d5327f71f75be99700930826f80` (`master`)
**Commit hash:** this report is part of the commit it describes, so it cannot contain its own hash. It is the commit on `master` directly after `ed5f8cd` with the message above (`git log -1 --grep "server-authoritative Entitlement API"`).
**Pushed:** no — local commit only.

## What the commit contains

The complete Entitlement API at review revision 4. That covers:
- the server-authoritative core;
- the Totality integrations (abilities, Class / Origin / Mastery / baseline providers, debug spell access off by default, Bank app, legacy migrations, commands);
- the revision-2 review corrections (fail-safe sources, domain enumeration, the progression context, the client lifecycle, invalidation);
- the R3 and R4 flight fixes, including Creative/Spectator: `PlayerMovementComponent.endBiologicalFlight()` and the Spectator exclusion in `StaminaServerTick`;
- unit, source-regression and live-world tests;
- the implementation report.

Full design, reconciliation and limitations: `Context/Audit/TOTALITY_ENTITLEMENT_API_IMPLEMENTATION_REPORT.md` (§0 corrections, §0.6–0.7 flight follow-ups, §10 verification, §11 limitations).

**Pre-commit state check:** the working tree was compared byte-for-byte with `Context/Audit/Review Bundles/TOTALITY_ENTITLEMENT_API_REVIEW_R4.zip`. All 108 task files were identical, with no extra or missing files. No patches were re-applied.

## Files included (109)

**Modified (33):**
- `src/main/java/zcylas/totality/Totality.java`
- `src/main/java/zcylas/totality/TotalityClient.java`
- `src/main/java/zcylas/totality/api/ability/AbilityComponent.java`
- `src/main/java/zcylas/totality/api/ability/AbilityRegistry.java`
- `src/main/java/zcylas/totality/api/ability/AbilityServerTick.java`
- `src/main/java/zcylas/totality/api/ability/impl/PhysiologyPassive.java`
- `src/main/java/zcylas/totality/api/ability/impl/VeinminerAbility.java`
- `src/main/java/zcylas/totality/api/core/movement/PlayerMovementComponent.java`
- `src/main/java/zcylas/totality/api/quest/QuestManager.java`
- `src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java`
- `src/main/java/zcylas/totality/api/rpg/classes/barbarian/BarbarianClass.java`
- `src/main/java/zcylas/totality/api/rpg/resources/verification/CrownOfStarsActiveInstanceActionVerification.java`
- `src/main/java/zcylas/totality/api/rpg/resources/verification/StandardSpellSlotMigrationVerification.java`
- `src/main/java/zcylas/totality/api/rpg/skills/core/OneHandedSkillHandler.java`
- `src/main/java/zcylas/totality/api/rpg/skills/core/PlayerSkillsComponent.java`
- `src/main/java/zcylas/totality/entity/magic/FireballProjectileEntity.java`
- `src/main/java/zcylas/totality/entity/magic/SpellBoltEntity.java`
- `src/main/java/zcylas/totality/init/TotalityClientSyncListeners.java`
- `src/main/java/zcylas/totality/init/TotalityCommands.java`
- `src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java`
- `src/main/java/zcylas/totality/networking/ability/ActivateAbilityHandler.java`
- `src/main/java/zcylas/totality/networking/ability/ClientAbilityManager.java`
- `src/main/java/zcylas/totality/networking/ability/EquipAbilityHandler.java`
- `src/main/java/zcylas/totality/networking/ability/SelectSpellHandler.java`
- `src/main/java/zcylas/totality/networking/ability/ToggleAbilityHandler.java`
- `src/main/java/zcylas/totality/networking/ancestry/SelectAncestryHandler.java`
- `src/main/java/zcylas/totality/networking/classes/SelectClassHandler.java`
- `src/main/java/zcylas/totality/networking/movement/MovementStaminaHandler.java`
- `src/main/java/zcylas/totality/networking/skills/UnlockMasteryHandler.java`
- `src/main/java/zcylas/totality/networking/stamina/StaminaServerTick.java`
- `src/main/java/zcylas/totality/screen/character/tabs/AbilitiesTab.java`
- `src/main/java/zcylas/totality/screen/character/tabs/SpellsTab.java`
- `src/main/java/zcylas/totality/screen/phone/PhoneAppGridScreen.java`

**Created — production (60):**
- `src/main/java/zcylas/totality/api/entitlement/AuthorizationPath.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementActions.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementAuditEntry.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementAuthorizationPolicy.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementCatalog.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementChange.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementClock.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementComponents.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementDecision.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementDefinition.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementDisplaySnapshot.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementDisplayState.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementEngine.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementEvents.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementGrant.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementGrantProvider.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementKey.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementLedger.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementMutationResult.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementPolicies.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementQueryContext.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementReasons.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementRetentionPolicy.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementRuleSet.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementService.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementSnapshot.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementStateContributor.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementSuspension.java`
- `src/main/java/zcylas/totality/api/entitlement/EntitlementTypeDefinition.java`
- `src/main/java/zcylas/totality/api/entitlement/GrantLifetime.java`
- `src/main/java/zcylas/totality/api/entitlement/GrantSourceRef.java`
- `src/main/java/zcylas/totality/api/entitlement/GrantSourceTypes.java`
- `src/main/java/zcylas/totality/api/entitlement/OrphanedEntitlementRecord.java`
- `src/main/java/zcylas/totality/api/entitlement/PermanentEntitlementFact.java`
- `src/main/java/zcylas/totality/api/entitlement/PermanentFactRecord.java`
- `src/main/java/zcylas/totality/api/entitlement/PlayerEntitlementComponent.java`
- `src/main/java/zcylas/totality/api/entitlement/PlayerEntitlementState.java`
- `src/main/java/zcylas/totality/api/entitlement/ProgressionContext.java`
- `src/main/java/zcylas/totality/api/entitlement/client/ClientEntitlementView.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/AbilityEntitlements.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/AbilitySelectionContributor.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/AncestryEntitlementGrantProvider.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/BaselineAbilityGrantProvider.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/ClassEntitlementGrantProvider.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/DebugSpellAccessProvider.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/EntitlementCommands.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/EntitlementLiveVerification.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/LegacyAbilityMigration.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/MasteryAbilityGrantProvider.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/PhoneAppEntitlements.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/TotalityConditionTypes.java`
- `src/main/java/zcylas/totality/api/entitlement/integration/TotalityEntitlements.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/DisclosurePolicy.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/DisplayRequirementSummary.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/EntitlementConditionDefinition.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/EntitlementConditionType.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/EntitlementDependencyKey.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/EntitlementRequirement.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/RequirementEvaluation.java`
- `src/main/java/zcylas/totality/api/entitlement/requirement/RequirementEvaluator.java`

**Created — tests (14):**
- `src/test/java/zcylas/totality/api/ability/impl/PhysiologyFlightRevocationTest.java`
- `src/test/java/zcylas/totality/api/entitlement/DomainEnumerationAndInvalidationTest.java`
- `src/test/java/zcylas/totality/api/entitlement/EntitlementDecisionTest.java`
- `src/test/java/zcylas/totality/api/entitlement/EntitlementIdentityAndCatalogTest.java`
- `src/test/java/zcylas/totality/api/entitlement/EntitlementPersistenceLifecycleTest.java`
- `src/test/java/zcylas/totality/api/entitlement/EntitlementSecurityTest.java`
- `src/test/java/zcylas/totality/api/entitlement/EntitlementTestFixture.java`
- `src/test/java/zcylas/totality/api/entitlement/GrantProvenanceTest.java`
- `src/test/java/zcylas/totality/api/entitlement/ProgressionContextTest.java`
- `src/test/java/zcylas/totality/api/entitlement/RequirementEngineTest.java`
- `src/test/java/zcylas/totality/api/entitlement/SafeFailureTest.java`
- `src/test/java/zcylas/totality/api/entitlement/client/ClientEntitlementViewTest.java`
- `src/test/java/zcylas/totality/api/entitlement/integration/EntitlementIntegrationTest.java`
- `src/test/java/zcylas/totality/api/entitlement/integration/EntitlementWiringSourceRegressionTest.java`

**Created — documentation (2):**
- `Context/Audit/TOTALITY_ENTITLEMENT_API_IMPLEMENTATION_REPORT.md`
- `Context/Audit/TOTALITY_ENTITLEMENT_API_COMMIT_REPORT.md` (this report)

## Final verification (run immediately before committing)

| Check | Result |
|---|---|
| `./gradlew cleanTest build` | BUILD SUCCESSFUL — 2,351 tests, 0 failures, 0 errors, 2 skipped; all 228 result files freshly written. A plain `./gradlew build` was up to date, so `cleanTest` forced the tests to re-run. |
| `./gradlew runVerificationServer` (verbose, disposable world) | All 28 live suites passed, including `EntitlementLiveVerification` 42/42; stopped gracefully with `stop` via stdin ("Stopping server"), BUILD SUCCESSFUL, exit 0 |

## Not included — unrelated changes left untouched

These existed before the Entitlement work and remain uncommitted, unmodified and unstaged:
- `D Context/References/TOTALITY_MASTER_v3.6_SECTIONS_26L_26M_EXTRACT.md`
- `?? Context/References/Anime Screenshots/`
- `?? Context/References/Mob Hud V1/`
- `?? Context/References/Notification API V2/`
- `?? Context/References/Other/`
- `?? Context/References/STEFAN_TOTALITY_MASTER_REFERENCE_v3.8_final_audited.docx`

Review bundles under `Context/Audit/Review Bundles/` are git-ignored and are not part of the commit.

## Outstanding limitations and deferred work

See §11 of the implementation report. In short:
- No per-menu view subscriptions or open-service session tokens.
- No datapack definition loading.
- No Codex / Technology / Cooking / Research / equipment / transformation integrations (only extension contracts).
- Vanilla advancements and XP orbs from debug-cast kills are not suppressed.
- The M11 cleanup of compatibility data is deferred.

Open owner decisions:
- Multiclassing into Barbarian now grants Rage.
- Whether the Bank app requires an open account (`has_account`).
- Whether spells should keep earning One-Handed XP.
- Whether Creative/Spectator biological flight with a valid source should still block stamina regen.

Universal spell access stays off by default.
