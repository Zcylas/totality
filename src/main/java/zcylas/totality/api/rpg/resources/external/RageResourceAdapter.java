package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.PlayerChargesComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.Optional;
import java.util.Set;

/**
 * <b>Transitional, query-only</b> adapter over one entry of the legacy-authoritative, generically
 * {@code Identifier}-keyed charge-pool store, {@link PlayerChargesComponent}, via {@link
 * ChargeComponents}. {@link PlayerChargesComponent} remains the sole authoritative owner of every
 * Rage charge gameplay operation (consumption on activation, Short/Long Rest recovery, maximum
 * recalculation on Barbarian class selection/level-up, persistence, its own bespoke sync packet) —
 * this adapter changes none of that; it only ever reads, for exactly as long as this transitional
 * phase lasts. See the Phase 2E report's "Legacy Rage characterization findings" section for the
 * full audit this class is built from.
 *
 * <h2>Why {@code SCALAR}, not {@code PARTITIONED_POOL}</h2>
 * Rage charges are fully mechanically interchangeable: no individual charge has a stable identity,
 * nothing spends or restores a specific charge, and there are no partial-fill segments. Canonical
 * design (§6.1, §6.3, §25.6) is explicit that Rage is a {@code SCALAR} current/maximum resource
 * presented as pips — inventing an artificial per-charge partition would contradict that design for
 * no representational benefit. {@code totality:rage} is registered as {@code SCALAR}-model, {@code
 * EXTERNAL_ADAPTER}-authority, {@code definitionVersion = 1}.
 *
 * <h2>Resource id vs. legacy backing key</h2>
 * The Resource API resource id ({@link PlayerResourceIds#RAGE}, {@code totality:rage}) and the
 * legacy charge-pool key this adapter reads ({@link BarbarianRageAbility#CHARGE_ID}, {@code
 * totality:barbarian_rage}) are two deliberately different identifiers living in two different
 * concepts — the Resource API's own resource identity, and {@link PlayerChargesComponent}'s internal
 * {@code Map<Identifier, ChargePool>} key. Neither is renamed, migrated, or duplicated; this adapter
 * is simply the bridge between them.
 *
 * <h2>Why the legacy component's own {@code get(ServerPlayer)} is not called here</h2>
 * {@link ChargeComponents#get(ServerPlayer)} throws {@code IllegalStateException} if the component
 * was never attached — a real, intentional behavior for ordinary gameplay callers (every real
 * {@code ServerPlayer} always has it attached, so this should never fire in practice), but exactly
 * the kind of crash this adapter's read-only query contract must never risk. This adapter instead
 * reads {@link ChargeComponents#maybeGet(ServerPlayer)} (a genuinely side-effect-free, non-throwing
 * lookup added in Phase 2E specifically for this purpose, mirroring {@code SpellSlotComponents.maybeGet}'s
 * Phase 2D precedent) and reports {@link ResourceQueryFailureReason#MALFORMED_OWNER_STATE} — not
 * {@code STATE_UNAVAILABLE_ON_THIS_SIDE} — when it is absent for a real server player, exactly like
 * {@code StandardSpellSlotsResourceAdapter}'s reasoning: the component is unconditionally attached at
 * construction, so its absence indicates a broken owner, not an ordinary transitional state.
 *
 * <h2>Presence-aware pool lookup — the genuinely new question this adapter introduces</h2>
 * Unlike {@code SpellSlotComponent}'s fixed ten-entry array (where every player, caster or not, has
 * an identical shape and "all zero" is always a valid non-caster success), {@link
 * PlayerChargesComponent}'s backing map is <b>sparse</b>: a non-Barbarian's map genuinely never
 * receives a {@code barbarian_rage} entry at all, since nothing ever calls {@code registerPool}/
 * {@code ensurePool} for that key unless the player is or becomes a Barbarian. This adapter therefore
 * reads {@link PlayerChargesComponent#getAllPools()} — already a safe, unmodifiable/read-only {@code
 * Map} view ({@code Collections.unmodifiableMap} around the component's live backing map, not a
 * defensive copy: callers cannot mutate the pool set through the returned {@code Map}, though a later
 * legitimate {@code PlayerChargesComponent} mutation would still be reflected by it) — and looks up
 * the key directly,
 * so a present-with-value entry is distinguishable from a genuinely absent one. No new accessor was
 * added to {@link PlayerChargesComponent} for this: {@code getAllPools().get(id)} already returns
 * {@code null} for an absent key exactly as a presence-aware lookup requires, so adding a redundant
 * {@code Optional}-returning method would only duplicate what the existing safe read-only view
 * already provides. A missing entry reports {@link ResourceQueryFailureReason#STATE_UNINITIALIZED} —
 * canonically documented as the correct shape for a source-specific resource "not yet granted" (see
 * the Phase 2E report) — never a fabricated 0/0 success and never {@code MALFORMED_OWNER_STATE}. This
 * is query-time classification only: it says nothing about whether the player could receive Rage
 * later, and this adapter never infers Barbarian ownership or grants anything itself.
 *
 * <h2>Server/client query boundary</h2>
 * Rage charges have no vanilla-native synchronization and no generic Resource API synchronization
 * yet — only a legacy bespoke packet ({@link PlayerChargesComponent#writeSyncPacket}/{@code
 * applySyncPacket}) and the client-side mirror is simply another instance of {@code
 * PlayerChargesComponent} itself, read directly by {@code TotalityClient}'s {@code ISecondaryResource}
 * HUD registration and by {@code ClassTab}'s "CLASS RESOURCE" panel, both left completely untouched
 * by this phase. See {@link ExternalResourceClientMirrorMode#LEGACY_BESPOKE_SYNCHRONIZATION}. This
 * adapter is deliberately never given a code path that reads the client-side component instance for
 * generic-query purposes — a client-side query returns a structured {@link
 * ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE} failure instead.
 */
