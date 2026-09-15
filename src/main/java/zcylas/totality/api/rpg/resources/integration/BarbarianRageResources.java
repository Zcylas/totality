package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.networking.resource.ResourceSyncManager;

import java.util.List;

/**
 * The canonical §16.2 {@code BarbarianClass -> totality:rage} {@code CLASS} grant (Phase 5 Rage
 * migration, 2026-09-15) — Rage is owned exclusively by the Barbarian class, unlike Mana/Stamina's
 * unconditional {@code totality:player_baseline} grant (see {@link PlayerBaselineResources}, the
 * direct template this class mirrors). {@link #PROVIDER} grants {@code totality:rage} only to a
 * player who currently {@code hasClass(TotalityClasses.BARBARIAN_ID)} — the same single source of
 * truth already used by {@code BarbarianRageAbility.canActivate}/{@code SelectClassHandler}/{@code
 * PlayerConnectionEvents}, so no second entitlement system is introduced.
 *
 * <p>Grant shape: {@link ResourceGrantInitialization.AtMaximum} (a newly-selected Barbarian starts
 * with a full Rage pool, matching legacy {@code registerChargePool}'s {@code ensurePool} which always
 * created a brand-new pool at {@code current == max}); {@link ResourceRemovalPolicy#REMOVE_STATE}
 * (canonical §16.7's default — no production class-removal/respec path exists, see {@code
 * SelectClassHandler}'s {@code hasAnyClass} guard, so this is never actually exercised today);
 * {@link ResourceVisibilityPolicy#WHEN_ACTIVE} (Rage is only relevant while the player actually has
 * the class, unlike Mana/Stamina's {@code ALWAYS_FOR_OWNER}).
 *
 * <p>{@code totality:rage}'s definition uses {@link zcylas.totality.api.rpg.resources.ResourceLifecyclePolicy#DEFAULT}
 * — no {@code .lifecycle(...)} override, unlike Mana/Stamina's {@code RESET_TO_MAXIMUM} override —
 * because {@code DEFAULT}'s {@code KEEP_CURRENT} death policy already matches legacy Rage's own death
 * behavior exactly (legacy {@code PlayerChargesComponent.copyFrom} is a blanket preserve-all-pools
 * copy; see {@code PlayerChargesRageCharacterizationTest.copyFromPreservesAllPoolsIndependently}, an
 * existing test proving this).
 */
public final class BarbarianRageResources {

    public static final Identifier SOURCE_ID = Identifier.fromNamespaceAndPath("totality", "barbarian_class");

    // player may be null in tests that inspect a provider's declared grant shape without a real
    // ServerPlayer (see ResourceGrantReconcilerTest's/PlayerBaselineResourcesTest's own established
    // nullable-player convention) — unlike PlayerBaselineResources.PROVIDER, this provider must
    // actually dereference the player to decide class ownership, so a null player conservatively
    // yields no grants rather than throwing.
    private static final ResourceGrantProvider PROVIDER = player ->
            player != null && ClassComponents.get(player).hasClass(TotalityClasses.BARBARIAN_ID)
                    ? List.of(grant())
                    : List.of();

    private static final ResourceGrantReconciler RECONCILER = new ResourceGrantReconciler(
            PlayerResourceRegistry.INSTANCE, ResourceGrantRegistry.INSTANCE,
            ResourceGrantPolicyRegistry.INSTANCE, PlayerResourceService.INSTANCE);

    private static ResourceGrant grant() {
        return new ResourceGrant(
                PlayerResourceIds.RAGE, SOURCE_ID, ResourceGrantSourceType.CLASS, ResourceGrantMode.PERSISTENT,
                new ResourceGrantInitialization.AtMaximum(), ResourceRemovalPolicy.REMOVE_STATE,
                ResourceVisibilityPolicy.WHEN_ACTIVE, 0);
    }

    /** Registers the grant provider. Called once at mod init, from {@code ProductionResourceDefinitions.register()}. */
    public static void register() {
        ResourceGrantRegistry.INSTANCE.register(PROVIDER);
    }

    /**
     * Reconciles {@code player}'s Rage grant against their live state, marking any structurally
     * changed resource dirty for the Phase 3A generic sync path to pick up. Called directly from
     * {@code BarbarianRageAbility.registerChargePool} (class selection — the one lifecycle trigger
     * not already covered generically) as well as indirectly by every ordinary join/respawn/
     * dimension-transfer trigger already wired through {@code BaselineResourceLifecycleEvents}
     * (which reconciles every registered provider, this one included, since {@link
     * ResourceGrantRegistry#INSTANCE} is shared global state).
     */
    public static void reconcile(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        ResourceGrantReconciler.ReconciliationResult result = RECONCILER.reconcile(player, state);
        for (Identifier id : result.instantiated()) {
            ResourceSyncManager.markDirty(player.getUUID(), id);
        }
        for (Identifier id : result.removed()) {
            ResourceSyncManager.markDirty(player.getUUID(), id);
        }
    }

    /** Self-heals a Barbarian whose Rage state was never instantiated (mirrors {@link PlayerBaselineResources#ensureInstantiated}). */
    public static void ensureInstantiated(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        if (ClassComponents.get(player).hasClass(TotalityClasses.BARBARIAN_ID) && !state.hasState(PlayerResourceIds.RAGE)) {
            reconcile(player);
        }
    }

    private BarbarianRageResources() {}
}
