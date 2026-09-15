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
import zcylas.totality.api.rpg.resources.integration.ResourceRemovalPolicy;
import zcylas.totality.api.rpg.resources.state.OrphanedResourceState;
import zcylas.totality.api.rpg.resources.state.PartitionedResourceState;
import zcylas.totality.api.rpg.resources.state.ResourceState;
import zcylas.totality.api.rpg.resources.state.ScalarResourceState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntToLongFunction;

/**
 * Generic, registry-driven player resource state. Registered under {@code totality:resource_state}
 * (see {@link ResourceStateComponents}) — a distinct identifier from the legacy
 * {@code totality:resources} component ({@link PlayerResourceComponent}).
 *
 * <p>{@link PlayerResourceRegistry#INSTANCE} registers two distinct authority categories of
 * production definition. The five remaining {@code EXTERNAL_ADAPTER}-authority resources (Health,
 * Food, Breath, Spell Slots, Rage) can never gain an entry here at all: {@link #instantiateScalar}/
 * {@link #instantiatePartitioned} actively reject any id whose registered definition is
 * {@code EXTERNAL_ADAPTER}-authority, and stale/malformed persisted data for such an id is
 * quarantined as an orphan rather than restored live (see {@code readLiveEntry}/{@code
 * readOrphanEntry}) — those five can never gain a duplicate, out-of-sync copy of their state here,
 * and keep using their existing storage (or, for Breath, no storage at all yet) until their own
 * later migration. {@code GENERIC_COMPONENT}-authority resources are, by contrast, legitimately
 * instantiable here. As of the Phase 4 migration (2026-09-15), {@code totality:mana}/{@code
 * totality:stamina} are the first two production resources whose state genuinely lives in this
 * component for every ordinary player — populated by {@link
 * zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources}'s {@code
 * totality:player_baseline} grant on join/respawn/dimension-transfer (see that class and {@code
 * BaselineResourceLifecycleEvents}), not merely registered-but-dormant. The three
 * {@code GENERIC_COMPONENT}-authority resources added by the dormant Resource Registration pass
 * (Thirst, Sanity, Ki) remain dormant for a different reason: no grant provider exists for any of
 * them yet, not rejected, simply never granted. Nothing in production code calls {@link #sync()}
 * yet — the Phase 3A/4 sync path uses {@code ResourceSyncManager.markDirty}/{@code flush} instead,
 * a separate mechanism from this component's own (still-unused) sync method — so this component
 * still never sends a network packet through {@link #sync()} itself.
 *
 * <p>Queries never auto-instantiate state (canonical §4.4: "A read-only query must not silently
 * grant or instantiate a resource"). State is only ever created via the explicit
 * {@code instantiateScalar}/{@code instantiatePartitioned} calls — for Mana/Stamina, called by
 * {@code ResourceGrantReconciler} (via the baseline grant above) and, once per player, by {@code
 * BaselineResourceLifecycleEvents}'s one-time legacy NBT import (see {@link #isLegacyMigrated}).
 */
public final class PlayerResourceStateComponent implements SyncedComponent, CopyableComponent<PlayerResourceStateComponent> {

    /**
     * Schema version 4 (bumped from 3 during the Phase 4 Mana/Stamina final external-review
     * correction pass, 2026-09-15): a new persisted {@code legacyMigratedResourceIds} set records,
     * per resource, that its one-time legacy NBT import (see {@link #isLegacyMigrated}) has already
     * run — durable proof distinct from mere state presence, so a resource that later becomes
     * absent from live state for any reason (quarantine, corruption) is never mistaken for "never
     * migrated" and re-imported from a now-stale legacy value. A save written under schema 3 or
     * earlier has no such entries; {@link #readData} defaults to an empty set, which is safe (see
     * {@code BaselineResourceLifecycleEvents}'s self-healing guard — a schema-3 save that already
     * has live Mana/Stamina state simply gets the marker set on its next join, without re-importing,
     * since the import step itself is separately guarded by state presence too).
     */
    private static final int SCHEMA_VERSION = 4;

