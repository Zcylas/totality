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
 */
public final class BlockDamageStorage {

    /**
     * @param block         identity of the block that was damaged; damage never transfers to a different block
     * @param max           Max Durability captured at first hit
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
        public final Identifier block;
        public final float max;
        public float integrity;
        public long lastImpactTick;
        /** Synthetic, negative crack "breaker" id: can never collide with an entity id. Not persisted. */
        int crackId;
        /** Last crack stage sent. Not persisted. */
        int sentStage = -2;

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

    /** The live entry for {@code pos}, or null. Discards (and clears the crack of) an entry whose block no longer matches. */
    @Nullable
    public Entry get(BlockPos pos, BlockState state) {
        Entry e = map().get(pos.asLong());
        if (e == null) return null;
        if (!e.block.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            remove(e);
            return null;
        }
        return e;
    }

    public Entry create(BlockPos pos, BlockState state, float max, long now) {
        Entry e = new Entry(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), max, max, now);
        e.crackId = nextCrackId++;
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
        if (e.crackId == 0) e.crackId = nextCrackId++;   // entries loaded from disk
    }

    /** Sends the vanilla crack for {@code integrity}. Resent on every sweep because the client expires unrefreshed cracks. */
    private void syncCrack(Entry e, float integrity) {
        ensureCrackId(e);
        e.sentStage = BlockDurability.crackStage(integrity, e.max);
        level.destroyBlockProgress(e.crackId, e.pos, e.sentStage);
    }

    private void clearCrack(Entry e) {
        ensureCrackId(e);
        level.destroyBlockProgress(e.crackId, e.pos, -1);
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
        float current = e.currentIntegrity(now);
        if (state.isAir() || !e.block.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock())) || current >= e.max) {
            map().remove(e.pos.asLong());
            clearCrack(e);
            saved.set(saved.get());
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
            out.add(new ClientboundBlockDestructionPacket(e.crackId, e.pos, BlockDurability.crackStage(current, e.max)));
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
