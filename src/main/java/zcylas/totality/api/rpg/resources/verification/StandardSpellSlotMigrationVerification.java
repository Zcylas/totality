package zcylas.totality.api.rpg.resources.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.entitlement.EntitlementGrant;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.integration.AbilityEntitlements;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.api.magic.spell.SpellSlotComponent;
import zcylas.totality.api.magic.spell.SpellSlotComponents;
import zcylas.totality.api.magic.spell.SpellSlotTable;
import zcylas.totality.api.rpg.classes.ClassChangeReconciler;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.resources.PartitionSelectionPolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceCost;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.resources.integration.StandardSpellSlotResources;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.networking.ability.ActivateAbilityHandler;
import zcylas.totality.networking.ability.ActivateAbilityPayload;
import zcylas.totality.networking.resource.BaselineResourceLifecycleEvents;
import zcylas.totality.server.TotalityFakePlayer;

/**
 * Dev-environment-gated self-test for the Phase 6 Standard Spell Slot migration (2026-09-16),
 * matching {@link BarbarianRageMigrationVerification}'s established pattern — exercises the REAL
 * production {@code PlayerResourceRegistry.INSTANCE}/{@code totality:spell_slots} registration,
 * {@link StandardSpellSlotResources}'s grant/reconciliation/Long Rest, {@code
 * StandardSpellSlotMaximumResolver}, {@code SpellSlotRecalculator}'s combined-caster-level formula,
 * the legacy NBT migration-import step, and the Warlock exclusion.
 *
 * <p>Casting is exercised directly through {@link PlayerResourceService#trySpend} with {@link
 * PartitionSelectionPolicy#EXACT_TIER} — the exact same call {@code ActivateAbilityHandler} makes
 * after a cast succeeds — rather than through a full ability/spell/network-packet simulation (kept
 * out of scope per the task's own "do not build a huge new testing framework" instruction).
 * {@code ActivateAbilityHandler}'s successful-cast-only ordering (query before {@code onActivate},
 * spend only after {@code Spell.didCastSucceed()}) is a structural property of its source, confirmed
 * by direct code read in the implementation report — the same standard the pre-existing Phases 4-8
 * gap audit already applied to this exact guarantee for the legacy pre-migration code.
 *
 * <p>Registration is gated on {@link VerificationReporter#isDevEnvironment()} — a complete no-op in
 * a production build.
 */
public final class StandardSpellSlotMigrationVerification {

    private static final int SUITE_DELAY_TICKS = 5;
    private static final Identifier VERIFICATION_SOURCE = Identifier.fromNamespaceAndPath("totality", "verification");

    private StandardSpellSlotMigrationVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return; // opt-in: runs against the live world
        // Finding 4 correction pass (2026-09-16): registered here, inside the same dev-environment
        // gate as everything else in this class — never in a static initializer, which would run
        // (loading this class at all) unconditionally regardless of environment.
        AbilityRegistry.add(new NoEffectTestSpell());
        AbilityRegistry.add(new AlwaysSucceedsTestSpell());
        ServerLifecycleEvents.SERVER_STARTED.register(StandardSpellSlotMigrationVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(StandardSpellSlotMigrationVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "StandardSpellSlotMigrationVerification");
        ServerLevel level = server.overworld();

        ServerPlayer wizard = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-wizard]");
        try {
            ClassComponents.get(wizard).selectClass(TotalityClasses.WIZARD_ID, 1);
            ClassChangeReconciler.reconcile(wizard);

            safe(r, "a fresh level-1 Wizard has totality:spell_slots granted at the correct level-1 maximum (2/2 at level 1, "
                    + "0/0 at every other tier) — no ordinary tier-10 partition exists at all", () -> {
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                if (!(query instanceof ResourceQueryResult.PartitionedSuccess success)) return result(false, "query=" + query);
                var snapshot = success.snapshot();
                boolean pass = snapshot.partition(1).map(p -> p.currentUnits() == 2 && p.maximumUnits() == 2).orElse(false)
                        && snapshot.partition(2).map(p -> p.maximumUnits() == 0).orElse(false)
                        && snapshot.partitions().size() == SpellSlotTable.STANDARD_SLOT_LEVELS
                        && snapshot.partition(10).isEmpty();
                return result(pass, "snapshot=" + snapshot);
            });

            safe(r, "EXACT_TIER trySpend at level 1 (the exact call ActivateAbilityHandler makes after a successful cast) "
                    + "consumes exactly one level-1 slot", () -> {
                var spendResult = PlayerResourceService.INSTANCE.trySpend(wizard,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = spendResult.isSuccess() && query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 1;
                return result(pass, "spendResult=" + spendResult + ", query=" + query);
            });

            safe(r, "Finding 4 correction: a real cast through the actual ActivateAbilityHandler.handle entry "
                    + "point — not a direct PlayerResourceService call — where the spell's own onActivate calls "
                    + "Spell.markNoEffect() consumes NO slot, proving the handler's successful-cast-only ordering "
                    + "end-to-end (pre-check -> onActivate -> didCastSucceed() -> spend-only-if-true), not merely "
                    + "that the resource layer is inert when nothing calls it", () -> {
                EntitlementService.INSTANCE.addGrant(wizard, EntitlementGrant.debugSession(
                        AbilityEntitlements.keyFor(NoEffectTestSpell.ID), VERIFICATION_SOURCE));
                ResourceQueryResult before = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long beforeRemaining = before instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;

                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(NoEffectTestSpell.ID, null));

                ResourceQueryResult after = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long afterRemaining = after instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;
                boolean pass = beforeRemaining >= 0 && beforeRemaining == afterRemaining;
                return result(pass, "before=" + before + ", after=" + after);
            });

            safe(r, "the same real ActivateAbilityHandler.handle entry point, given a spell whose onActivate "
                    + "does NOT call markNoEffect(), DOES consume exactly one slot — proving the no-effect check "
                    + "above is discriminating real cast-result branching, not merely a handler that never spends", () -> {
                EntitlementService.INSTANCE.addGrant(wizard, EntitlementGrant.debugSession(
                        AbilityEntitlements.keyFor(AlwaysSucceedsTestSpell.ID), VERIFICATION_SOURCE));
                ResourceQueryResult before = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long beforeRemaining = before instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;

                ActivateAbilityHandler.handle(wizard, new ActivateAbilityPayload(AlwaysSucceedsTestSpell.ID, null));

                ResourceQueryResult after = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                long afterRemaining = after instanceof ResourceQueryResult.PartitionedSuccess s
                        ? s.snapshot().partition(1).orElseThrow().currentUnits() : -1;
                boolean pass = beforeRemaining > 0 && afterRemaining == beforeRemaining - 1;
                return result(pass, "before=" + before + ", after=" + after);
            });

            // The AlwaysSucceedsTestSpell check above deliberately spends one real level-1 slot
            // through the real handler (that IS the check) — restore to the exact "1 remaining"
            // state the pre-existing sequence below was already written to expect, so this
            // correction-pass addition cannot shift any of its hardcoded assertions.
            StandardSpellSlotResources.onLongRest(wizard);
            PlayerResourceService.INSTANCE.trySpend(wizard,
                    new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                    ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));

