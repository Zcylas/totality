package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.magic.spell.SpellSlotComponent;
import zcylas.totality.api.magic.spell.SpellSlotComponents;
import zcylas.totality.api.rpg.resources.PartitionedResourceSnapshot;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;

import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * <b>Transitional, query-only</b> adapter over the legacy-authoritative standard (non-Pact-Magic)
 * spell-slot store, {@link SpellSlotComponent}, via {@link SpellSlotComponents}. {@link
 * SpellSlotComponent} remains the sole authoritative owner of every standard spell-slot gameplay
 * operation (consumption, Long Rest restoration, multiclass maximum recalculation via {@code
 * SpellSlotRecalculator}, persistence, its own bespoke sync packet) — this adapter changes none of
 * that; it only ever reads, for exactly as long as this transitional phase lasts. See the Phase 2D
 * report's "Legacy spell-slot characterization findings" section for the full audit this class is
 * built from.
 *
 * <h2>Why {@link ResourceQueryResult.PartitionedSuccess}, not {@link ResourceQueryResult.Success}</h2>
 * {@code totality:spell_slots} is registered as {@code PARTITIONED_POOL}-model, {@code
 * EXTERNAL_ADAPTER}-authority ({@code definitionVersion = 1}; see {@code ProductionResourceDefinitions}).
 * Ten stable partitions — spell levels 1 through 10, matching {@link SpellSlotComponent#MAX_SPELL_LEVEL}
 * exactly — are always present in every successful snapshot, in ascending order, even where a
 * level's maximum is 0 (a non-caster, or a caster who has not yet gained slots at that level). A
 * future phase migrating this resource's actual storage onto {@code GENERIC_COMPONENT} requires an
 * explicit {@code definitionVersion} increase and a real migration step — never a silent structural
 * hot-swap of what {@code totality:spell_slots} means.
 *
 * <h2>Why the legacy component's own {@code get(ServerPlayer)} is not called here</h2>
 * {@link SpellSlotComponents#get(ServerPlayer)} throws {@code IllegalStateException} if the
 * component was never attached — a real, intentional behavior for ordinary gameplay callers (every
 * real {@code ServerPlayer} always has it attached, so this should never fire in practice), but
 * exactly the kind of crash this adapter's read-only query contract must never risk. This adapter
 * instead reads {@link SpellSlotComponents#maybeGet(ServerPlayer)} (a genuinely side-effect-free,
 * non-throwing lookup added in Phase 2D specifically for this purpose) and reports {@link
 * ResourceQueryFailureReason#MALFORMED_OWNER_STATE} — not {@code STATE_UNAVAILABLE_ON_THIS_SIDE} —
 * when it is absent for a real server player. This deliberately differs from {@code
 * ManaResourceAdapter}/{@code StaminaResourceAdapter}'s choice of {@code STATE_UNAVAILABLE_ON_THIS_SIDE}
 * for the same "component missing" case: unlike Mana/Stamina's legacy component, {@link
 * SpellSlotComponent} is unconditionally attached to every {@code ServerPlayer} at construction (see
 * {@code MixinServerPlayer} → {@code PlayerComponentEvents.attachComponentsTo}), so its absence for a
 * real server player is not an ordinary, expected transitional state — it indicates something has
 * gone wrong with the owner itself, exactly the shape {@code MALFORMED_OWNER_STATE} exists for. This
 * is a final, deliberate Phase 2D design decision, not an oversight.
 *
 * <h2>No initialization sentinel — all-zero state is a valid success</h2>
 * Unlike Mana/Stamina's {@code -1} sentinel plus {@code isManaInitialized()}/{@code isStaminaInitialized()},
 * {@link SpellSlotComponent} has no concept of "never recalculated" distinct from "genuinely zero at
 * every level" — both produce identical all-zero {@code maxSlots}/{@code usedSlots} arrays. This
 * adapter therefore never returns {@link ResourceQueryFailureReason#STATE_UNINITIALIZED}: an
 * attached component whose every level reads 0/0 is a valid, successful ten-partition snapshot,
 * exactly as legitimate as a permanent non-caster class. This is a final, deliberate Phase 2D design
 * decision (see the Phase 2D report's "all-zero state" section for the full reasoning) — future code
 * must not reintroduce an inferred-initialization check here by consulting class/caster data.
 *
 * <h2>Server/client query boundary</h2>
 * Standard spell slots have no vanilla-native synchronization and no generic Resource API
 * synchronization yet — only a legacy bespoke packet and a legacy client-side cache ({@code
 * ClientSpellSlotManager}, fed by {@link SpellSlotComponent#writeSyncPacket}/{@code applySyncPacket}),
 * read directly by {@code SpellRadialScreen} and left completely untouched by this phase. See {@link
 * ExternalResourceClientMirrorMode#LEGACY_BESPOKE_SYNCHRONIZATION}. This adapter is deliberately never
 * given a code path that imports or reads {@code ClientSpellSlotManager} — a client-side query
 * returns a structured {@link ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE} failure
 * instead.
 */
public final class StandardSpellSlotsResourceAdapter implements ExternalPlayerResourceAdapter {

    public static final Identifier ID = PlayerResourceIds.SPELL_SLOTS_ADAPTER;

    /**
     * The canonical, adapter-owned unit scale for standard spell slots: the legacy owner
     * ({@link SpellSlotComponent}) stores discrete slot counts, which are already whole numbers with
     * no fixed-point scaling. This is a property of the legacy store itself, not of whatever a
     * {@link PlayerResourceDefinition} happens to declare — the adapter always produces a snapshot
     * at this scale regardless of the queried definition's own {@code unitScale()}, so a definition
     * accidentally misconfigured with a different scale cannot cause this adapter to silently
     * relabel unconverted slot counts as if they were expressed in that scale. Keeping the two
     * independent is exactly what lets {@link zcylas.totality.api.rpg.resources.PlayerResourceService}'s
     * unit-scale mismatch validation ({@code validatePartitionedExternalSnapshot}) remain a
     * meaningful check rather than a tautology.
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
        Optional<SpellSlotComponent> component = SpellSlotComponents.maybeGet(serverPlayer);
        if (component.isEmpty()) {
            // See the class Javadoc's "Why the legacy component's own get(ServerPlayer) is not
            // called here" section for why this is MALFORMED_OWNER_STATE rather than
            // STATE_UNAVAILABLE_ON_THIS_SIDE.
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, definition.id());
        }
        return resolve(definition.id(), component.get());
    }

    /**
     * Builds the ten-partition snapshot from the component's already-stored {@code maxSlots}/{@code
     * usedSlots} arrays, package-visible so a test can prove it never invokes recalculation,
     * synchronization, or mutation — using a real {@link SpellSlotComponent} (constructible with a
     * {@code null} {@code ServerPlayer}, matching {@code ManaResourceAdapter}/{@code
     * StaminaResourceAdapter}'s precedent for their own legacy component) rather than requiring the
     * Minecraft runtime just to exercise this logic.
     *
     * <p>Reads only {@link SpellSlotComponent#getMax(int)}/{@link SpellSlotComponent#getUsed(int)}
     * for levels 1 through {@link SpellSlotComponent#MAX_SPELL_LEVEL} — both pure array reads with
     * no side effect — and never calls {@code recalculate}, {@code useSlot}, {@code restoreAll},
     * {@code restoreSome}, or anything that could trigger {@code sync()}.
     *
     * @return a {@link ResourceQueryResult.Failure} naming {@link ResourceQueryFailureReason#MALFORMED_OWNER_STATE}
     *         the moment any level's stored maximum is negative, stored used count is negative, or
     *         used exceeds maximum — malformed legacy owner state (e.g. corrupted persisted NBT) is
     *         never silently clamped or repaired. Otherwise a {@link ResourceQueryResult.PartitionedSuccess}
     *         containing all ten levels, in ascending order, with {@code current = maximum - used}
     *         for each — including levels where both are 0 (see the class Javadoc's "all-zero state" section) —
     *         always expressed at {@link #UNIT_SCALE}, never at the queried definition's own
     *         {@code unitScale()} (see the class Javadoc's "canonical, adapter-owned unit scale" field doc).
     */
    static ResourceQueryResult resolve(Identifier resourceId, SpellSlotComponent component) {
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> partitions = new TreeMap<>();
        for (int level = 1; level <= SpellSlotComponent.MAX_SPELL_LEVEL; level++) {
            int maximum = component.getMax(level);
            int used = component.getUsed(level);
            if (maximum < 0 || used < 0 || used > maximum) {
                return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, resourceId);
            }
            long current = (long) maximum - used;
            partitions.put(level, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(current, maximum));
        }
        return new ResourceQueryResult.PartitionedSuccess(
                new PartitionedResourceSnapshot(resourceId, partitions, UNIT_SCALE));
    }

    @Override
    public Set<ExternalResourceOperationSupport> supportedOperations() {
        return Set.of(ExternalResourceOperationSupport.QUERY);
    }

    @Override
    public ExternalResourceClientMirrorMode clientMirrorMode() {
        return ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION;
    }

    public static final StandardSpellSlotsResourceAdapter INSTANCE = new StandardSpellSlotsResourceAdapter();
}