    private final Map<Identifier, ResourceState> states = new LinkedHashMap<>();
    private final Map<Identifier, OrphanedResourceState> orphanedStates = new LinkedHashMap<>();
    /**
     * The currently-winning {@link zcylas.totality.api.rpg.resources.integration.ResourceGrant}'s
     * declared removal policy for each instantiated resource — canonical §16.2: "every grant
     * requires... what happens when the source disappears." {@link
     * zcylas.totality.api.rpg.resources.integration.ResourceGrantReconciler} is deliberately
     * stateless between calls and recomputes live grants fresh every time, so by the time a
     * resource's last grant has actually disappeared, the grant object itself (and thus its
     * removal policy) is no longer visible anywhere — this one small per-resource field is what
     * lets removal still apply the correct policy instead of falling back to a resource-wide
     * default. Deliberately in-memory only (not persisted): if a grant disappears entirely between
     * server sessions before any reconciliation runs, the per-resource {@code
     * ResourceGrantPolicyRegistry} default is used instead of the exact grant's own policy — a
     * documented, minor limitation (see the pre-Phase-4 foundation correction report), since real
     * grant loss overwhelmingly happens during an active session that already holds this record.
     */
    private final Map<Identifier, ResourceRemovalPolicy> grantRemovalPolicies = new LinkedHashMap<>();
    /**
     * Canonical §24.4: "mark migration version" — durable, per-resource proof that a legacy-store
     * import has already run, independent of whether the resource's live state currently happens to
     * be present. See {@link #isLegacyMigrated}/{@link #markLegacyMigrated}.
     */
    private final Set<Identifier> legacyMigratedResourceIds = new LinkedHashSet<>();
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
        grantRemovalPolicies.remove(id);
    }

    // ── Active/dormant flag and grant-removal-policy bookkeeping (correction pass 2026-09-15) ──

    /**
     * Whether {@code id}'s live state is currently active (granted) rather than dormant (retained
     * after its last grant source disappeared under {@code PRESERVE_DORMANT}/{@code
     * RESET_AND_PRESERVE} — canonical §16.7). Returns {@code true} for a resource with no state at
     * all; callers that care about that distinction already check {@link #hasState} first.
     */
    public boolean isActive(Identifier id) {
        ResourceState state = states.get(id);
        if (state instanceof ScalarResourceState scalar) return scalar.active();
        if (state instanceof PartitionedResourceState partitioned) return partitioned.active();
        return true;
    }

    public void setActive(Identifier id, boolean active) {
        ResourceState state = states.get(id);
        if (state instanceof ScalarResourceState scalar) scalar.setActive(active);
        else if (state instanceof PartitionedResourceState partitioned) partitioned.setActive(active);
    }

    public void setGrantRemovalPolicy(Identifier id, ResourceRemovalPolicy policy) {
        grantRemovalPolicies.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(policy, "policy"));
    }

    public Optional<ResourceRemovalPolicy> getGrantRemovalPolicy(Identifier id) {
        return Optional.ofNullable(grantRemovalPolicies.get(id));
    }

    public void clearGrantRemovalPolicy(Identifier id) {
        grantRemovalPolicies.remove(id);
    }

    /**
     * Canonical §24.4's "mark migration version" — {@code true} once {@code id}'s one-time legacy
     * NBT import has completed, regardless of whether its live state is currently present (see the
     * field's own Javadoc for why this must be independent of {@link #hasState}).
     */
    public boolean isLegacyMigrated(Identifier id) {
        return legacyMigratedResourceIds.contains(id);
    }

    public void markLegacyMigrated(Identifier id) {
        legacyMigratedResourceIds.add(Objects.requireNonNull(id, "id"));
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
        output.putInt("LegacyMigratedCount", legacyMigratedResourceIds.size());
        i = 0;
        for (Identifier id : legacyMigratedResourceIds) {
            output.putString("LegacyMigrated_" + i, id.toString());
            i++;
        }
    }

    private static void writeLiveEntry(ValueOutput output, String prefix, Identifier id, ResourceState state) {
        output.putString(prefix + "_id", id.toString());
        output.putString(prefix + "_model", state.model().name());
        boolean active = state instanceof ScalarResourceState scalarActive ? scalarActive.active()
                : state instanceof PartitionedResourceState partitionedActive ? partitionedActive.active() : true;
        output.putBoolean(prefix + "_active", active);
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
        // In-memory-only bookkeeping (see the field's own Javadoc) — a fresh load starts with no
        // known winning-grant removal policy for anything; the next reconciliation repopulates it
        // for every resource still actually granted.
        grantRemovalPolicies.clear();
        int resourceCount = input.getIntOr("ResourceCount", 0);
        for (int i = 0; i < resourceCount; i++) {
            readLiveEntry(input, "Resource_" + i);
        }
        int orphanedCount = input.getIntOr("OrphanedCount", 0);
        for (int i = 0; i < orphanedCount; i++) {
            readOrphanEntry(input, "Orphaned_" + i);
        }
        legacyMigratedResourceIds.clear();
        // Defaults to 0 for any save written before schema 4 — safe (see the field's own Javadoc):
        // a resource that was already live before this schema existed simply gets marked on its
        // next join rather than treated as "never migrated."
        int legacyMigratedCount = input.getIntOr("LegacyMigratedCount", 0);
        for (int i = 0; i < legacyMigratedCount; i++) {
            String raw = input.getStringOr("LegacyMigrated_" + i, "");
            if (!raw.isEmpty()) {
                legacyMigratedResourceIds.add(Identifier.parse(raw));
            }
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
        // Default true: a save written before schema 3 has no `_active` key and, by construction,
        // never had a dormancy concept — its state was always live.
        boolean active = input.getBooleanOr(prefix + "_active", true);
        if (model == ResourceModel.SCALAR) {
            long current = input.getLongOr(prefix + "_current", 0L);
            long overflow = input.getLongOr(prefix + "_overflow", 0L);
            long remainder = input.getLongOr(prefix + "_remainder", 0L);
            ScalarResourceState state = new ScalarResourceState(current, overflow, remainder);
            state.setActive(active);
            return state;
        }
        PartitionedResourceState state = new PartitionedResourceState();
        readPartitionMapInto(input, prefix, state::setCurrent, state::setOverflow);
        state.setActive(active);
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
        // Phase 4 (Mana/Stamina migration): this now actually consults each resource's declared
        // ResourceDeathPolicy instead of always blanket-copying — Mana/Stamina need it (legacy
        // behavior is a full refill to maximum on respawn, not preservation of current value).
        // Thirst/Sanity/Ki remain at ResourceLifecyclePolicy.DEFAULT (KEEP_CURRENT), so this change
        // is behaviorally invisible for them — they still fall through to the same blanket copy as
        // before.
        orphanedStates.clear();
        // Deep-copy genuine orphans first: OrphanedResourceState.copy() (not putAll, which would
        // share the same mutable instances between this component and `other`).
        for (Map.Entry<Identifier, OrphanedResourceState> entry : other.orphanedStates.entrySet()) {
            orphanedStates.put(entry.getKey(), entry.getValue().copy());
        }

        grantRemovalPolicies.clear();
        grantRemovalPolicies.putAll(other.grantRemovalPolicies);

        // Migration-completion is a permanent, once-ever fact about the player, unrelated to
        // whatever a resource's own ResourceDeathPolicy does to its value below — always carried
        // across respawn unconditionally, exactly like grantRemovalPolicies above.
        legacyMigratedResourceIds.clear();
        legacyMigratedResourceIds.addAll(other.legacyMigratedResourceIds);

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
            ResourceDeathPolicy deathPolicy = PlayerResourceRegistry.INSTANCE.get(id)
                    .map(definition -> definition.lifecycle().deathPolicy())
                    .orElse(ResourceDeathPolicy.KEEP_CURRENT);
            switch (deathPolicy) {
                case KEEP_CURRENT -> states.put(id, copyState(entry.getValue()));
                case RESET_TO_MAXIMUM, RESET_TO_MINIMUM -> {
                    // Deliberately dropped, not copied: the next grant reconciliation (which the
                    // respawn lifecycle hook triggers immediately after component copy) sees this
                    // resource as newly "missing" and reinitializes it via the winning grant's own
                    // ResourceGrantInitialization (canonical §16.6) — reusing the already-tested
                    // instantiation path instead of resolving a live ResourceMaximumResolver from
                    // inside this low-level component-copy method, where the new ServerPlayer's other
                    // components are not guaranteed fully attached yet. This produces the death
                    // policy's literal named value only when the resource's grant initialization is
                    // configured consistently with it (e.g. RESET_TO_MAXIMUM paired with AtMaximum,
                    // as Mana/Stamina's Phase 4 migration does) — a documented simplification, not a
                    // fully general death/initialization decoupling.
                }
                case SET_TO_AUTHORED_VALUE, CLEAR_OVERFLOW, CUSTOM -> {
                    Totality.LOGGER.warn("[ResourceState] death policy {} is not implemented this pass for "
                            + "{} — falling back to KEEP_CURRENT", deathPolicy, id);
                    states.put(id, copyState(entry.getValue()));
                }
            }
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
