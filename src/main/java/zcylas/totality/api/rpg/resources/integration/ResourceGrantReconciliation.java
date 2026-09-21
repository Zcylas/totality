package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.networking.resource.ResourceSyncManager;

/**
 * The one shared "reconcile every registered {@link ResourceGrantProvider} against {@code player}'s
 * live state, then mark whatever changed dirty for sync" entry point — factored out so every caller
 * that needs a full-registry sweep (baseline Mana/Stamina, Barbarian Rage, and the class-mutation
 * lifecycle seam in {@code zcylas.totality.api.rpg.classes.ClassChangeReconciler}) shares exactly one
 * {@link ResourceGrantReconciler} wiring instead of each constructing its own identical instance.
 *
 * <p>{@link ResourceGrantReconciler#reconcile} always evaluates every provider registered against
 * {@link ResourceGrantRegistry#INSTANCE} — not just a caller's "own" resource — so this facade is
 * deliberately resource-agnostic: it has no idea whether Rage, Mana, a future Ki, or anything else is
 * involved, which is exactly what lets the class-mutation lifecycle seam stay universal rather than
 * hardcoding a per-resource call list.
 */
public final class ResourceGrantReconciliation {

    private static final ResourceGrantReconciler RECONCILER = new ResourceGrantReconciler(
            PlayerResourceRegistry.INSTANCE, ResourceGrantRegistry.INSTANCE,
            ResourceGrantPolicyRegistry.INSTANCE, PlayerResourceService.INSTANCE);

    /** Reconciles every registered grant provider against {@code player} and marks every
     *  instantiated/removed resource dirty for the Generic sync path to pick up. */
    public static ResourceGrantReconciler.ReconciliationResult reconcileAndSync(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        ResourceGrantReconciler.ReconciliationResult result = RECONCILER.reconcile(player, state);
        for (Identifier id : result.instantiated()) {
            ResourceSyncManager.markDirty(player.getUUID(), id);
        }
        for (Identifier id : result.removed()) {
            ResourceSyncManager.markDirty(player.getUUID(), id);
        }
        return result;
    }

    private ResourceGrantReconciliation() {}
}
