package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.magic.spell.SpellSlotRecalculator;
import zcylas.totality.api.magic.spell.SpellSlotTable;
import zcylas.totality.api.rpg.resources.MaximumChangePolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceAmount;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;

import java.util.List;

/**
 * The canonical {@code combined multiclass caster progression -> totality:spell_slots} {@code CLASS}
 * grant (Phase 6 Standard Spell Slot migration, 2026-09-16) — mirrors {@link BarbarianRageResources}'s
 * shape exactly, generalized from "one specific class" to "any class currently contributing to the
 * shared Standard Spell Slot pool" via {@link SpellSlotRecalculator#computeCombinedCasterLevel}, the
 * same single authoritative calculation {@code StandardSpellSlotMaximumResolver} uses for the
 * maximum itself.
 *
 * <p>Warlock is deliberately excluded from this entitlement check — {@code
 * SpellSlotRecalculator.computeCombinedCasterLevel} never counts {@code CasterProgression.WARLOCK}
 * levels, so a Warlock-only character's combined level is 0 and this provider grants nothing to them
 * (Pact Magic is Phase 7 scope, a structurally separate pool this class does not touch).
 *
 * <p>Grant shape: {@link ResourceGrantInitialization.AtMaximum} (a newly-qualifying caster starts
 * with a full pool at every tier they have — the same "genuinely new pool" semantics {@code
 * BarbarianRageResources} uses); {@link ResourceRemovalPolicy#REMOVE_STATE} (canonical default —
 * exercised by {@code ClassChangeReconciler} exactly like Rage's); {@link
 * ResourceVisibilityPolicy#WHEN_ACTIVE} (the pool is only relevant while the player actually
 * qualifies, matching Rage's own choice over Mana/Stamina's {@code ALWAYS_FOR_OWNER}). <b>Correction
 * pass (2026-09-16):</b> {@link MaximumChangePolicy#PRESERVE_DEFICIT} is also registered for this
 * resource (see {@link #register()}) — unlike Rage/Mana/Stamina, a level-up growing the combined
 * caster level must immediately grant the newly unlocked capacity while leaving already-spent slots
 * spent, not silently do nothing until the next rest.
 */
public final class StandardSpellSlotResources {

    public static final Identifier SOURCE_ID = Identifier.fromNamespaceAndPath("totality", "standard_caster_progression");

    // player may be null in tests that inspect a provider's declared grant shape without a real
    // ServerPlayer (see BarbarianRageResources' own established nullable-player convention) — this
    // provider must dereference the player to compute the combined caster level, so a null player
    // conservatively yields no grants rather than throwing.
    private static final ResourceGrantProvider PROVIDER = player ->
            player != null && SpellSlotRecalculator.computeCombinedCasterLevel(player) > 0
                    ? List.of(grant())
                    : List.of();

    private static ResourceGrant grant() {
        return new ResourceGrant(
                PlayerResourceIds.SPELL_SLOTS, SOURCE_ID, ResourceGrantSourceType.CLASS, ResourceGrantMode.PERSISTENT,
                new ResourceGrantInitialization.AtMaximum(), ResourceRemovalPolicy.REMOVE_STATE,
                ResourceVisibilityPolicy.WHEN_ACTIVE, 0);
    }

    /** Registers the grant provider. Called once at mod init, from {@code ProductionResourceDefinitions.register()}. */
    public static void register() {
        ResourceGrantRegistry.INSTANCE.register(PROVIDER);
        // Correction pass (2026-09-16): a multiclass level-up that raises the combined caster level
        // does NOT re-instantiate this grant (it stays continuously granted — ResourceGrantReconciler
        // never refills already-instantiated state), so its live maximum can rise on a level-up with
        // no accompanying change to its stored PARTITIONED_POOL current. PRESERVE_DEFICIT is the
        // explicit "add the gained difference" choice canonical §10.5 requires the owning system to
        // make: a newly unlocked tier (old max 0) becomes immediately usable, and a partially-spent
        // tier's already-spent slots stay spent — see ClassChangeReconciler's maximum-reconciliation
        // step, which applies this policy whenever a genuine pre-mutation maximum was captured.
        ResourceGrantPolicyRegistry.INSTANCE.register(PlayerResourceIds.SPELL_SLOTS,
                new ResourceGrantPolicy(ResourceGrantAggregationPolicy.SINGLE_OWNER, ResourceRemovalPolicy.REMOVE_STATE,
                        MaximumChangePolicy.PRESERVE_DEFICIT));
    }

    /** Reconciles {@code player}'s Standard Spell Slot grant against their live state, marking any
     *  structurally changed resource dirty for the Generic sync path to pick up. Called by {@code
     *  ClassChangeReconciler} (transitively, via the shared {@link ResourceGrantRegistry#INSTANCE})
     *  and by every ordinary join/respawn/dimension-transfer trigger already wired through {@code
     *  BaselineResourceLifecycleEvents}. */
    public static void reconcile(ServerPlayer player) {
        ResourceGrantReconciliation.reconcileAndSync(player);
    }

    /**
     * Long Rest: fully restores every Standard Spell Slot tier to its current resolved maximum
     * (canonical D&D 2024 rule — see {@link zcylas.totality.api.magic.spell.SpellSlotComponent}'s
     * own retired {@code restoreAll} for the exact legacy behavior this reproduces). Mirrors {@code
     * BarbarianRageAbility#onLongRest}'s exact-deficit pattern per partition, so the checked-
     * arithmetic mutation path cannot spuriously overflow-reject a legitimate full restore.
     *
     * <p>There is deliberately no {@code onShortRest} — canonical Phase 6 scope: Short Rest does not
     * restore Standard Spell Slots (only Warlock Pact Magic would, and that is Phase 7 scope).
     */
    public static void onLongRest(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        if (!state.hasState(PlayerResourceIds.SPELL_SLOTS)) return;
        var partitioned = state.getPartitioned(PlayerResourceIds.SPELL_SLOTS);
        if (partitioned.isEmpty()) return;
        var definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS);
        if (definition.isEmpty()) return;
        var maxOpt = PlayerResourceService.INSTANCE.resolveMaximum(player, definition.get(), ResourceResolutionContext.EMPTY);
        if (maxOpt.isEmpty() || !(maxOpt.get() instanceof ResourceMaximum.Partitioned partitionedMax)) {
            return;
        }
        for (int level = 1; level <= SpellSlotTable.STANDARD_SLOT_LEVELS; level++) {
            long max = partitionedMax.effectiveByPartition().getOrDefault(level, 0L);
            long current = partitioned.get().getCurrent(level);
            long deficit = max - current;
            if (deficit <= 0) continue;
            PlayerResourceService.INSTANCE.restore(player,
                    ResourceAmount.partitioned(PlayerResourceIds.SPELL_SLOTS, level, deficit),
                    ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.LONG_REST)));
        }
    }

    private StandardSpellSlotResources() {}
}
