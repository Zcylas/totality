package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.networking.resource.ResourceSyncManager;

import java.util.List;

/**
 * The canonical §16.2 {@code totality:player_baseline} {@code GLOBAL_SYSTEM} grant — Mana and
 * Stamina are universal player resources, granted unconditionally to every player, not gated behind
 * any class/species/lineage/discipline check (canonical §16.2: "The resource definition must not
 * hardcode checks such as `player is Barbarian`"; this provider hardcodes nothing — every player
 * simply receives both grants). This is the first production {@link ResourceGrantProvider}/{@link
 * ResourceGrantReconciler} wiring in this codebase (Phase 4 Mana/Stamina migration, 2026-09-15) —
 * the pre-Phase-4 foundation deliberately built and tested this machinery without ever registering a
 * real production provider.
 *
 * <p>{@link #ensureInstantiated} exists specifically so {@link
 * zcylas.totality.api.rpg.mana.PlayerManaManager}/{@link zcylas.totality.api.rpg.stamina.PlayerStaminaManager}
 * can reproduce their legacy getters' lazy-initialize-on-first-access behavior without lazily
 * initializing through an ad hoc default — it self-heals through this exact same real grant
 * mechanism instead. Ordinary gameplay never needs this (the join/respawn/dimension-transfer
 * lifecycle hooks in {@code BaselineResourceLifecycleEvents} already reconcile before any gameplay
 * code runs); it exists for callers that construct a {@code ServerPlayer} outside that lifecycle
 * (dev-only self-tests using {@code TotalityFakePlayer}, which never fires {@code
 * ServerPlayConnectionEvents.JOIN}).
 */
public final class PlayerBaselineResources {

    public static final Identifier SOURCE_ID = Identifier.fromNamespaceAndPath("totality", "player_baseline");

    private static final ResourceGrantProvider PROVIDER = player -> List.of(
            grant(PlayerResourceIds.MANA),
            grant(PlayerResourceIds.STAMINA)
    );

    private static final ResourceGrantReconciler RECONCILER = new ResourceGrantReconciler(
            PlayerResourceRegistry.INSTANCE, ResourceGrantRegistry.INSTANCE,
            ResourceGrantPolicyRegistry.INSTANCE, PlayerResourceService.INSTANCE);

    private static ResourceGrant grant(Identifier resourceId) {
        return new ResourceGrant(
                resourceId, SOURCE_ID, ResourceGrantSourceType.GLOBAL_SYSTEM, ResourceGrantMode.PERSISTENT,
                new ResourceGrantInitialization.AtMaximum(), ResourceRemovalPolicy.REMOVE_STATE,
                ResourceVisibilityPolicy.ALWAYS_FOR_OWNER, 0);
    }

    /** Registers the grant provider. Called once at mod init, from {@code ProductionResourceDefinitions.register()}. */
    public static void register() {
        ResourceGrantRegistry.INSTANCE.register(PROVIDER);
    }

    /**
     * Reconciles {@code player}'s Mana/Stamina grants against their live state and marks every
     * structurally changed resource dirty for the existing Phase 3A generic sync path to pick up —
     * closing the "no production caller ever invokes reconcile()" gap the pre-Phase-4 foundation
     * correction pass deliberately left open (see that pass's Issue 6).
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

    /** Self-heals a player whose Mana/Stamina state was never instantiated (see the class Javadoc). */
    public static void ensureInstantiated(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        if (!state.hasState(PlayerResourceIds.MANA) || !state.hasState(PlayerResourceIds.STAMINA)) {
            reconcile(player);
        }
    }

    private PlayerBaselineResources() {}
}
