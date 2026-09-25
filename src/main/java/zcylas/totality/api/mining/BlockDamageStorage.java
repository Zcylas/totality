package zcylas.totality.api.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.util.data.CodecSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension, position-owned Block Damage. The authority is a {@link net.minecraft.world.level.saveddata.SavedData}
 * on the level's data storage (not a static map), so damage is shared by every source, survives
 * chunk unload and server restart, and is never keyed by player.
 *
 * <p>Recovery is lazy: {@link Entry#currentIntegrity} derives it from timestamps whenever the
 * entry is read, so nothing ticks a damaged block just to heal it.
 *
 * <p>Reconciliation is lazy too (Block Breaking V2, Pass 1): every read or sweep of a record applies
 * {@link IntegrityReconciliation} against the block now at its position and that block's current
 * {@link BlockProfiles profile} — deleting it for an unrelated replacement or a non-ORDINARY classification, and
 * rescaling it by percentage for a changed maximum or an authored transformation. The record of a supported
 * multi-position assembly (door, bed, extended piston) lives on the owner position
 * ({@link BlockProfiles#integrityOwner}); a record an older save left on the other position is folded into it.
 */
public final class BlockDamageStorage {

    /**
     * @param block         identity of the block that was damaged; damage never transfers to an unrelated block
     * @param max           Max Durability captured at first hit (rescaled by percentage if the profile changes)
     * @param integrity     integrity as of {@code lastImpactTick} (before lazy recovery)
     * @param lastImpactTick level game time of the last impact
     */
    public static final class Entry {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(e -> e.pos),
                Identifier.CODEC.fieldOf("block").forGetter(e -> e.block),
                Codec.FLOAT.fieldOf("max").forGetter(e -> e.max),
                Codec.FLOAT.fieldOf("integrity").forGetter(e -> e.integrity),
                Codec.LONG.fieldOf("last_impact").forGetter(e -> e.lastImpactTick)
        ).apply(i, Entry::new));

        public final BlockPos pos;
        public Identifier block;
        public float max;
        public float integrity;
        public long lastImpactTick;
        /** Synthetic, negative crack "breaker" id: can never collide with an entity id. Not persisted. The shared
         *  assembly partner position (if any) uses {@code crackId + 1}; ids are handed out in steps of 2. */
        int crackId;
        /** Last crack stage sent. Not persisted. */
        int sentStage = -2;
        /** Position the partner crack ({@code crackId + 1}) was last sent to, or null. Not persisted. */
        @Nullable BlockPos sentPartner;

        Entry(BlockPos pos, Identifier block, float max, float integrity, long lastImpactTick) {
            this.pos = pos.immutable();
            this.block = block;
            this.max = max;
            this.integrity = integrity;
            this.lastImpactTick = lastImpactTick;
        }

        public float currentIntegrity(long now) {
            return recovered(integrity, max, lastImpactTick, now);
        }
    }

    /** Pure recovery formula (unit-checkable): nothing until the delay elapses, then linear. */
    public static float recovered(float integrity, float max, long lastImpactTick, long now) {
        long idle = now - lastImpactTick - MiningTuning.RECOVERY_DELAY_TICKS;
        if (idle <= 0) return integrity;
        return Math.min(max, integrity + idle * max * MiningTuning.RECOVERY_FRACTION_PER_TICK);
    }

    /** Serialized payload. */
    public static final class Data {
        static final Codec<Data> CODEC = Entry.CODEC.listOf().xmap(Data::new, Data::toList);
        final Map<Long, Entry> entries = new HashMap<>();

        Data() {}
        Data(List<Entry> list) { for (Entry e : list) entries.put(e.pos.asLong(), e); }
        List<Entry> toList() { return List.copyOf(entries.values()); }
    }

    private static final CodecSavedData.Factory<Data> STORAGE =
            CodecSavedData.create(Data.CODEC, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "block_damage"))
                    .defaultValue(Data::new);

    private static int nextCrackId = Integer.MIN_VALUE;

    private final ServerLevel level;
    private final CodecSavedData<Data> saved;

    private BlockDamageStorage(ServerLevel level, CodecSavedData<Data> saved) {
        this.level = level;
        this.saved = saved;
    }

    /** Test seam: the same logic over an explicitly loaded payload (e.g. one read back from disk). */
    static BlockDamageStorage over(ServerLevel level, CodecSavedData<Data> saved) {
        return new BlockDamageStorage(level, saved);
    }

    static CodecSavedData<Data> open(SavedDataStorage storage) { return STORAGE.create(storage); }

    public static BlockDamageStorage get(ServerLevel level) { return new BlockDamageStorage(level, STORAGE.create(level)); }

    private Map<Long, Entry> map() { return saved.get().entries; }

    /** Verification seam: the live entry map, so a self-test can snapshot and exactly restore pre-existing state. */
    Map<Long, Entry> entriesForVerification() { return map(); }

    /** Verification seam: persist the map after a self-test restored it (marks the SavedData dirty, no crack packets). */
    void markDirtyForVerification() { saved.set(saved.get()); }

    /**
     * The live, reconciled entry for {@code pos} (an Integrity owner position), or null. Folds in a legacy record
     * left on the assembly partner, then applies {@link #reconcile}: an entry for an unrelated block, or for a
     * block that is no longer ORDINARY, is discarded (and its crack cleared).
     */
    @Nullable
    public Entry get(BlockPos pos, BlockState state) {
        BlockPos partner = BlockProfiles.integrityPartner(level, pos, state);
        if (partner != null) {
            Entry legacy = map().get(partner.asLong());
            if (legacy != null) {
                if (legacy.block.equals(key(level.getBlockState(partner)))) foldIntoOwner(legacy, pos, state, level.getGameTime());
                else remove(legacy);
            }
        }
        Entry e = map().get(pos.asLong());
        return e == null ? null : reconcile(e, state);
    }

    /**
     * Applies lazy {@link IntegrityReconciliation#reconcile} to {@code e} against {@code state} at its position; null
     * when deleted. A different block id is always deleted here, whatever transformations are authored: only
     * {@link #transformBlock} can carry a record across block ids.
     */
    @Nullable
    private Entry reconcile(Entry e, BlockState state) {
        BlockProfile.Resolved profile = BlockProfiles.resolve(level, e.pos, state);
        return apply(e, key(state), IntegrityReconciliation.reconcile(e.integrity, e.max, e.block.equals(key(state)),
                profile.classification(), profile.maxDurability()));
    }

    @Nullable
    private Entry apply(Entry e, Identifier block, IntegrityReconciliation.Decision decision) {
        switch (decision.action()) {
            case KEEP -> { return e; }
            case RESCALE -> {
                e.block = block;
                e.max = decision.max();
                e.integrity = decision.integrity();
                saved.set(saved.get());
                return e;
            }
            default -> {
                remove(e);
                return null;
            }
        }
    }

    /** One suppressed position of a running transaction (server thread only). */
    private record Suppressed(ServerLevel level, long pos) {}

    /** Positions whose removal notification is suppressed while a {@link #transaction} runs; each is reconciled after it. */
    private static final java.util.ArrayDeque<Suppressed> SUPPRESSED = new java.util.ArrayDeque<>();

    /**
     * Removal notification from the chunk block-change hook ({@code LevelChunkBlockRemovalMixin}): the block at
     * {@code pos} is being replaced by a different block right now, so any record held there belongs to a block that
     * no longer exists and is deleted immediately (with its crack). Same-block state changes never get here. Positions
     * inside a running {@link #transaction} are exempt: that transaction reconciles each of them itself afterwards.
     * Cheap on the common path: no records in this level, or none at this position, returns at once.
     */
    public static void onBlockRemoved(ServerLevel level, BlockPos pos) {
        if (!SUPPRESSED.isEmpty() && SUPPRESSED.contains(new Suppressed(level, pos.asLong()))) return;
        BlockDamageStorage storage = get(level);
        if (storage.isEmpty()) return;
        Entry e = storage.map().get(pos.asLong());
        if (e != null) storage.remove(e);
    }

    /**
     * Performs a physical transformation of the block at {@code pos} into {@code newState} through {@link #transaction}
     * (the explicit, event-safe path; verification and future direct callers use it).
     *
     * @return whether the block change was applied ({@code Level.setBlock}); a refused change leaves the record as
     *         it was before the call
     */
    public boolean transformBlock(BlockPos pos, BlockState newState, int flags) {
        return transaction(level, pos, () -> level.setBlock(pos, newState, flags));
    }

    /** Verification seam: the positions a transaction at {@code pos} would snapshot. */
    static java.util.Set<BlockPos> tiedPositionsForVerification(ServerLevel level, BlockPos pos) { return tiedPositions(level, pos); }

    /** The positions a change at {@code pos} can structurally carry along: its Integrity owner, the owner's assembly
     *  partner, and a connected Copper Chest half (independent record, but converted together by vanilla). Loaded only. */
    private static java.util.Set<BlockPos> tiedPositions(ServerLevel level, BlockPos pos) {
        java.util.Set<BlockPos> tied = new java.util.LinkedHashSet<>();
        tied.add(pos.immutable());
        // Pass 4B hardening: no broad surrounding-chunk check (an unrelated unloaded chunk must not hide a loaded owner
        // or partner). The owner/partner lookups check each position they read individually and never load a chunk.
        BlockState state = level.getBlockState(pos);
        BlockPos owner = BlockProfiles.integrityOwner(level, pos, state);
        tied.add(owner);
        BlockPos partner = BlockProfiles.integrityPartner(level, owner, owner.equals(pos) ? state : level.getBlockState(owner));
        if (partner != null) tied.add(partner);
        if (state.getBlock() instanceof net.minecraft.world.level.block.CopperChestBlock
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos connected = net.minecraft.world.level.block.ChestBlock.getConnectedBlockPos(pos, state);
            if (level.isLoaded(connected)) tied.add(connected);
        }
        return tied;
    }

    /**
     * The event-safe physical-transformation transaction (Block Breaking V2, Pass 4A). Wraps exactly one vanilla
     * in-place mutation (an Axe/Honeycomb/Hoe/Shovel use, a weathering tick, a Farmland/Path reversion...) and runs it
     * UNCHANGED, with its own flags and side effects. Around it:
     * <ol>
     *   <li>before: the records on the positions the change can carry along ({@link #tiedPositions}) are snapshotted
     *       together with the block standing on each;</li>
     *   <li>during: the removal hook is suppressed for exactly those positions (nested changes anywhere else are
     *       untouched);</li>
     *   <li>after: each snapshotted position is reconciled. Same block -> nothing (ordinary state change; a changed
     *       maximum is rescaled lazily as usual). Different block -> {@link IntegrityReconciliation#transform}: the
     *       record migrates by percentage only if it belonged to the block that stood there AND {@code old -> new} is a
     *       registered {@link BlockProfiles#transformation}; any other result deletes it, like a removal.</li>
     * </ol>
     * A mutation that changes nothing (rejected, failed, predicate false) leaves every record exactly as it was. With
     * no records on the tied positions the mutation simply runs. This is not id inference: only the change produced
     * inside this bracketed, authorized call is ever evaluated.
     */
    public static <T> T transaction(ServerLevel level, BlockPos pos, java.util.function.Supplier<T> mutation) {
        BlockDamageStorage storage = get(level);
        if (storage.isEmpty() || !level.isLoaded(pos)) return mutation.get();
        record Before(BlockPos pos, Entry entry, BlockState state) {}
        List<Before> before = new ArrayList<>();
        for (BlockPos p : tiedPositions(level, pos)) {
            Entry e = storage.map().get(p.asLong());
            if (e != null) before.add(new Before(p, e, level.getBlockState(p)));
        }
        if (before.isEmpty()) return mutation.get();
        for (Before b : before) SUPPRESSED.push(new Suppressed(level, b.pos().asLong()));
        T result;
        try {
            result = mutation.get();
        } finally {
            for (int i = 0; i < before.size(); i++) SUPPRESSED.pop();
        }
        for (Before b : before) {
            BlockState after = level.getBlockState(b.pos());
            if (after.is(b.state().getBlock())) continue;              // unchanged block: nothing to migrate
            Entry e = b.entry();
            if (storage.map().get(b.pos().asLong()) != e) continue;   // already handled inside the mutation
            if (e.block.equals(key(after))) continue;                   // already migrated by a nested transaction
                                                                         // (e.g. a Cauldron interaction emptying via lowerFillLevel)
            BlockProfile.Resolved profile = BlockProfiles.resolve(level, b.pos(), after);
            var decision = IntegrityReconciliation.transform(e.integrity, e.max, e.block.equals(key(b.state())),
                    BlockProfiles.transformsTo(b.state().getBlock(), after.getBlock()),
                    profile.classification(), profile.maxDurability());
            Entry migrated = storage.apply(e, key(after), decision);
            if (migrated != null) storage.syncCrack(migrated, migrated.currentIntegrity(level.getGameTime()));
        }
        return result;
    }

    /**
     * Moves a record found on a non-owner assembly position (saved before ownership was shared) onto the owner.
     * The more damaged record wins ({@link IntegrityReconciliation#memberWinsFold}); its raw integrity and
     * last-impact tick are kept together so lazy recovery is not counted twice. A different maximum is rescaled by
     * the owner's next reconcile.
     */
    private void foldIntoOwner(Entry member, BlockPos owner, BlockState ownerState, long now) {
        remove(member);
        Entry existing = map().get(owner.asLong());
        float ownerFraction = existing != null && existing.max > 0f ? existing.currentIntegrity(now) / existing.max : Float.NaN;
        float memberFraction = member.max > 0f ? member.currentIntegrity(now) / member.max : Float.NaN;
        if (!IntegrityReconciliation.memberWinsFold(ownerFraction, memberFraction)) return;
        Entry folded = existing != null ? existing : new Entry(owner, key(ownerState.getBlock()), member.max, member.integrity, member.lastImpactTick);
        folded.block = key(ownerState.getBlock());
        folded.max = member.max;
        folded.integrity = member.integrity;
        folded.lastImpactTick = member.lastImpactTick;
        ensureCrackId(folded);
        map().put(owner.asLong(), folded);
        saved.set(saved.get());
    }

    private static Identifier key(BlockState state) { return key(state.getBlock()); }

    private static Identifier key(net.minecraft.world.level.block.Block block) { return BuiltInRegistries.BLOCK.getKey(block); }

    /**
     * Old-save reconciliation that needs no position (run once per level at server start; it touches only this
     * damage map, never the world): drops records whose block no longer exists, whose saved values are corrupt, or
     * whose block is no longer ORDINARY in any state, so no dormant finite damage survives a classification change
     * made while the chunk was unloaded. Everything position-dependent stays lazy.
     *
     * @return number of records removed
     */
    public int reconcileRecords() {
        int removed = 0;
        for (Entry e : new ArrayList<>(map().values())) {
            var block = BuiltInRegistries.BLOCK.getOptional(e.block);
            boolean keep = block.isPresent() && Float.isFinite(e.max) && e.max > 0f && Float.isFinite(e.integrity)
                    && block.get().getStateDefinition().getPossibleStates().stream()
                            .anyMatch(st -> BlockProfiles.resolveStatic(st).ordinary());
            if (!keep) {
                remove(e);
                removed++;
            }
        }
        return removed;
    }

    public Entry create(BlockPos pos, BlockState state, float max, long now) {
        Entry e = new Entry(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), max, max, now);
        ensureCrackId(e);
        map().put(pos.asLong(), e);
        saved.set(saved.get());
        return e;
    }

    /** Commit an integrity change and refresh the vanilla crack. */
    public void update(Entry e, float integrity, long now) {
        e.integrity = integrity;
        e.lastImpactTick = now;
        saved.set(saved.get());
        syncCrack(e, integrity);
    }

    public void remove(Entry e) {
        map().remove(e.pos.asLong());
        saved.set(saved.get());
        clearCrack(e);
    }

    private void ensureCrackId(Entry e) {
        if (e.crackId == 0) {                            // new, or loaded from disk
            e.crackId = nextCrackId;
            nextCrackId += 2;                            // crackId + 1 is the assembly partner's
        }
    }

    /** The assembly partner currently sharing {@code e}'s record, or null (needs the owner's chunk loaded). */
    @Nullable
    private BlockPos partner(Entry e) {
        return level.isLoaded(e.pos) ? BlockProfiles.integrityPartner(level, e.pos, level.getBlockState(e.pos)) : null;
    }

    /** Sends the vanilla crack for {@code integrity}. Resent on every sweep because the client expires unrefreshed cracks. */
    private void syncCrack(Entry e, float integrity) {
        ensureCrackId(e);
        e.sentStage = BlockDurability.crackStage(integrity, e.max);
        level.destroyBlockProgress(e.crackId, e.pos, e.sentStage);
        BlockPos partner = partner(e);
        if (partner != null) {
            level.destroyBlockProgress(e.crackId + 1, partner, e.sentStage);
        } else if (e.sentPartner != null) {
            level.destroyBlockProgress(e.crackId + 1, e.sentPartner, -1);   // the assembly split (piston retracted)
        }
        e.sentPartner = partner;
    }

    private void clearCrack(Entry e) {
        ensureCrackId(e);
        level.destroyBlockProgress(e.crackId, e.pos, -1);
        level.destroyBlockProgress(e.crackId + 1, e.pos, -1);   // the client clears by id, whatever the position
        e.sentPartner = null;
    }

    /**
     * Current integrity of a LOADED entry after validating it, or -1 when the entry is stale and was pruned
     * (block replaced, air, or fully recovered; the crack is cleared and the SavedData marked dirty).
     * Returns NaN (entry untouched) when its chunk is not loaded. Shared by {@link #sweep} and the immediate
     * join/respawn sync so both apply exactly the same rules.
     */
    private float validate(Entry e, long now) {
        if (!level.isLoaded(e.pos)) return Float.NaN;
        BlockState state = level.getBlockState(e.pos);
        BlockPos owner = BlockProfiles.integrityOwner(level, e.pos, state);
        if (!owner.equals(e.pos) && e.block.equals(key(state))) {
            foldIntoOwner(e, owner, level.getBlockState(owner), now);   // legacy record on a non-owner position
            return -1f;
        }
        if (reconcile(e, state) == null) return -1f;
        float current = e.currentIntegrity(now);
        if (current >= e.max) {
            remove(e);
            return -1f;
        }
        return current;
    }

    /**
     * Low-frequency maintenance for loaded damaged blocks: prunes stale entries and re-broadcasts current
     * crack stages so they never expire client-side. Unloaded chunks are skipped entirely; their recovery is
     * computed lazily the next time they are touched or swept.
     */
    public void sweep() {
        long now = level.getGameTime();
        for (Entry e : new ArrayList<>(map().values())) {          // copy: validate() may remove entries
            float current = validate(e, now);
            if (current >= 0f) syncCrack(e, current);
        }
    }

    /**
     * Crack packets for every VALID damaged block within 32 blocks of {@code at}. Stale entries found on
     * the way (replaced, air, fully recovered) are pruned instead of being sent.
     */
    public List<ClientboundBlockDestructionPacket> cracksNear(net.minecraft.world.phys.Vec3 at) {
        long now = level.getGameTime();
        List<ClientboundBlockDestructionPacket> out = new ArrayList<>();
        for (Entry e : new ArrayList<>(map().values())) {
            if (e.pos.distToCenterSqr(at) >= 1024.0) continue;
            float current = validate(e, now);
            if (!(current >= 0f)) continue;                        // pruned (-1) or not loaded (NaN)
            ensureCrackId(e);
            int stage = BlockDurability.crackStage(current, e.max);
            out.add(new ClientboundBlockDestructionPacket(e.crackId, e.pos, stage));
            BlockPos partner = partner(e);
            if (partner != null) out.add(new ClientboundBlockDestructionPacket(e.crackId + 1, partner, stage));
        }
        return out;
    }

    /** Send persisted cracks to one player right away (join, respawn). */
    public static void syncTo(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockDamageStorage storage = get(level);
        if (storage.isEmpty()) return;
        for (var packet : storage.cracksNear(player.position())) player.connection.send(packet);
    }

    public boolean isEmpty() { return map().isEmpty(); }

    public int size() { return map().size(); }
}