public final class RageResourceAdapter implements ExternalPlayerResourceAdapter {

    public static final Identifier ID = PlayerResourceIds.RAGE_ADAPTER;

    /**
     * The canonical, adapter-owned unit scale for Rage charges: the legacy owner ({@link
     * PlayerChargesComponent}'s {@code ChargePool}) stores discrete charge counts, which are already
     * whole numbers with no fixed-point scaling. This is a property of the legacy store itself, not
     * of whatever a {@link PlayerResourceDefinition} happens to declare — the adapter always
     * produces a snapshot at this scale regardless of the queried definition's own {@code
     * unitScale()}, so a definition accidentally misconfigured with a different scale cannot cause
     * this adapter to silently relabel unconverted charge counts as if they were expressed in that
     * scale. Keeping the two independent is exactly what lets {@link
     * zcylas.totality.api.rpg.resources.PlayerResourceService}'s unit-scale mismatch validation
     * remain a meaningful check rather than a tautology — the same reasoning
     * {@code StandardSpellSlotsResourceAdapter.UNIT_SCALE} already established in Phase 2D.
     */
    public static final long UNIT_SCALE = 1L;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public ResourceQueryResult snapshot(Player player, PlayerResourceDefinition definition) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, definition.id());
        }
        Optional<PlayerChargesComponent> component = ChargeComponents.maybeGet(serverPlayer);
        if (component.isEmpty()) {
            // See the class Javadoc's "Why the legacy component's own get(ServerPlayer) is not
            // called here" section for why this is MALFORMED_OWNER_STATE rather than
            // STATE_UNAVAILABLE_ON_THIS_SIDE.
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, definition.id());
        }
        return resolve(definition.id(), component.get());
    }

    /**
     * Builds the scalar snapshot from the component's already-stored {@code ChargePool} for {@link
     * BarbarianRageAbility#CHARGE_ID}, package-visible so a test can prove it never invokes
     * consumption, restoration, recalculation, or synchronization — using a real {@link
     * PlayerChargesComponent} (constructible with a {@code null} {@code ServerPlayer}, matching
     * every prior legacy-component adapter's precedent) rather than requiring the Minecraft runtime
     * just to exercise this logic.
     *
     * <p>Reads only {@link PlayerChargesComponent#getAllPools()} — a pure, already-safe read-only
     * map view — and never calls {@code registerPool}, {@code ensurePool}, {@code updatePoolMax},
     * {@code setMax}, {@code consume}, {@code restore}, {@code restoreAll}, or {@code onRest}. Never
     * inspects {@code rechargeType}/{@code rechargeAmount} (Rest-recovery metadata, not part of this
     * resource's current/maximum shape), the active Rage duration, status-effect amplifier, ability
     * toggle state, combat timer, or Barbarian class level — none of those are read anywhere in this
     * method.
     *
     * @return a {@link ResourceQueryResult.Failure} naming {@link ResourceQueryFailureReason#STATE_UNINITIALIZED}
     *         if no {@code barbarian_rage} entry exists in the pool map at all (Rage not yet granted
     *         for this player — see the class Javadoc); {@link ResourceQueryFailureReason#MALFORMED_OWNER_STATE}
     *         if a present entry's stored current is negative, stored maximum is negative, or current
     *         exceeds maximum — malformed legacy owner state (e.g. corrupted persisted NBT) is never
     *         silently clamped or repaired; otherwise a {@link ResourceQueryResult.Success} wrapping
     *         a {@link ResourceSnapshot} with the pool's current/maximum exactly as stored (including
     *         a present {@code 0}/{@code 0} entry, which is a valid success — see the class Javadoc),
     *         always expressed at {@link #UNIT_SCALE}.
     */
    static ResourceQueryResult resolve(Identifier resourceId, PlayerChargesComponent component) {
        PlayerChargesComponent.ChargePool pool = component.getAllPools().get(BarbarianRageAbility.CHARGE_ID);
        if (pool == null) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNINITIALIZED, resourceId);
        }
        int current = pool.current();
        int maximum = pool.max();
        if (current < 0 || maximum < 0 || current > maximum) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, resourceId);
        }
        return new ResourceQueryResult.Success(new ResourceSnapshot(resourceId, current, maximum, UNIT_SCALE));
    }

    @Override
    public Set<ExternalResourceOperationSupport> supportedOperations() {
        return Set.of(ExternalResourceOperationSupport.QUERY);
    }

    @Override
    public ExternalResourceClientMirrorMode clientMirrorMode() {
        return ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION;
    }

    public static final RageResourceAdapter INSTANCE = new RageResourceAdapter();
}
