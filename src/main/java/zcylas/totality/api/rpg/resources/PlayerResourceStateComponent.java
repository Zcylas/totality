package zcylas.totality.api.rpg.resources;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.resources.state.OrphanedResourceState;
import zcylas.totality.api.rpg.resources.state.PartitionedResourceState;
import zcylas.totality.api.rpg.resources.state.ResourceState;
import zcylas.totality.api.rpg.resources.state.ScalarResourceState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntToLongFunction;

/**
 * Generic, registry-driven player resource state. Registered under {@code totality:resource_state}
 * (see {@link ResourceStateComponents}) — a new identifier, distinct from the legacy
 * {@code totality:resources} component ({@link PlayerResourceComponent}), which remains the sole
 * authority for Stamina and Mana until an explicit later migration.
 *
 * {@link PlayerResourceRegistry#INSTANCE} registers two distinct authority categories of
 * production definition. The seven {@code EXTERNAL_ADAPTER}-authority resources (Health, Food,
 * Breath, Mana, Stamina, Spell Slots, Rage) can never gain an entry here at all:
 * {@link #instantiateScalar}/{@link #instantiatePartitioned} actively reject any id whose
 * registered definition is {@code EXTERNAL_ADAPTER}-authority, and stale/malformed persisted data
 * for such an id is quarantined as an orphan rather than restored live (see
 * {@code readLiveEntry}/{@code readOrphanEntry}) — those seven can never gain a duplicate,
 * out-of-sync copy of their state here, and keep using their existing storage (or, for Breath, no
 * storage at all yet) until an explicit later migration. The three {@code GENERIC_COMPONENT}-
 * authority resources added by the dormant Resource Registration pass (Thirst, Sanity, Ki) are, by
 * contrast, legitimately instantiable here — but nothing in production code currently calls
 * {@code instantiateScalar} for any of them (no grant provider exists yet), so they too remain
 * absent from every ordinary player's state today, for a different reason than the seven above:
 * not rejected, simply never granted. This component does not affect Stamina, Mana, Rage, or
 * Breath, which keep using their existing storage (or, for Breath, no storage at all yet) until an
 * explicit later migration. Nothing in production code calls {@link #sync()} yet, so this
 * component never sends a network packet.
 *
 * Queries never auto-instantiate state (canonical §4.4: "A read-only query must not silently
 * grant or instantiate a resource"). State is only ever created via the explicit
 * {@code instantiateScalar}/{@code instantiatePartitioned} calls, which nothing in production code
 * invokes automatically yet — a future grant provider is what will call them for
 * {@code GENERIC_COMPONENT} resources (Thirst, Sanity, Ki today; more as future migrations land).
 */
public final class PlayerResourceStateComponent implements SyncedComponent, CopyableComponent<PlayerResourceStateComponent> {

    /**
     * Schema version 2 (bumped from 1 during the Phase 1 pre-commit correction pass): the orphan
     * entry format ({@code Orphaned_i_*}) now also carries a scalar regeneration remainder and
     * uses the union of the current/overflow partition key sets rather than only the current map's
     * keys. Safe to bump without a migration path — no real resource has written Phase 1 state
     * under schema 1 yet (this whole component is still inert; see the class Javadoc).
     */
    private static final int SCHEMA_VERSION = 2;

    private final Map<Identifier, ResourceState> states = new LinkedHashMap<>();
    private final Map<Identifier, OrphanedResourceState> orphanedStates = new LinkedHashMap<>();
    private final ServerPlayer player;

    public PlayerResourceStateComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Queries (never auto-instantiate) ────────────────────────────────────────

    public boolean hasState(Identifier id) {
        return states.containsKey(id);
    }

    public Optional<ScalarResourceState> getScalar(Identifier id) {
        ResourceState state = states.get(id);
        return state instanceof ScalarResourceState scalar ? Optional.of(scalar) : Optional.empty();
    }

    public Optional<PartitionedResourceState> getPartitioned(Identifier id) {
        ResourceState state = states.get(id);
        return state instanceof PartitionedResourceState partitioned ? Optional.of(partitioned) : Optional.empty();
    }

