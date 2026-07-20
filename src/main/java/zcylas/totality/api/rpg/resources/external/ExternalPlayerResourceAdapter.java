package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;

import java.util.Set;

/**
 * Query-time view over a resource whose mutable state is owned by another authoritative system —
 * either a vanilla system (Health/Combat, Food/Hunger, air supply) or a Totality-owned legacy store
 * ({@code PlayerResourceComponent}, transitionally, ahead of a future generic-storage migration).
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.5.
 *
 * <p>This is now implemented by a genuinely mixed set of adapters, not just the original Phase 2A
 * pair — do not read this Javadoc as describing only Health/Food:
 * <ul>
 *     <li>{@code HealthResourceAdapter}, {@code FoodResourceAdapter}, {@code BreathResourceAdapter}
 *         (Phase 2A/2B) read vanilla state that is already synchronized to the client through
 *         vanilla's own native packets ({@link ExternalResourceClientMirrorMode#NATIVE_SYNCHRONIZATION}) —
 *         these can answer a query on <b>either</b> the server or the client, using the exact same
 *         code path.</li>
 *     <li>{@code ManaResourceAdapter}, {@code StaminaResourceAdapter} (Phase 2C) are
 *         <b>transitional, server-only</b> query adapters over a legacy Totality store that has no
 *         vanilla-native or generic Resource API client synchronization yet
 *         ({@link ExternalResourceClientMirrorMode#LEGACY_BESPOKE_SYNCHRONIZATION}) — these can only
 *         answer on the server, and correctly decline (see below) when queried from the client.</li>
 * </ul>
 *
 * <p><b>Deliberate deviation from the canonical {@code ServerPlayer}-typed interface shown in the
 * design document:</b> this interface remains typed against the common {@link Player} superclass
 * (not {@code ServerPlayer}) precisely so the client-capable adapters above can serve both the
 * server and the client HUD through one query path, with no second client-only query surface or
 * duplicate packet. Individual adapters that cannot actually answer on a given side (today: every
 * transitional legacy-store adapter, on the client) are responsible for checking
 * {@code player instanceof ServerPlayer} themselves and rejecting the unsupported side with
 * {@link ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE} — the interface itself makes no
 * promise that every adapter can answer on every side. This remains safe specifically because every
 * adapter today is query-only — a future mutating adapter (restore/drain/set) would need to
 * re-narrow to {@code ServerPlayer} for those operations, since only the server may authoritatively
 * mutate.
 */
public interface ExternalPlayerResourceAdapter {

    /** Matches the adapter identifier a {@link PlayerResourceDefinition} references via {@code externalAdapterId()}. */
    Identifier id();

    /**
     * Builds a read-only snapshot from the authoritative owner's current state. Must never mutate
     * the owner's state, instantiate any Resource API player state, or send a packet.
     *
     * <p><b>Result shape depends on the queried {@code definition}'s {@link
     * zcylas.totality.api.rpg.resources.ResourceModel}</b> (introduced in Phase 2D, alongside the
     * first {@code PARTITIONED_POOL} production resource, {@code totality:spell_slots}): a
     * {@link zcylas.totality.api.rpg.resources.ResourceModel#SCALAR} definition expects a
     * {@link ResourceQueryResult.Success} wrapping a scalar {@code ResourceSnapshot} (one
     * current/maximum pair); a {@link zcylas.totality.api.rpg.resources.ResourceModel#PARTITIONED_POOL}
     * definition expects a {@link ResourceQueryResult.PartitionedSuccess} wrapping a
     * {@code PartitionedResourceSnapshot} (one current/maximum pair per integer partition). An
     * adapter that only ever serves one model only ever needs to construct the one matching shape —
     * every existing Phase 2A/2B/2C adapter (Health, Food, Breath, Mana, Stamina) is {@code SCALAR}-only
     * and requires no change. {@link zcylas.totality.api.rpg.resources.PlayerResourceService} treats a
     * shape that does not match the definition's declared model (a {@code PARTITIONED_POOL} definition
     * whose adapter returned scalar {@code Success}, or vice versa) as {@code CORRUPT_ADAPTER_SNAPSHOT}
     * — it never coerces one shape into the other.
     *
     * <p>Returns a {@link ResourceQueryResult.Success} or {@link ResourceQueryResult.PartitionedSuccess}
     * wrapping the snapshot, or a {@link ResourceQueryResult.Failure} — never {@code null}, and never a
     * fabricated/clamped placeholder — when the owner's actual state cannot be represented as a valid
     * snapshot right now. {@link zcylas.totality.api.rpg.resources.PlayerResourceService} trusts a returned
     * {@code Failure}'s reason directly only after confirming <b>both</b> (a) its
     * {@code resourceId} matches the definition actually queried, and (b) its
     * {@link ResourceQueryFailureReason} is one of the three an adapter is actually permitted to
     * determine on its own authority — <b>only these three may ever be returned here</b>:
     * <ul>
     *     <li>{@link ResourceQueryFailureReason#MALFORMED_OWNER_STATE} — the owner's state is
     *         genuinely unrepresentable (e.g. {@code BreathResourceAdapter}'s non-positive vanilla
     *         maximum case, or {@code ManaResourceAdapter}/{@code StaminaResourceAdapter}'s
     *         non-positive legacy-manager maximum case).</li>
     *     <li>{@link ResourceQueryFailureReason#STATE_UNINITIALIZED} — a legacy store that has
     *         never been initialized for this player ({@code ManaResourceAdapter}/
     *         {@code StaminaResourceAdapter}'s {@code -1} sentinel).</li>
     *     <li>{@link ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE} — this adapter
     *         cannot answer on the calling side at all (a transitional legacy-store adapter queried
     *         from the client, before generic client synchronization exists).</li>
     * </ul>
     * Every other {@code ResourceQueryFailureReason} value belongs to the registry/service/generic-
     * state layers ({@code RESOURCE_NOT_REGISTERED}, {@code ADAPTER_NOT_REGISTERED},
     * {@code STATE_NOT_INSTANTIATED}, {@code UNSUPPORTED_MODEL}, {@code OPERATION_UNSUPPORTED},
     * {@code MAXIMUM_UNAVAILABLE}, {@code CORRUPT_ADAPTER_SNAPSHOT} itself) — an adapter has no
     * authority or visibility to determine any of those about itself, and
     * {@code PlayerResourceService} does not trust one at face value even if returned: it is turned
     * into {@code CORRUPT_ADAPTER_SNAPSHOT} instead (correction pass, Phase 2C). If a future adapter
     * genuinely needs to express a failure mode none of the three current reasons covers, extend
     * this contract deliberately (a new named {@code ResourceQueryFailureReason}, documented and
     * added to {@code PlayerResourceService}'s allowed-reasons set) rather than reusing one of the
     * unrelated service-owned reasons above to approximate it.
     *
     * <p>This is a real, typed "I cannot answer, and here specifically is why" signal, not
     * {@code null} used as ordinary control flow. Reusing {@link ResourceQueryResult} — the same
     * sealed type {@link zcylas.totality.api.rpg.resources.PlayerResourceService#query} itself
     * returns — rather than inventing a third adapter-specific result wrapper is a deliberate
     * "smallest coherent extension" (Phase 2B's own instruction, applied again in Phase 2C): the
     * shape needed is exactly "a snapshot xor a structured reason," which {@link ResourceQueryResult}
     * already is.
     */
    ResourceQueryResult snapshot(Player player, PlayerResourceDefinition definition);

    Set<ExternalResourceOperationSupport> supportedOperations();

    ExternalResourceClientMirrorMode clientMirrorMode();
}
