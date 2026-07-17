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
 * Phase 0/1 foundation only: no production {@link PlayerResourceDefinition} is registered by this
 * patch, so {@link PlayerResourceRegistry#INSTANCE} is empty and this component holds zero
 * entries for every player. It does not affect Stamina, Mana, Rage, Health, Food, or Breath, which
 * keep using their existing storage until an explicit later migration. Nothing in production code
 * calls {@link #sync()} yet, so this component never sends a network packet during this patch.
 *
 * Queries never auto-instantiate state (canonical §4.4: "A read-only query must not silently
 * grant or instantiate a resource"). State is only ever created via the explicit
 * {@code instantiateScalar}/{@code instantiatePartitioned} calls, which nothing in this patch
 * invokes automatically — a future grant provider is what will call them.
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
        ResourceState existing = states.get(id);
        if (existing != null) {
            if (existing instanceof PartitionedResourceState partitioned) return partitioned;
            throw new IllegalStateException(id + " is already instantiated as " + existing.model());
        }
        PartitionedResourceState created = new PartitionedResourceState();
        states.put(id, created);
        return created;
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
        buf.writeInt(states.size());
        for (Map.Entry<Identifier, ResourceState> entry : states.entrySet()) {
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
            states.put(id, readStatePayload(buf, model));
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
        output.putInt("ResourceCount", states.size());
        int i = 0;
        for (Map.Entry<Identifier, ResourceState> entry : states.entrySet()) {
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
            if (definition.isPresent() && definition.get().model() == model) {
                // Definition returned with a compatible model — restore into live state (canonical §5.4).
                states.put(id, orphanToLiveState(orphan));
            } else {
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
        // No production PlayerResourceDefinition is registered by this patch, so there is
        // currently nothing for a per-resource ResourceLifecyclePolicy.deathPolicy() to
        // differentiate — this defaults to a full copy (equivalent to every resource using
        // ResourceDeathPolicy.KEEP_CURRENT). Once real resources are registered, later migration
        // work should consult each resource's own lifecycle policy here instead of this blanket
        // copy — see the readiness audit's migration matrix.
        states.clear();
        for (Map.Entry<Identifier, ResourceState> entry : other.states.entrySet()) {
            states.put(entry.getKey(), copyState(entry.getValue()));
        }
        orphanedStates.clear();
        // Deep-copy: OrphanedResourceState.copy() (not putAll, which would share the same mutable
        // instances between this component and `other`).
        for (Map.Entry<Identifier, OrphanedResourceState> entry : other.orphanedStates.entrySet()) {
            orphanedStates.put(entry.getKey(), entry.getValue().copy());
        }
    }

    private static ResourceState copyState(ResourceState state) {
        if (state instanceof ScalarResourceState scalar) return scalar.copy();
        if (state instanceof PartitionedResourceState partitioned) return partitioned.copy();
        throw new IllegalStateException("Unknown ResourceState implementation: " + state.getClass());
    }
}