    public Set<Identifier> instantiatedResourceIds() {
        return Collections.unmodifiableSet(states.keySet());
    }

    public Set<Identifier> orphanedResourceIds() {
        return Collections.unmodifiableSet(orphanedStates.keySet());
    }

    // ── Explicit instantiation — nothing in this patch calls these automatically ─

    public ScalarResourceState instantiateScalar(Identifier id, long initialCurrentUnits) {
        rejectExternalAuthority(id, "instantiateScalar");
        ResourceState existing = states.get(id);
        if (existing != null) {
            if (existing instanceof ScalarResourceState scalar) return scalar;
            throw new IllegalStateException(id + " is already instantiated as " + existing.model());
        }
        ScalarResourceState created = new ScalarResourceState(initialCurrentUnits);
        states.put(id, created);
        return created;
    }

    public PartitionedResourceState instantiatePartitioned(Identifier id) {
        rejectExternalAuthority(id, "instantiatePartitioned");
        ResourceState existing = states.get(id);
        if (existing != null) {
            if (existing instanceof PartitionedResourceState partitioned) return partitioned;
            throw new IllegalStateException(id + " is already instantiated as " + existing.model());
        }
        PartitionedResourceState created = new PartitionedResourceState();
        states.put(id, created);
        return created;
    }

    /**
     * An {@code EXTERNAL_ADAPTER} definition must never become live generic component state —
     * its authoritative owner is the adapter (vanilla Health/Food), not this component. Silently
     * allowing instantiation here would create exactly the duplicate-authority bug the Phase 2A
     * task's "Prevent duplicate external state" section exists to close. Unregistered ids are
     * allowed through unchanged (a resource not yet known to {@link PlayerResourceRegistry#INSTANCE},
     * e.g. in an isolated test registry) — this guard only fires for a definition it can actually see.
     */
    private static void rejectExternalAuthority(Identifier id, String methodName) {
        if (isRegisteredExternalAdapterAuthority(id)) {
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(id).orElseThrow();
            throw new IllegalArgumentException(
                    id + " is EXTERNAL_ADAPTER-authority (adapter " + definition.externalAdapterId().orElse(null)
                            + ") — " + methodName + " must not create generic component state for it");
        }
    }

    /**
     * True only when {@code id} is registered in {@link PlayerResourceRegistry#INSTANCE} AND that
     * definition is {@code EXTERNAL_ADAPTER}-authority. Shared by {@link #rejectExternalAuthority}
     * and the persisted-data quarantine checks in {@link #readLiveEntry}/{@link #readOrphanEntry}
     * so all three enforce the exact same rule. Package-visible so this one decision point can be
     * unit-tested directly without needing to fabricate a {@code ValueInput}/{@code ValueOutput}.
     */
    static boolean isRegisteredExternalAdapterAuthority(Identifier id) {
        return PlayerResourceRegistry.INSTANCE.get(id)
                .map(definition -> definition.stateAuthority() == ResourceStateAuthority.EXTERNAL_ADAPTER)
                .orElse(false);
    }

    public void removeState(Identifier id) {
        states.remove(id);
    }

