package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.resources.PlayerResourceComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceComponents;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

import java.util.Set;
import java.util.function.IntSupplier;

/**
 * <b>Transitional, query-only</b> adapter over the legacy-authoritative Stamina store
 * ({@link PlayerResourceComponent#getStamina()}, via {@link ResourceComponents}). {@link PlayerStaminaManager}
 * remains the sole authoritative owner of every Stamina gameplay operation (sprint/flight/bow/melee/
 * ability costs, regeneration, combat-state and Exhaustion multipliers, maximum calculation,
 * persistence, its own bespoke packet) — this adapter changes none of that; it only ever reads, for
 * exactly as long as this transitional phase lasts. See the Phase 2C report's "Exact audited Stamina
 * architecture" section for the full audit this class is built from. Mirrors {@link ManaResourceAdapter}'s
 * design exactly — see that class's Javadoc for the full rationale behind each decision below.
 *
 * <h2>Why "transitional" (definition-version migration note)</h2>
 * {@code totality:stamina} is registered as {@code EXTERNAL_ADAPTER}-authority pointing at this
 * adapter, at {@code definitionVersion = 1} (see {@code ProductionResourceDefinitions}). A future
 * phase migrating Stamina's actual storage onto {@code GENERIC_COMPONENT}
 * ({@link zcylas.totality.api.rpg.resources.PlayerResourceStateComponent}) requires an explicit
 * {@code definitionVersion} increase and a real migration step at that time — never a silent
 * structural hot-swap of what {@code totality:stamina} means.
 *
 * <h2>Why the legacy manager's own getters are not called here</h2>
 * {@link PlayerStaminaManager#getStamina(Player)} lazily initializes the legacy component to its
 * maximum the first time it is called for a player whose Stamina has never been set — this adapter
 * instead reads {@link PlayerResourceComponent#isStaminaInitialized()}/{@link PlayerResourceComponent#getStamina()}
 * directly (both side-effect-free) and reports {@link ResourceQueryFailureReason#STATE_UNINITIALIZED}
 * rather than initializing anything. {@link PlayerStaminaManager#getMaxStamina(Player)} <em>is</em>
 * called directly — a pure computation, confirmed by audit to have no observable side effect.
 * Correction pass: the initialized check now runs <em>before</em> the maximum is ever computed — an
 * uninitialized query returns {@code STATE_UNINITIALIZED} without calling
 * {@link PlayerStaminaManager#getMaxStamina(Player)} at all, so the {@code MaxStaminaCalcEvent}
 * dispatch that method performs never fires for a query that cannot produce a snapshot anyway. See
 * {@link #resolve} below.
 *
 * <h2>Server/client query boundary</h2>
 * Stamina has no vanilla native synchronization and no generic Resource API synchronization yet —
 * only a legacy bespoke packet ({@code SyncStaminaPayload}) and a legacy client-side cache
 * ({@code ClientStaminaManager}), read directly by {@code TotalityHudRenderer} and left completely
 * untouched by this phase. See {@link ExternalResourceClientMirrorMode#LEGACY_BESPOKE_SYNCHRONIZATION}.
 * This adapter never imports or reads {@code ClientStaminaManager} — a client-side query returns a
 * structured {@link ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE} failure instead.
 */
public final class StaminaResourceAdapter implements ExternalPlayerResourceAdapter {

    public static final Identifier ID = PlayerResourceIds.STAMINA_ADAPTER;

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
        return resolve(definition.id(), component.get(), () -> PlayerStaminaManager.getMaxStamina(player), definition.unitScale());
    }

    /**
     * Orchestrates the initialized-check-before-maximum-calculation ordering. Identical rule to
     * {@link ManaResourceAdapter#resolve} — see that method's Javadoc for the full rationale.
     */
    static ResourceQueryResult resolve(Identifier resourceId, PlayerResourceComponent component, IntSupplier maximumSupplier, long unitScale) {
        if (!component.isStaminaInitialized()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNINITIALIZED, resourceId);
        }
        return normalize(resourceId, component.getStamina(), maximumSupplier.getAsInt(), unitScale);
    }

    /**
     * Pure normalization core, package-visible so it can be unit-tested directly with arbitrary
     * {@code (current, liveMaximum)} inputs without a real Minecraft runtime. Identical rule to
     * {@link ManaResourceAdapter#normalize} — see that method's Javadoc for the full rationale.
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

    public static final StaminaResourceAdapter INSTANCE = new StaminaResourceAdapter();
}
