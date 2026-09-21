package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.classes.ClassComponents;
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
 * The canonical {@code class progression -> totality:health_recovery_dice} {@code CLASS} grant
 * (Phase 7A, 2026-09-16; renamed from the working name {@code totality:hit_dice}/{@code
 * HitDiceResources} before commit — this Generic Resource is a spendable/restorable Health
 * recovery pool, NOT the future Hit Die API, a separate, unimplemented Character Creation/
 * Progression system that will eventually govern class/resource growth rolls — see the
 * implementation report's "relationship to the future Hit Die API" section) — canonical §25.10:
 * "Grant owner: Class progression system," "Aggregation: SHARED_RESOURCE." A player qualifies as
 * soon as they own at least one class — every {@code ClassData} always declares an {@code hpDie}
 * (non-nullable), so "owns a class" and "contributes at least one Health Recovery Die" are the
 * same condition (canonical §28.9: "A single-class character receives one maximum [Health Recovery
 * Die] per allocated class level").
 *
 * <p>Grant shape: {@link ResourceGrantInitialization.AtMaximum} (a first-time grant starts full,
 * mirroring Rage/Standard Spell Slots — the same "genuinely new pool" semantics both already use);
 * {@link ResourceRemovalPolicy#REMOVE_STATE} (losing every contributing class removes the pool —
 * canonical default, matching the task's explicit instruction); {@link
 * ResourceVisibilityPolicy#WHEN_ACTIVE}.
 *
 * <p>{@link MaximumChangePolicy#PRESERVE_DEFICIT} is registered for this resource. Canonical
 * §25.10/§28.9's "do not automatically fill newly added dice unless the Class/level-up design
 * explicitly requests it" is an explicit conditional escape hatch, not an absolute prohibition —
 * and this task's own instructions are exactly such a request, asking for the same
 * deficit-preserving shape Standard Spell Slots already established (Phase 6 §22/§23): already-
 * spent dice stay spent, but a level-up's newly gained die — or a brand-new die-size partition
 * (see {@code HealthRecoveryDiceMaximumResolver}'s Javadoc for why that partition is never
 * genuinely absent) — becomes immediately available. {@code SHARED_RESOURCE} aggregation is
 * registered per canon's own explicit choice for this resource (distinct from Standard Spell
 * Slots' {@code SINGLE_OWNER}) even though only one grant provider exists for it today —
 * canonical §16.4: "any valid grant may seed the first instantiation," the correct semantic even
 * with a single current provider.
 */
public final class HealthRecoveryDiceResources {

    public static final Identifier SOURCE_ID = Identifier.fromNamespaceAndPath("totality", "class_progression_health_recovery_dice");

    // player may be null in tests that inspect a provider's declared grant shape without a real
    // ServerPlayer (see BarbarianRageResources' own established nullable-player convention) —
    // ClassComponents.get never returns null for a real player (PlayerComponentEvents.registerForPlayers
    // always attaches the component), so no defensive null-check beyond the player itself is needed.
    private static final ResourceGrantProvider PROVIDER = player ->
            player != null && ClassComponents.get(player).hasAnyClass()
                    ? List.of(grant())
                    : List.of();

    private static ResourceGrant grant() {
        return new ResourceGrant(
                PlayerResourceIds.HEALTH_RECOVERY_DICE, SOURCE_ID, ResourceGrantSourceType.CLASS, ResourceGrantMode.PERSISTENT,
                new ResourceGrantInitialization.AtMaximum(), ResourceRemovalPolicy.REMOVE_STATE,
                ResourceVisibilityPolicy.WHEN_ACTIVE, 0);
    }

    /** Registers the grant provider and the resource's {@link MaximumChangePolicy}. Called once at
     *  mod init, from {@code ProductionResourceDefinitions.register()}. */
    public static void register() {
        ResourceGrantRegistry.INSTANCE.register(PROVIDER);
        ResourceGrantPolicyRegistry.INSTANCE.register(PlayerResourceIds.HEALTH_RECOVERY_DICE,
                new ResourceGrantPolicy(ResourceGrantAggregationPolicy.SHARED_RESOURCE, ResourceRemovalPolicy.REMOVE_STATE,
                        MaximumChangePolicy.PRESERVE_DEFICIT));
    }

    /** Reconciles {@code player}'s Health Recovery Dice grant against their live state, marking any
     *  structurally changed resource dirty for the Generic sync path to pick up. Called
     *  transitively (via the shared {@link ResourceGrantRegistry#INSTANCE}) by {@code
     *  ClassChangeReconciler} and by every ordinary join/respawn/dimension-transfer trigger
     *  already wired through {@code BaselineResourceLifecycleEvents}. */
    public static void reconcile(ServerPlayer player) {
        ResourceGrantReconciliation.reconcileAndSync(player);
    }

    /**
     * Long Rest: canonical §25.10/§28.9 — "The adopted Rest rule currently restores all [Health
     * Recovery Dice] on a valid Long Rest," "Long Rest restores all [Health Recovery Dice]
     * according to the adopted Totality rule." Full restoration, every partition, mirrors {@code
     * StandardSpellSlotResources#onLongRest}'s exact-deficit pattern so the checked-arithmetic
     * mutation path cannot spuriously overflow-reject a legitimate full restore.
     *
     * <p>There is deliberately no {@code onShortRest} here — Health Recovery Dice are SPENT during
     * a Short Rest (a deliberate player choice, owned by the Rest/Health integration layer per
     * canonical §25.10, not yet built — see the implementation report), never restored by one.
     */
    public static void onLongRest(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        if (!state.hasState(PlayerResourceIds.HEALTH_RECOVERY_DICE)) return;
        var partitioned = state.getPartitioned(PlayerResourceIds.HEALTH_RECOVERY_DICE);
        if (partitioned.isEmpty()) return;
        var definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH_RECOVERY_DICE);
        if (definition.isEmpty()) return;
        var maxOpt = PlayerResourceService.INSTANCE.resolveMaximum(player, definition.get(), ResourceResolutionContext.EMPTY);
        if (maxOpt.isEmpty() || !(maxOpt.get() instanceof ResourceMaximum.Partitioned partitionedMax)) {
            return;
        }
        for (Integer dieSize : partitionedMax.effectiveByPartition().keySet()) {
            long max = partitionedMax.effectiveByPartition().getOrDefault(dieSize, 0L);
            long current = partitioned.get().getCurrent(dieSize);
            long deficit = max - current;
            if (deficit <= 0) continue;
            PlayerResourceService.INSTANCE.restore(player,
                    ResourceAmount.partitioned(PlayerResourceIds.HEALTH_RECOVERY_DICE, dieSize, deficit),
                    ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.LONG_REST)));
        }
    }

    private HealthRecoveryDiceResources() {}
}