    // ── Sync — nothing in production code calls sync() during this patch ────────

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            ResourceStateComponents.RESOURCE_STATE.sync((ComponentProvider) player);
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        // Defensive filter: `states` should never contain an EXTERNAL_ADAPTER-authority entry —
        // every entry path (instantiateScalar/instantiatePartitioned, readLiveEntry,
        // readOrphanEntry, copyFrom, applySyncPacket) already rejects or quarantines one — but this
        // does not trust that invariant blindly. A future bug or corrupted in-memory state must not
        // be able to put Health/Food on the wire as if the generic component were authoritative for
        // them; native vanilla synchronization remains their only wire protocol.
        List<Map.Entry<Identifier, ResourceState>> toSend = states.entrySet().stream()
                .filter(entry -> !isRegisteredExternalAdapterAuthority(entry.getKey()))
                .toList();
        buf.writeInt(toSend.size());
        for (Map.Entry<Identifier, ResourceState> entry : toSend) {
            buf.writeUtf(entry.getKey().toString());
            buf.writeUtf(entry.getValue().model().name());
            writeStatePayload(buf, entry.getValue());
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        states.clear();
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.parse(buf.readUtf());
            ResourceModel model = ResourceModel.valueOf(buf.readUtf());
            // Always fully consume this entry's payload bytes first, regardless of what happens
            // next — buffer alignment for every later entry in this same packet must not depend on
            // whether this particular entry turns out to be external-authority.
            ResourceState state = readStatePayload(buf, model);
            if (isRegisteredExternalAdapterAuthority(id)) {
                // A sender should never produce this (writeSyncPacket filters it too), but a stale
                // client, a modified server, or a future bug must not be able to make Health/Food
                // live generic state via the network path either. Sync payloads are transient
                // (never persisted), so this is discarded outright rather than quarantined into
                // `orphanedStates` — quarantining is reserved for data actually read from NBT.
                Totality.LOGGER.warn(
                        "[ResourceState] applySyncPacket received live generic state for "
                                + "EXTERNAL_ADAPTER-authority {} — discarding rather than treating it as authoritative", id);
                continue;
            }
            states.put(id, state);
        }
    }

    private static void writeStatePayload(RegistryFriendlyByteBuf buf, ResourceState state) {
        if (state instanceof ScalarResourceState scalar) {
            buf.writeLong(scalar.currentUnits());
            buf.writeLong(scalar.overflowUnits());
        } else if (state instanceof PartitionedResourceState partitioned) {
            Set<Integer> partitions = partitioned.partitions();
            buf.writeInt(partitions.size());
            for (int partition : partitions) {
                buf.writeInt(partition);
                buf.writeLong(partitioned.getCurrent(partition));
                buf.writeLong(partitioned.getOverflow(partition));
            }
        }
    }

    private static ResourceState readStatePayload(RegistryFriendlyByteBuf buf, ResourceModel model) {
        if (model == ResourceModel.SCALAR) {
            long current = buf.readLong();
            long overflow = buf.readLong();
            return new ScalarResourceState(current, overflow, 0L);
        }
        PartitionedResourceState state = new PartitionedResourceState();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            int partition = buf.readInt();
            state.setCurrent(partition, buf.readLong());
            state.setOverflow(partition, buf.readLong());
        }
        return state;
    }

    // ── Persistence ──────────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("SchemaVersion", SCHEMA_VERSION);
        // Same defensive filter as writeSyncPacket: `states` should never actually contain an
        // EXTERNAL_ADAPTER-authority entry, but persistence output does not trust that blindly
        // either — corrupted in-memory state must not be able to write a duplicate Health/Food
        // entry to NBT.
        List<Map.Entry<Identifier, ResourceState>> liveToWrite = states.entrySet().stream()
                .filter(entry -> !isRegisteredExternalAdapterAuthority(entry.getKey()))
                .toList();
        output.putInt("ResourceCount", liveToWrite.size());
        int i = 0;
        for (Map.Entry<Identifier, ResourceState> entry : liveToWrite) {
            writeLiveEntry(output, "Resource_" + i, entry.getKey(), entry.getValue());
            i++;
        }
        output.putInt("OrphanedCount", orphanedStates.size());
        i = 0;
        for (Map.Entry<Identifier, OrphanedResourceState> entry : orphanedStates.entrySet()) {
            writeOrphanEntry(output, "Orphaned_" + i, entry.getKey(), entry.getValue());
            i++;
        }
    }

    private static void writeLiveEntry(ValueOutput output, String prefix, Identifier id, ResourceState state) {
        output.putString(prefix + "_id", id.toString());
        output.putString(prefix + "_model", state.model().name());
        if (state instanceof ScalarResourceState scalar) {
            output.putLong(prefix + "_current", scalar.currentUnits());
            output.putLong(prefix + "_overflow", scalar.overflowUnits());
            output.putLong(prefix + "_remainder", scalar.regenerationRemainder());
        } else if (state instanceof PartitionedResourceState partitioned) {
            writePartitionMap(output, prefix, partitioned.partitions(), partitioned::getCurrent, partitioned::getOverflow);
        }
    }

    private static void writeOrphanEntry(ValueOutput output, String prefix, Identifier id, OrphanedResourceState orphan) {
        output.putString(prefix + "_id", id.toString());
        output.putString(prefix + "_model", orphan.model().name());
        // orphan.partitions() is the union of the current/overflow key sets, so an overflow-only
        // partition is written even though it has no current-value entry (see PartitionedResourceState
        // and OrphanedResourceState's own partitions() Javadoc for why this matters).
        writePartitionMap(output, prefix, orphan.partitions(), orphan::getCurrent, orphan::getOverflow);
        if (orphan.model() == ResourceModel.SCALAR) {
            output.putLong(prefix + "_remainder", orphan.scalarRegenerationRemainder());
        }
    }

    private static void writePartitionMap(
            ValueOutput output,
            String prefix,
            Set<Integer> partitions,
            IntToLongFunction currentFn,
            IntToLongFunction overflowFn
    ) {
        output.putInt(prefix + "_partitionCount", partitions.size());
        int j = 0;
        for (int partition : partitions) {
            output.putInt(prefix + "_partition_" + j + "_key", partition);
            output.putLong(prefix + "_partition_" + j + "_current", currentFn.applyAsLong(partition));
            output.putLong(prefix + "_partition_" + j + "_overflow", overflowFn.applyAsLong(partition));
            j++;
        }
    }

    @Override
    public void readData(ValueInput input) {
        states.clear();
        orphanedStates.clear();
        int resourceCount = input.getIntOr("ResourceCount", 0);
        for (int i = 0; i < resourceCount; i++) {
            readLiveEntry(input, "Resource_" + i);
        }
        int orphanedCount = input.getIntOr("OrphanedCount", 0);
        for (int i = 0; i < orphanedCount; i++) {
            readOrphanEntry(input, "Orphaned_" + i);
        }
    }

    /** Reads a {@code Resource_i} entry, written by {@link #writeLiveEntry}. */
    private void readLiveEntry(ValueInput input, String prefix) {
        try {
            String rawId = input.getStringOr(prefix + "_id", "");
            String rawModel = input.getStringOr(prefix + "_model", "");
            if (rawId.isEmpty() || rawModel.isEmpty()) return;
            Identifier id = Identifier.parse(rawId);
            ResourceModel persistedModel = ResourceModel.valueOf(rawModel);

            Optional<PlayerResourceDefinition> definition = PlayerResourceRegistry.INSTANCE.get(id);
            if (definition.isEmpty() || definition.get().model() != persistedModel) {
                if (definition.isPresent()) {
                    Totality.LOGGER.warn(
                            "[ResourceState] {} persisted as {} but registered as {} — preserving as orphan",
                            id, persistedModel, definition.get().model());
                }
                orphanedStates.put(id, readLiveFormatAsOrphan(input, prefix, persistedModel));
                return;
            }
            if (isRegisteredExternalAdapterAuthority(id)) {
                // Stale/malformed generic-component NBT for a resource that is now (or always was)
                // EXTERNAL_ADAPTER-authority — e.g. leftover data from before Health/Food were
                // registered, or a corrupted save. It must never override the adapter as live
                // generic state; quarantine it for diagnostics instead (canonical §5.4).
                Totality.LOGGER.warn(
                        "[ResourceState] {} persisted as generic component state but is now EXTERNAL_ADAPTER-authority "
                                + "— quarantining as orphan rather than treating it as authoritative", id);
                orphanedStates.put(id, readLiveFormatAsOrphan(input, prefix, persistedModel));
                return;
            }

            states.put(id, readLiveFormat(input, prefix, persistedModel));
        } catch (Exception ex) {
            Totality.LOGGER.warn("[ResourceState] Failed to read '{}' — skipping malformed entry", prefix, ex);
        }
    }

    /** Reads an {@code Orphaned_i} entry, written by {@link #writeOrphanEntry} (always partition-map shaped). */
    private void readOrphanEntry(ValueInput input, String prefix) {
        try {
            String rawId = input.getStringOr(prefix + "_id", "");
            String rawModel = input.getStringOr(prefix + "_model", "");
            if (rawId.isEmpty() || rawModel.isEmpty()) return;
            Identifier id = Identifier.parse(rawId);
            ResourceModel model = ResourceModel.valueOf(rawModel);

            OrphanedResourceState orphan = new OrphanedResourceState(model);
            readPartitionMapInto(input, prefix,
                    (partition, value) -> orphan.putCurrent(partition, value),
                    (partition, value) -> orphan.putOverflow(partition, value));
            if (model == ResourceModel.SCALAR) {
                orphan.setScalarRegenerationRemainder(input.getLongOr(prefix + "_remainder", 0L));
            }

            Optional<PlayerResourceDefinition> definition = PlayerResourceRegistry.INSTANCE.get(id);
            boolean restorable = definition.isPresent()
                    && definition.get().model() == model
                    && !isRegisteredExternalAdapterAuthority(id);
            if (restorable) {
                // Definition returned with a compatible, non-external model — restore into live
                // state (canonical §5.4).
                states.put(id, orphanToLiveState(orphan));
            } else {
                if (definition.isPresent() && definition.get().model() == model) {
                    Totality.LOGGER.warn(
                            "[ResourceState] Orphan {} matches a now-EXTERNAL_ADAPTER-authority definition "
                                    + "— leaving quarantined rather than restoring as live generic state", id);
                }
                orphanedStates.put(id, orphan);
            }
        } catch (Exception ex) {
            Totality.LOGGER.warn("[ResourceState] Failed to read orphan '{}' — skipping malformed entry", prefix, ex);
        }
    }

    private static ResourceState readLiveFormat(ValueInput input, String prefix, ResourceModel model) {
        if (model == ResourceModel.SCALAR) {
            long current = input.getLongOr(prefix + "_current", 0L);
            long overflow = input.getLongOr(prefix + "_overflow", 0L);
            long remainder = input.getLongOr(prefix + "_remainder", 0L);
            return new ScalarResourceState(current, overflow, remainder);
        }
        PartitionedResourceState state = new PartitionedResourceState();
        readPartitionMapInto(input, prefix, state::setCurrent, state::setOverflow);
        return state;
    }

    /** Reads a just-orphaned entry from the LIVE format (matching {@link #writeLiveEntry}'s fields). */
    private static OrphanedResourceState readLiveFormatAsOrphan(ValueInput input, String prefix, ResourceModel model) {
        OrphanedResourceState orphan = new OrphanedResourceState(model);
        if (model == ResourceModel.SCALAR) {
            orphan.putCurrent(0, input.getLongOr(prefix + "_current", 0L));
            orphan.putOverflow(0, input.getLongOr(prefix + "_overflow", 0L));
            orphan.setScalarRegenerationRemainder(input.getLongOr(prefix + "_remainder", 0L));
        } else {
            readPartitionMapInto(input, prefix,
                    (partition, value) -> orphan.putCurrent(partition, value),
                    (partition, value) -> orphan.putOverflow(partition, value));
        }
        return orphan;
    }

    /**
     * Restores an orphan back into live state once its definition reappears with a compatible
     * model (canonical §5.4). Current, overflow, and — for {@code SCALAR} — the regeneration
     * remainder are all preserved exactly; iterates {@link OrphanedResourceState#partitions()}
     * (the current/overflow key union) so an overflow-only partition is not silently dropped.
     */
    private static ResourceState orphanToLiveState(OrphanedResourceState orphan) {
        if (orphan.model() == ResourceModel.SCALAR) {
            return new ScalarResourceState(
                    orphan.getCurrent(0), orphan.getOverflow(0), orphan.scalarRegenerationRemainder());
        }
        PartitionedResourceState state = new PartitionedResourceState();
        for (int partition : orphan.partitions()) {
            state.setCurrent(partition, orphan.getCurrent(partition));
            state.setOverflow(partition, orphan.getOverflow(partition));
        }
        return state;
    }

    private static void readPartitionMapInto(
            ValueInput input,
            String prefix,
            BiConsumer<Integer, Long> setCurrent,
            BiConsumer<Integer, Long> setOverflow
    ) {
        int partitionCount = input.getIntOr(prefix + "_partitionCount", 0);
        for (int j = 0; j < partitionCount; j++) {
            int key = input.getIntOr(prefix + "_partition_" + j + "_key", j);
            setCurrent.accept(key, input.getLongOr(prefix + "_partition_" + j + "_current", 0L));
            setOverflow.accept(key, input.getLongOr(prefix + "_partition_" + j + "_overflow", 0L));
        }
    }

    // ── Respawn copy ─────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerResourceStateComponent other, HolderLookup.Provider registries) {
        // Three production GENERIC_COMPONENT definitions exist as of the dormant Resource
        // Registration pass (totality:thirst/sanity/ki), but none has a grant provider yet, so
        // `other.states` normally has no live entry for any of them to copy in the first place —
        // registering a definition does not fabricate a live entry here. All three also currently
        // use ResourceLifecyclePolicy.DEFAULT (ResourceDeathPolicy.KEEP_CURRENT), so even in the
        // hypothetical case where one had been instantiated, this blanket full copy already matches
        // what their own declared deathPolicy would ask for — there is still nothing for a
        // per-resource ResourceLifecyclePolicy.deathPolicy() to meaningfully differentiate today.
        // Once a resource declares a non-default deathPolicy, later migration work should consult
        // each resource's own lifecycle policy here instead of this blanket copy — see the
        // readiness audit's migration matrix.
        orphanedStates.clear();
        // Deep-copy genuine orphans first: OrphanedResourceState.copy() (not putAll, which would
        // share the same mutable instances between this component and `other`).
        for (Map.Entry<Identifier, OrphanedResourceState> entry : other.orphanedStates.entrySet()) {
            orphanedStates.put(entry.getKey(), entry.getValue().copy());
        }

        states.clear();
        for (Map.Entry<Identifier, ResourceState> entry : other.states.entrySet()) {
            Identifier id = entry.getKey();
            if (isRegisteredExternalAdapterAuthority(id)) {
                // `other.states` should never actually contain an EXTERNAL_ADAPTER-authority entry
                // (same invariant as writeSyncPacket/writeData), but copyFrom does not trust that
                // blindly either. Preservation is appropriate here (this is real, if corrupted,
                // in-memory data) — quarantine it into `orphanedStates` instead of copying it as
                // authoritative live state, exactly like the NBT-read quarantine path.
                Totality.LOGGER.warn(
                        "[ResourceState] copyFrom encountered live generic state for "
                                + "EXTERNAL_ADAPTER-authority {} — quarantining rather than copying it as authoritative", id);
                orphanedStates.put(id, stateToOrphan(entry.getValue()));
                continue;
            }
            states.put(id, copyState(entry.getValue()));
        }
    }

    private static ResourceState copyState(ResourceState state) {
        if (state instanceof ScalarResourceState scalar) return scalar.copy();
        if (state instanceof PartitionedResourceState partitioned) return partitioned.copy();
        throw new IllegalStateException("Unknown ResourceState implementation: " + state.getClass());
    }

    /**
     * Inverse of {@link #orphanToLiveState}: converts a live {@link ResourceState} into the
     * {@link OrphanedResourceState} shape, used only by {@link #copyFrom}'s defensive quarantine
     * path above.
     */
    private static OrphanedResourceState stateToOrphan(ResourceState state) {
        OrphanedResourceState orphan = new OrphanedResourceState(state.model());
        if (state instanceof ScalarResourceState scalar) {
            orphan.putCurrent(0, scalar.currentUnits());
            orphan.putOverflow(0, scalar.overflowUnits());
            orphan.setScalarRegenerationRemainder(scalar.regenerationRemainder());
        } else if (state instanceof PartitionedResourceState partitioned) {
            for (int partition : partitioned.partitions()) {
                orphan.putCurrent(partition, partitioned.getCurrent(partition));
                orphan.putOverflow(partition, partitioned.getOverflow(partition));
            }
        } else {
            throw new IllegalStateException("Unknown ResourceState implementation: " + state.getClass());
        }
        return orphan;
    }
}