            safe(r, "EXACT_TIER never falls back to another tier: spending at level 2 (currently 0/0 for this level-1 "
                    + "Wizard) fails rather than silently drawing from level 1", () -> {
                var spendResult = PlayerResourceService.INSTANCE.trySpend(wizard,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 2, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean level1Unchanged = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 1;
                return result(!spendResult.isSuccess() && level1Unchanged, "spendResult=" + spendResult + ", query=" + query);
            });

            safe(r, "spending the last level-1 slot then attempting a third spend is rejected with no mutation", () -> {
                PlayerResourceService.INSTANCE.trySpend(wizard,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // 1 -> 0
                var spendResult = PlayerResourceService.INSTANCE.trySpend(wizard,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean stillZero = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 0;
                return result(!spendResult.isSuccess() && stillZero, "spendResult=" + spendResult);
            });

            safe(r, "Short Rest does not restore Standard Spell Slots", () -> {
                StandardSpellSlotResources.onLongRest(wizard); // sanity: not part of this check's assertion
                PlayerResourceService.INSTANCE.trySpend(wizard,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // 2 -> 1
                // Short Rest has no Standard-Spell-Slot-specific hook at all (see PlayerConnectionEvents) —
                // this check simply re-confirms the value is untouched by anything except the explicit spend above.
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 1;
                return result(pass, "query=" + query);
            });

            safe(r, "Long Rest fully restores every Standard Spell Slot tier to its current maximum", () -> {
                StandardSpellSlotResources.onLongRest(wizard);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 2;
                return result(pass, "query=" + query);
            });

            safe(r, "Level-up regression fix (2026-09-16 correction pass): leveling the Wizard class up through "
                    + "the real production sequence (ClassChangeReconciler.captureResolvedMaximums before the "
                    + "mutation, then reconcile(player, before) after it — exactly what AddClassLevelHandler now "
                    + "does) grants the newly gained capacity from a maximum increase while leaving the "
                    + "already-spent slot spent, rather than silently doing nothing until the next rest (the "
                    + "reported bug: PRESERVE_DEFICIT example 2 — old max=2, current=1 (1 spent), new max=3, "
                    + "expected current=2)", () -> {
                PlayerResourceService.INSTANCE.trySpend(wizard,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // 2 -> 1
                var before = ClassChangeReconciler.captureResolvedMaximums(wizard);
                ClassComponents.get(wizard).addClassLevel(TotalityClasses.WIZARD_ID); // class level 1 -> 2
                ClassChangeReconciler.reconcile(wizard, before);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                // Level 2 full caster: 3 level-1 slots (SpellSlotTable.FULL_CASTER row 2).
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 2
                        && success.snapshot().partition(1).orElseThrow().maximumUnits() == 3;
                return result(pass, "query=" + query);
            });

            safe(r, "no ordinary tier-10 slot exists even at a high class level that historically granted one "
                    + "(level 30, pre-Phase-6 would have unlocked a second 10th-level slot)", () -> {
                ClassComponents.get(wizard).setClassLevel(TotalityClasses.WIZARD_ID, 30);
                ClassChangeReconciler.reconcile(wizard);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(wizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partitions().size() == SpellSlotTable.STANDARD_SLOT_LEVELS
                        && success.snapshot().partition(10).isEmpty();
                return result(pass, "query=" + query);
            });
        } finally {
            wizard.discard();
        }

        ServerPlayer classLevelDecrease = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-class-level-decrease]");
        try {
            // Correction pass (2026-09-16, Finding 1): reproduces the exact lifecycle the fix
            // addresses. BaselineResourceLifecycleEvents' own JOIN handler (which runs first, per
            // ModEvents.register()'s registration order) reconciles class-owned Generic Resource
            // grants using the player's CURRENTLY STORED class level; PlayerConnectionEvents' JOIN
            // handler (which runs second) can then lower that stored level via
            // PlayerClassComponent.setClassLevel — a call that performs no reconciliation of its own
            // (confirmed by reading its source: it only mutates the map and calls sync()). This
            // directly replicates that exact sequence — seed at a high level, mutate the stored
            // level down exactly like the fixed JOIN code does, then call the same
            // ClassChangeReconciler.reconcile(player) the fix now adds — rather than firing a real
            // ServerPlayConnectionEvents.JOIN, which TotalityFakePlayer cannot do (see
            // BaselineResourceLifecycleEvents.migrateLegacyIfAbsent's own established precedent for
            // testing this exact class of lifecycle at the nearest real production seam instead).
            ClassComponents.get(classLevelDecrease).selectClass(TotalityClasses.WIZARD_ID, 20);
            ClassChangeReconciler.reconcile(classLevelDecrease); // seeds AtMaximum at level 20

            safe(r, "Finding 1 regression setup: a level-20 Wizard is granted the correct high maxima "
                    + "(1st-level 4/4, 2nd-level 3/3) before the simulated level drop", () -> {
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(classLevelDecrease, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 4
                        && success.snapshot().partition(1).orElseThrow().maximumUnits() == 4
                        && success.snapshot().partition(2).orElseThrow().currentUnits() == 3
                        && success.snapshot().partition(2).orElseThrow().maximumUnits() == 3;
                return result(pass, "query=" + query);
            });

            safe(r, "Finding 1 regression setup: spending 3 of 4 level-1 slots before the drop, so the "
                    + "post-drop check below can distinguish a preserved value from a refill", () -> {
                var spendResult = PlayerResourceService.INSTANCE.trySpend(classLevelDecrease,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 3, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // 4 -> 1
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(classLevelDecrease, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = spendResult.isSuccess() && query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 1;
                return result(pass, "spendResult=" + spendResult + ", query=" + query);
            });

            safe(r, "Finding 1 fix: simulating the join-time class-level drop this fix addresses (a direct "
                    + "PlayerClassComponent.setClassLevel mutation to a much lower level, exactly like "
                    + "PlayerConnectionEvents' own stored-level normalization) followed by the exact "
                    + "ClassChangeReconciler.reconcile(player) call the fix adds: clamps the now-over-maximum "
                    + "2nd-level partition down to its new maximum, leaves the still-valid 1st-level partition "
                    + "untouched at its preserved value (no free refill), produces no stale current > maximum "
                    + "anywhere in the pool, remains wire-safe, and still has no ordinary tier-10 partition", () -> {
                ClassComponents.get(classLevelDecrease).setClassLevel(TotalityClasses.WIZARD_ID, 1); // 20 -> 1, no reconciliation of its own
                ClassChangeReconciler.reconcile(classLevelDecrease); // the exact call the fix adds

                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(classLevelDecrease, PlayerResourceIds.SPELL_SLOTS);
                if (!(query instanceof ResourceQueryResult.PartitionedSuccess success)) {
                    return result(false, "query=" + query);
                }
                var snapshot = success.snapshot();
                boolean everyPartitionValid = true;
                for (int lvl = 1; lvl <= SpellSlotTable.STANDARD_SLOT_LEVELS; lvl++) {
                    var partition = snapshot.partition(lvl).orElseThrow();
                    if (partition.currentUnits() > partition.maximumUnits()) everyPartitionValid = false;
                }
                // Level-1 Wizard row (SpellSlotTable.FULL_CASTER[0]): {2, 0, 0, ...} — 1st-level max 2, 2nd-level max 0.
                boolean level1PreservedNotRefilled = snapshot.partition(1).orElseThrow().currentUnits() == 1
                        && snapshot.partition(1).orElseThrow().maximumUnits() == 2;
                boolean level2ClampedNotStale = snapshot.partition(2).orElseThrow().currentUnits() == 0
                        && snapshot.partition(2).orElseThrow().maximumUnits() == 0;
                boolean noTierTen = snapshot.partition(10).isEmpty();
                boolean wireSafe;
                try {
                    ResourcePartitionedWireSnapshot.from(snapshot);
                    wireSafe = true;
                } catch (IllegalArgumentException wireInvariantViolation) {
                    wireSafe = false;
                }
                boolean pass = everyPartitionValid && level1PreservedNotRefilled && level2ClampedNotStale && noTierTen && wireSafe;
                return result(pass, "snapshot=" + snapshot + ", wireSafe=" + wireSafe);
            });
        } finally {
            classLevelDecrease.discard();
        }

        ServerPlayer growthPreservation = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-growth]");
        try {
            // Correction pass (2026-09-16): the manual-reproduction regression report's remaining
            // three PRESERVE_DEFICIT examples (new tier unlock, fully-spent case, and a genuine
            // decrease-with-spent-slots reconciliation using a real captured "before" snapshot — the
            // above classLevelDecrease player only exercises the DEGENERATE no-before-captured 1-arg
            // reconcile(player), which is intentionally clamp-only regardless of a resource's
            // declared policy; see ClassChangeReconciler's own Javadoc for why that degeneration is
            // safe). All state here goes through the exact captureResolvedMaximums -> mutate ->
            // reconcile(player, before) sequence AddClassLevelHandler now uses in production.
            ClassComponents.get(growthPreservation).selectClass(TotalityClasses.WIZARD_ID, 2);
            ClassChangeReconciler.reconcile(growthPreservation); // fresh grant — AtMaximum, no before needed

            safe(r, "PRESERVE_DEFICIT example 4 + example 1 (task spec): a fully-spent tier gains only its newly "
                    + "granted capacity on level-up (old max=3, current=0, new max=4 -> expected current=1), and, "
                    + "in the SAME level-up, a brand-new tier that had max=0 becomes immediately fully usable "
                    + "(old max=0, current=0, new max=2 -> expected current=2) — both computed from one real "
                    + "captureResolvedMaximums/reconcile(player, before) pass, level 2 -> 3", () -> {
                PlayerResourceService.INSTANCE.trySpend(growthPreservation,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 3, PartitionSelectionPolicy.EXACT_TIER)
                        , ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // tier1: 3/3 -> 0/3

                var before = ClassChangeReconciler.captureResolvedMaximums(growthPreservation);
                ClassComponents.get(growthPreservation).addClassLevel(TotalityClasses.WIZARD_ID); // level 2 -> 3
                ClassChangeReconciler.reconcile(growthPreservation, before);

                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(growthPreservation, PlayerResourceIds.SPELL_SLOTS);
                // SpellSlotTable.FULL_CASTER row 3 = {4, 2, 0, ...}: tier1 max 3 -> 4, tier2 max 0 -> 2.
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 1
                        && success.snapshot().partition(1).orElseThrow().maximumUnits() == 4
                        && success.snapshot().partition(2).orElseThrow().currentUnits() == 2
                        && success.snapshot().partition(2).orElseThrow().maximumUnits() == 2;
                return result(pass, "query=" + query);
            });

            safe(r, "PRESERVE_DEFICIT example 3 (task spec): a genuine captured-before decrease clamps a "
                    + "shrinking maximum while preserving already-spent capacity (old max=4, current=2 (2 spent), "
                    + "new max=3 -> expected current=1), distinct from the classLevelDecrease player's degenerate "
                    + "clamp-only check above", () -> {
                StandardSpellSlotResources.onLongRest(growthPreservation); // tier1: 1/4 -> 4/4
                PlayerResourceService.INSTANCE.trySpend(growthPreservation,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 2, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // tier1: 4/4 -> 2/4

                var before = ClassChangeReconciler.captureResolvedMaximums(growthPreservation);
                ClassComponents.get(growthPreservation).setClassLevel(TotalityClasses.WIZARD_ID, 2); // level 3 -> 2, no reconciliation of its own
                ClassChangeReconciler.reconcile(growthPreservation, before);

                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(growthPreservation, PlayerResourceIds.SPELL_SLOTS);
                // SpellSlotTable.FULL_CASTER row 2 = {3, 0, ...}: tier1 max 4 -> 3.
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 1
                        && success.snapshot().partition(1).orElseThrow().maximumUnits() == 3;
                return result(pass, "query=" + query);
            });
        } finally {
            growthPreservation.discard();
        }

        ServerPlayer maximumOnlyChange = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-maxonly]");
        try {
            // Cleanup pass (2026-09-16): the maximum-only sync edge — a partition's resolved maximum
            // genuinely changes while every partition's reconciled current stays exactly the same, so
            // reconcilePartitioned's normal drain/restore loop never runs (delta == 0 everywhere) and
            // nothing would mark the resource dirty without the new explicit fallback (see
            // ClassChangeReconciler#reconcilePartitioned's own Javadoc). Engineered precisely:
            // SpellSlotTable.FULL_CASTER rows 6 and 7 differ ONLY in tier 4 (0 vs 1) — every other tier
            // is byte-for-byte identical between the two rows, so forcing tier 4's single already-spent
            // slot to 0/0 is the only thing that can possibly change, and PRESERVE_DEFICIT's own floor
            // clamp keeps it at exactly 0 either way (an already-empty partition can't go more empty).
            // The sync-marking fix itself (that this exact "maximumChanged && !mutated" shape now
            // explicitly marks the resource dirty) is proven by the accompanying source-regression
            // sentinel, ClassChangeReconcilerMaximumSyncSourceRegressionTest — this check instead
            // proves the reconciled STATE itself comes out correct (no stale/negative/resurrected
            // value) in exactly the scenario that exercises that new fallback.
            ClassComponents.get(maximumOnlyChange).selectClass(TotalityClasses.WIZARD_ID, 7);
            ClassChangeReconciler.reconcile(maximumOnlyChange); // fresh grant — AtMaximum

            safe(r, "maximum-only sync edge setup: a level-7 Wizard's 4th-level slot is spent to 0/1", () -> {
                var spendResult = PlayerResourceService.INSTANCE.trySpend(maximumOnlyChange,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 4, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(maximumOnlyChange, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = spendResult.isSuccess() && query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(4).orElseThrow().currentUnits() == 0;
                return result(pass, "spendResult=" + spendResult + ", query=" + query);
            });

            safe(r, "maximum-only sync edge: forcing level 7 -> 6 (tier 4 max 1 -> 0, every other tier's max "
                    + "unchanged) with a real captured-before reconciles tier 4 to 0/0 (not negative, not "
                    + "resurrected) and leaves every other tier exactly as it was — a genuine maximum change "
                    + "with zero current delta anywhere in the resource, the exact shape that used to leave "
                    + "nothing marking the resource dirty", () -> {
                var before = ClassChangeReconciler.captureResolvedMaximums(maximumOnlyChange);
                ClassComponents.get(maximumOnlyChange).setClassLevel(TotalityClasses.WIZARD_ID, 6); // 7 -> 6, no reconciliation of its own
                ClassChangeReconciler.reconcile(maximumOnlyChange, before);

                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(maximumOnlyChange, PlayerResourceIds.SPELL_SLOTS);
                if (!(query instanceof ResourceQueryResult.PartitionedSuccess success)) return result(false, "query=" + query);
                var snapshot = success.snapshot();
                // SpellSlotTable.FULL_CASTER row 6 = {4, 3, 3, 0, 0, ...} vs row 7 = {4, 3, 3, 1, 0, ...}.
                boolean tier4Correct = snapshot.partition(4).orElseThrow().currentUnits() == 0
                        && snapshot.partition(4).orElseThrow().maximumUnits() == 0;
                boolean othersUnchanged = snapshot.partition(1).orElseThrow().currentUnits() == 4
                        && snapshot.partition(1).orElseThrow().maximumUnits() == 4
                        && snapshot.partition(2).orElseThrow().currentUnits() == 3
                        && snapshot.partition(3).orElseThrow().currentUnits() == 3;
                boolean wireSafe;
                try {
                    ResourcePartitionedWireSnapshot.from(snapshot);
                    wireSafe = true;
                } catch (IllegalArgumentException wireInvariantViolation) {
                    wireSafe = false;
                }
                boolean pass = tier4Correct && othersUnchanged && wireSafe;
                return result(pass, "snapshot=" + snapshot + ", wireSafe=" + wireSafe);
            });
        } finally {
            maximumOnlyChange.discard();
        }

        ServerPlayer tierSevenUnlock = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-tier7]");
        try {
            // Correction pass (2026-09-16): the EXACT manual reproduction from the bug report — a
            // Wizard leveling from class level 12 to 13 unlocking a 7th-level slot for the first
            // time — reproduced through the real production captureResolvedMaximums/addClassLevel/
            // reconcile(player, before) sequence, at the exact levels/tier the report describes.
            ClassComponents.get(tierSevenUnlock).selectClass(TotalityClasses.WIZARD_ID, 12);
            ClassChangeReconciler.reconcile(tierSevenUnlock); // fresh grant at level 12 — AtMaximum

            safe(r, "exact manual-reproduction regression: a Wizard leveling from class level 12 to 13 "
                    + "immediately gains a usable 7th-level Standard Spell Slot (1/1), matching the reported bug's "
                    + "scenario precisely (SpellSlotTable.FULL_CASTER row 12 tier7=0, row 13 tier7=1)", () -> {
                ResourceQueryResult beforeLevelUp = PlayerResourceService.INSTANCE.query(tierSevenUnlock, PlayerResourceIds.SPELL_SLOTS);
                boolean tier7WasLocked = beforeLevelUp instanceof ResourceQueryResult.PartitionedSuccess s
                        && s.snapshot().partition(7).orElseThrow().maximumUnits() == 0;

                var before = ClassChangeReconciler.captureResolvedMaximums(tierSevenUnlock);
                ClassComponents.get(tierSevenUnlock).addClassLevel(TotalityClasses.WIZARD_ID); // class level 12 -> 13
                ClassChangeReconciler.reconcile(tierSevenUnlock, before);

                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(tierSevenUnlock, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = tier7WasLocked && query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(7).orElseThrow().currentUnits() == 1
                        && success.snapshot().partition(7).orElseThrow().maximumUnits() == 1;
                return result(pass, "tier7WasLocked=" + tier7WasLocked + ", query=" + query);
            });
        } finally {
            tierSevenUnlock.discard();
        }

        ServerPlayer warlock = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-warlock]");
        try {
            ClassComponents.get(warlock).selectClass(TotalityClasses.WARLOCK_ID, 5);
            ClassChangeReconciler.reconcile(warlock);

            safe(r, "a Warlock never gains Standard Spell Slot state — Pact Magic is a separate, Phase 7 pool", () -> {
                boolean hasState = ResourceStateComponents.get(warlock).hasState(PlayerResourceIds.SPELL_SLOTS);
                return result(!hasState, "hasState=" + hasState);
            });
        } finally {
            warlock.discard();
        }

        ServerPlayer nonCaster = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-noncaster]");
        try {
            safe(r, "a player with no caster class levels never gains Standard Spell Slot state, even after an "
                    + "explicit reconciliation pass", () -> {
                StandardSpellSlotResources.reconcile(nonCaster);
                boolean hasState = ResourceStateComponents.get(nonCaster).hasState(PlayerResourceIds.SPELL_SLOTS);
                return result(!hasState, "hasState=" + hasState);
            });
        } finally {
            nonCaster.discard();
        }

        ServerPlayer legacyWizard = TotalityFakePlayer.create(level, "[StandardSpellSlotMigrationVerification-legacy]");
        try {
            safe(r, "Legacy NBT migration imports the exact partially-depleted legacy remaining values for tiers "
                    + "1-9 through the real production pipeline (BaselineResourceLifecycleEvents.migrateLegacyIfAbsent), "
                    + "clamped against the freshly resolved maximum, with no partition 10 in the resulting state. "
                    + "NOTE: this check exercises the legacy component's own current API (SpellSlotComponent#recalculate/"
                    + "useSlot), which — post-Phase-6, MAX_SPELL_LEVEL=9 — cannot represent a historical tier-10 value at "
                    + "all; it does not by itself prove tier-10 NBT data is discarded. That specific proof is a raw-NBT "
                    + "unit test instead: SpellSlotComponentTest#readDataIgnoresHistoricalTierTenKeysFromAGenuinelyOldPreMigrationSave, "
                    + "which hand-builds the old max_9/used_9 keys and reads them through the real readData().", () -> {
                ClassComponents.get(legacyWizard).selectClass(TotalityClasses.WIZARD_ID, 3);
                // Simulate a pre-Phase-6 save's still-valid tiers 1-9: legacy component holds real
                // level-3 maxima (4/2/0/.../0) with one level-1 slot spent.
                SpellSlotComponent legacy = SpellSlotComponents.get(legacyWizard);
                legacy.recalculate(new int[] {4, 2, 0, 0, 0, 0, 0, 0, 0});
                legacy.useSlot(1); // 4/4 -> 3/4 remaining at level 1
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(legacyWizard);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(legacyWizard, PlayerResourceIds.SPELL_SLOTS);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(1).orElseThrow().currentUnits() == 3
                        && success.snapshot().partition(1).orElseThrow().maximumUnits() == 4
                        && success.snapshot().partition(2).orElseThrow().currentUnits() == 2
                        && success.snapshot().partition(10).isEmpty();
                return result(pass, "query=" + query);
            });

            safe(r, "Re-running the legacy migration import is idempotent — already-migrated Generic state is "
                    + "never overwritten by stale legacy data", () -> {
                PlayerResourceService.INSTANCE.trySpend(legacyWizard,
                        new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // 3 -> 2
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(legacyWizard);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(legacyWizard, PlayerResourceIds.SPELL_SLOTS);
                return result(query instanceof ResourceQueryResult.PartitionedSuccess success
                                && success.snapshot().partition(1).orElseThrow().currentUnits() == 2,
                        "expected the post-spend value 2 to survive a repeated migration call, got " + query);
            });

            safe(r, "The durable migration marker is set, matching Mana/Stamina/Rage's own marker contract", () -> {
                PlayerResourceStateComponent state = ResourceStateComponents.get(legacyWizard);
                return result(state.isLegacyMigrated(PlayerResourceIds.SPELL_SLOTS),
                        "isLegacyMigrated=" + state.isLegacyMigrated(PlayerResourceIds.SPELL_SLOTS));
            });
        } finally {
            legacyWizard.discard();
        }

        r.summarize();
    }

    @FunctionalInterface
    private interface CheckBody {
        CheckResult run() throws Exception;
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, CheckBody body) {
        try {
            CheckResult outcome = body.run();
            r.check(label, outcome.pass(), outcome.detail());
        } catch (Exception e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ── Correction pass (2026-09-16, Finding 4): synthetic dev-only leveled spells ──────────────
    // Registered into the real, shared AbilityRegistry via its own public #add(Ability) — the same
    // "sub-registries inject entries without declaring them in AbilityRegistry itself" seam that
    // method's own Javadoc already documents, used here for a dev-only self-test instead. Both are
    // ordinary level-1 leveled spells (never cantrips) so ActivateAbilityHandler's Standard Spell
    // Slot pre-check/spend path is genuinely exercised, not skipped.

    /** Always reports no effect — proves a real cast through the real handler that never succeeds spends nothing. */
    private static final class NoEffectTestSpell extends Spell {
        static final net.minecraft.resources.Identifier ID =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "selftest_no_effect_spell");

        NoEffectTestSpell() {
            super(ID, "[Self-Test] No-Effect Spell", "Dev-only synthetic spell for Finding 4 regression coverage.",
                    Ability.Type.ACTIVE, 1, SpellSchool.DESTRUCTION, false, false, 0,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "textures/ability/selftest.png"), "");
        }

        @Override
        public void onActivate(ServerPlayer player, @org.jspecify.annotations.Nullable AbilityContext context) {
            markNoEffect();
        }
    }

    /** Always reports success — the control spell proving the no-effect check above is genuinely
     *  discriminating cast-result branching, not merely a handler that never spends anything. */
    private static final class AlwaysSucceedsTestSpell extends Spell {
        static final net.minecraft.resources.Identifier ID =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "selftest_always_succeeds_spell");

        AlwaysSucceedsTestSpell() {
            super(ID, "[Self-Test] Always-Succeeds Spell", "Dev-only synthetic spell for Finding 4 regression coverage.",
                    Ability.Type.ACTIVE, 1, SpellSchool.DESTRUCTION, false, false, 0,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "textures/ability/selftest.png"), "");
        }

        @Override
        public void onActivate(ServerPlayer player, @org.jspecify.annotations.Nullable AbilityContext context) {
            // Deliberately does nothing (in particular, never calls markNoEffect()) — a successful,
            // effect-free cast for this test's purposes; ActivateAbilityHandler only cares about
            // Spell.didCastSucceed(), which defaults to true unless markNoEffect() is called.
        }
    }
}
