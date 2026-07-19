package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.resources.PlayerResourceComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceComponents;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.Set;
import java.util.function.IntSupplier;

/**
 * <b>Transitional, query-only</b> adapter over the legacy-authoritative Mana store
 * ({@link PlayerResourceComponent#getMana()}, via {@link ResourceComponents}). {@link PlayerManaManager}
 * remains the sole authoritative owner of every Mana gameplay operation (spending, regeneration,
 * maximum calculation, persistence, its own bespoke packet) — this adapter changes none of that; it
 * only ever reads, for exactly as long as this transitional phase lasts. See the Phase 2C report's
 * "Exact audited Mana architecture" section for the full audit this class is built from.
 *
 * <h2>Why "transitional" (definition-version migration note)</h2>
 * {@code totality:mana} is registered as {@code EXTERNAL_ADAPTER}-authority pointing at this
 * adapter, at {@code definitionVersion = 1} (see {@code ProductionResourceDefinitions}). A future
 * phase migrating Mana's actual storage onto {@code GENERIC_COMPONENT}
 * ({@link zcylas.totality.api.rpg.resources.PlayerResourceStateComponent})
 * requires an explicit {@code definitionVersion} increase and a real migration step at that time —
 * never a silent structural hot-swap of what {@code totality:mana} means. Until that phase exists,
 * {@code totality:mana} means exactly one thing: a read-only mirror of {@link PlayerResourceComponent#getMana()}.
 *
 * <h2>Why the legacy manager's own getters are not called here</h2>
 * {@link PlayerManaManager#getMana(Player)} lazily initializes the legacy component to its maximum
 * the first time it is called for a player whose Mana has never been set
 * ({@code if (!comp.isManaInitialized()) comp.setMana(max)}) — a real, intentional side effect for
 * ordinary gameplay callers, but exactly the kind of "a read silently becomes a write" this
 * adapter's read-only query contract must never reproduce. This adapter instead reads
 * {@link PlayerResourceComponent#isManaInitialized()}/{@link PlayerResourceComponent#getMana()}
 * directly — both genuinely side-effect-free reads on the component itself — and reports
 * {@link ResourceQueryFailureReason#STATE_UNINITIALIZED} rather than initializing anything when the
 * component has never been set. {@link PlayerManaManager#getMaxMana(Player)} <em>is</em> called
 * directly: it is a pure computation (equipment/effect/stat loop plus an event post), confirmed by
 * audit to have no observable side effect, matching {@code HealthResourceAdapter}'s precedent of
 * calling {@code player.getMaxHealth()} directly. Correction pass: the initialized check now runs
 * <em>before</em> the maximum is ever computed — an uninitialized query returns
 * {@code STATE_UNINITIALIZED} without calling {@link PlayerManaManager#getMaxMana(Player)} at all,
 * so the (harmless today, but unnecessary) {@code MaxManaCalcEvent} dispatch that method performs
 * never fires for a query that cannot produce a snapshot anyway. See {@link #resolve} below.
 *
 * <h2>Server/client query boundary</h2>
 * Mana has no vanilla native synchronization and no generic Resource API synchronization yet — only
 * a legacy bespoke packet ({@code SyncManaPayload}) and a legacy client-side cache
 * ({@code ClientManaManager}), read directly by {@code TotalityHudRenderer} and left completely
 * untouched by this phase. See {@link ExternalResourceClientMirrorMode#LEGACY_BESPOKE_SYNCHRONIZATION}.
 * This adapter is deliberately never given a code path that imports or reads {@code ClientManaManager} —
 * a client-side query returns a structured {@link ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE}
 * failure instead.
 */
public final class ManaResourceAdapter implements ExternalPlayerResourceAdapter {

    public static final Identifier ID = PlayerResourceIds.MANA_ADAPTER;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public ResourceQueryResult snapshot(Player player, PlayerResourceDefinition definition) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, definition.id());
        }
        var component = ResourceComponents.maybeGet(serverPlayer);
        if (component.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, definition.id());
        }
        return resolve(definition.id(), component.get(), () -> PlayerManaManager.getMaxMana(player), definition.unitScale());
    }

    /**
     * Orchestrates the initialized-check-before-maximum-calculation ordering, package-visible so a
     * test can prove the {@code maximumSupplier} is never invoked for uninitialized state — using a
     * real {@link PlayerResourceComponent} (constructible without a {@code ServerPlayer}, matching
     * Phase 1's own precedent) and a plain counting lambda in place of
     * {@code PlayerManaManager.getMaxMana(Player)}, with no mocking framework needed.
     *
     * @return a {@link ResourceQueryResult.Failure} naming {@link ResourceQueryFailureReason#STATE_UNINITIALIZED}
     *         immediately, without calling {@code maximumSupplier} at all, if {@code component} is
     *         not yet initialized; otherwise delegates to {@link #normalize} with the component's
     *         current value and the supplier's (now-computed) result.
     */
    static ResourceQueryResult resolve(Identifier resourceId, PlayerResourceComponent component, IntSupplier maximumSupplier, long unitScale) {
        if (!component.isManaInitialized()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNINITIALIZED, resourceId);
        }
        return normalize(resourceId, component.getMana(), maximumSupplier.getAsInt(), unitScale);
    }

    /**
     * Pure normalization core, package-visible so it can be unit-tested directly with arbitrary
     * {@code (current, liveMaximum)} inputs without a real Minecraft runtime. Assumes {@code current}
     * is already known-initialized (see {@link #resolve}, which is the only production caller).
     *
     * @return a {@link ResourceQueryResult.Failure} naming {@link ResourceQueryFailureReason#MALFORMED_OWNER_STATE}
     *         if {@code liveMaximum} is not positive, or if the (never legitimately negative, per
     *         {@link PlayerResourceComponent#isManaInitialized()}'s own {@code >= 0} contract, but
     *         guarded defensively anyway) current value is negative; otherwise a
     *         {@link ResourceQueryResult.Success} wrapping the snapshot, current passed through
     *         <em>unclamped</em> against the maximum — {@code PlayerResourceRecalculator} and the
     *         Mana server tick already intentionally allow current to transiently exceed maximum
     *         (e.g. immediately after a Fortify effect expires) until their next clamping pass, and
     *         this adapter must not silently "repair" that existing, accepted behavior.
     */
    static ResourceQueryResult normalize(Identifier resourceId, int current, int liveMaximum, long unitScale) {
        if (liveMaximum <= 0) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, resourceId);
        }
        if (current < 0) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, resourceId);
        }
        return new ResourceQueryResult.Success(new ResourceSnapshot(resourceId, current, liveMaximum, unitScale));
    }

    @Override
    public Set<ExternalResourceOperationSupport> supportedOperations() {
        return Set.of(ExternalResourceOperationSupport.QUERY);
    }

    @Override
    public ExternalResourceClientMirrorMode clientMirrorMode() {
        return ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION;
    }

    public static final ManaResourceAdapter INSTANCE = new ManaResourceAdapter();
}
