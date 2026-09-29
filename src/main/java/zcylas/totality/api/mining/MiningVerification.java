package zcylas.totality.api.mining;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import zcylas.totality.api.mining.BlockProfile.Classification;
import zcylas.totality.api.mining.BlockProfile.Layer;
import zcylas.totality.api.mining.BlockProfile.Ownership;
import zcylas.totality.api.mining.BlockProfile.Tool;
import java.util.ArrayList;
import java.util.Map;

/**
 * Dev-environment self-test of the Block Breaking API's server-side logic (same convention as the
 * other {@code *Verification} suites). Covers durability, Integrity arithmetic, shared and
 * identity-checked state, persistence encoding, lazy recovery, tiers and tool stress.
 *
 * <p>NOT covered (needs live input / a real client, so it stays on the manual checklist): the
 * contact-frame swing loop, client intent/meter, crack rendering and the player-actor terminal
 * break with drops/Silk Touch/Fortune/protection cancellation.
 */
public final class MiningVerification {

    private MiningVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return; // opt-in: runs against the live world
        registerHarvestProbe();
        ServerLifecycleEvents.SERVER_STARTED.register(MiningVerification::run);
    }

    private static MiningImpact impact(BlockPos pos, float damage, int tier, MiningSource.Kind kind) {
        return new MiningImpact(pos, Direction.UP, Vec3.atCenterOf(pos), damage, tier, MiningSource.of(kind, null));
    }

    private static void run(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "MiningVerification");
        ServerLevel level = server.overworld();
        BlockPos pos = new BlockPos(0, level.getMaxY() - 5, 0);
        BlockDamageStorage storage = BlockDamageStorage.get(level);
        BlockState original = level.getBlockState(pos);
        // Pre-existing block damage in this world is never the suite's to change: snapshot the live
        // overworld map (exact Entry objects and every mutable field), run the whole suite against an
        // empty map, and restore the snapshot in finally. The suite runs synchronously on the server
        // thread, so no tick (production sweep, player mining) can observe the temporarily empty map.
        java.util.Map<Long, BlockDamageStorage.Entry> live = storage.entriesForVerification();
        List<EntrySnapshot> snapshot = live.entrySet().stream().map(EntrySnapshot::of).toList();
        int leftovers = -1;
        // The suite breaks many blocks, but asserts drops only through harvest eligibility and Block.getDrops, never
        // through spawned items. In its freshly loaded, non-entity-ticking test chunks those item entities are not
        // visible to Level#getAllEntities, so clearItems cannot remove them and they were saved into the world.
        // Block drops are therefore off for the duration of this synchronous suite and the exact prior value restored.
        var gameRules = level.getGameRules();
        boolean blockDropsBefore = gameRules.get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS);
        // Everything above only reads. Every mutation happens inside this try, so the finally below always runs
        // once live state has started to change; its restores are idempotent if setup failed part-way.
        try {
            live.clear();
            gameRules.set(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS, false, server);
            level.getChunkAt(pos);
            pure(r);
            persistence(r, server, level, pos);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            sweepIsolated(r, server, level, pos);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            durability(r, level, pos);
            integrity(r, level, storage, pos);
            sharedAndIdentity(r, level, storage, pos);
            tiers(r, level, storage, pos);
            forceTolerance(r);
            corrected(r, server, level);
            BlockProfileChecks.run(r, level, storage, pos);
        } finally {
            // Nested so each restoration still runs if an earlier cleanup step throws.
            try {
                BlockState now = level.getBlockState(pos);
                BlockDamageStorage.Entry e = storage.get(pos, now);
                if (e != null) storage.remove(e);
                level.setBlockAndUpdate(pos, original);
            } finally {
                try {
                    leftovers = (int) live.entrySet().stream()   // entries that are not the snapshot's own objects
                            .filter(kv -> snapshot.stream().noneMatch(sn -> sn.key() == kv.getKey() && sn.entry() == kv.getValue()))
                            .count();
                    live.clear();
                    for (EntrySnapshot snap : snapshot) snap.restoreInto(live);
                    storage.markDirtyForVerification();
                } finally {
                    gameRules.set(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS, blockDropsBefore, server);
                }
            }
        }
        r.check("block-drops game rule restored to its prior value",
                gameRules.get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS) == blockDropsBefore, "prior=" + blockDropsBefore);
        r.check("the suite's own cleanup left no verifier-created block-damage entries behind",
                leftovers == 0, "leftover entries=" + leftovers);
        r.check("pre-existing block-damage state restored exactly (same entries, same objects, same fields)",
                live.size() == snapshot.size() && snapshot.stream().allMatch(snap -> snap.matches(live)),
                "snapshot=" + snapshot.size() + " live=" + live.size());
        var coverage = BlockCoverageReport.write(server);
        r.check("coverage: every live registry block has exactly one coverage row",
                coverage.values().stream().mapToInt(Integer::intValue).sum() == net.minecraft.core.registries.BuiltInRegistries.BLOCK.size(), "" + coverage);
        r.summarize();
        reportPersistedState(server);
    }

    /** Exact state of one pre-existing live entry: the object itself plus every field a self-test could change. */
    /** Package-private: also used by DurabilityRegressionVerification for the same snapshot/restore. */
    record EntrySnapshot(long key, BlockDamageStorage.Entry entry, float integrity, long lastImpactTick,
                                 int crackId, int sentStage) {
        static EntrySnapshot of(java.util.Map.Entry<Long, BlockDamageStorage.Entry> kv) {
            var e = kv.getValue();
            return new EntrySnapshot(kv.getKey(), e, e.integrity, e.lastImpactTick, e.crackId, e.sentStage);
        }

        void restoreInto(java.util.Map<Long, BlockDamageStorage.Entry> live) {
            entry.integrity = integrity;
            entry.lastImpactTick = lastImpactTick;
            entry.crackId = crackId;
            entry.sentStage = sentStage;
            live.put(key, entry);
        }

        boolean matches(java.util.Map<Long, BlockDamageStorage.Entry> live) {
            var e = live.get(key);
            return e == entry && e.integrity == integrity && e.lastImpactTick == lastImpactTick
                    && e.crackId == crackId && e.sentStage == sentStage;
        }
    }

    /**
     * {@link BlockDamageStorage#sweep()} rules, proven only on verifier-owned entries: the storage here is backed by
     * a throwaway SavedDataStorage in a temp directory (the same seam {@link #persistence} uses), never the live
     * saved data of any dimension. Uses the suite's own test block at {@code pos} (restored by the caller).
     */
    private static void sweepIsolated(VerificationReporter r, MinecraftServer server, ServerLevel level, BlockPos pos) {
        java.nio.file.Path dir = null;
        int liveBefore = BlockDamageStorage.get(level).size();
        try {
            dir = java.nio.file.Files.createTempDirectory("totality_block_damage_sweep_test");
            var sds = new net.minecraft.world.level.storage.SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess());
            var saved = BlockDamageStorage.open(sds);
            BlockDamageStorage iso = BlockDamageStorage.over(level, saved);
            var entries = saved.get().entries;
            try {
                var stone = net.minecraft.resources.Identifier.withDefaultNamespace("stone");
                var dirt = net.minecraft.resources.Identifier.withDefaultNamespace("dirt");
                long now = level.getGameTime();
                long healed = now - MiningTuning.RECOVERY_DELAY_TICKS - 1_000_000L;
                BlockPos far = new BlockPos(29_000_000, 0, 29_000_000);   // a chunk this suite never loads

                entries.put(pos.asLong(), new BlockDamageStorage.Entry(pos, stone, 100f, 40f, now));
                entries.put(far.asLong(), new BlockDamageStorage.Entry(far, stone, 100f, 40f, healed));
                iso.sweep();
                var kept = entries.get(pos.asLong());
                r.check("sweep: keeps a valid damaged entry (matching block, not recovered) with its integrity unchanged",
                        kept != null && kept.integrity == 40f && kept.lastImpactTick == now, "" + kept);
                var skipped = entries.get(far.asLong());
                r.check("sweep: leaves an entry in an unloaded chunk untouched, even one that would count as recovered",
                        !level.isLoaded(far) && skipped != null && skipped.integrity == 40f && skipped.lastImpactTick == healed,
                        "loaded=" + level.isLoaded(far) + " entry=" + skipped);

                entries.clear();
                entries.put(pos.asLong(), new BlockDamageStorage.Entry(pos, dirt, 100f, 40f, now));
                iso.sweep();
                r.check("sweep: prunes an entry whose block was replaced", entries.isEmpty(), "size=" + entries.size());

                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                entries.put(pos.asLong(), new BlockDamageStorage.Entry(pos, stone, 100f, 40f, now));
                iso.sweep();
                r.check("sweep: prunes an entry whose block is now air", entries.isEmpty(), "size=" + entries.size());
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());

                entries.put(pos.asLong(), new BlockDamageStorage.Entry(pos, stone, 100f, 40f, healed));
                iso.sweep();
                r.check("sweep: prunes a fully recovered entry", entries.isEmpty(), "size=" + entries.size());

                r.check("sweep test never touched the live block-damage saved data",
                        BlockDamageStorage.get(level).size() == liveBefore, "live " + liveBefore + "->" + BlockDamageStorage.get(level).size());
            } finally {
                for (var e : new java.util.ArrayList<>(entries.values())) iso.remove(e);   // also clears any crack sent
                sds.close();
            }
        } catch (Exception ex) {
            r.check("sweep: isolated test threw", false, ex.toString());
        } finally {
            if (dir != null) try (var walk = java.nio.file.Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            } catch (java.io.IOException ignored) {}
        }
    }

    /** Dev diagnostic (read-only): how much persisted block damage each level holds. Never sweeps or prunes. */
    private static void reportPersistedState(MinecraftServer server) {
        for (ServerLevel l : server.getAllLevels()) {
            int size = BlockDamageStorage.get(l).size();
            if (size == 0) continue;
            Totality.LOGGER.info("[MiningVerification] {}: {} persisted damaged block(s) (game time {})",
                    l.dimension().identifier(), size, l.getGameTime());
        }
    }

    private static void pure(VerificationReporter r) {
        r.check("crack stage: undamaged -> none", BlockDurability.crackStage(100, 100) == -1, "");
        r.check("crack stage: 82/100 -> 1", BlockDurability.crackStage(82, 100) == 1, "" + BlockDurability.crackStage(82, 100));
        r.check("crack stage: 10/100 -> 9", BlockDurability.crackStage(10, 100) == 9, "");
        r.check("crack stage: 0/100 clamps to 9", BlockDurability.crackStage(0, 100) == 9, "");
        r.check("recovery: nothing during delay", BlockDamageStorage.recovered(50, 100, 0, MiningTuning.RECOVERY_DELAY_TICKS) == 50, "");
        r.check("recovery: partial after delay",
                BlockDamageStorage.recovered(50, 100, 0, MiningTuning.RECOVERY_DELAY_TICKS + 600) > 50, "");
        r.check("recovery: capped at max", BlockDamageStorage.recovered(50, 100, 0, 1_000_000) == 100, "");
        r.check("DEX bare-hand tier: 10->0, 12->1, 16->2, 20->3, 24->4, 28->5 (capped)",
                MiningTuning.bareHandTier(10) == 0 && MiningTuning.bareHandTier(12) == 1 && MiningTuning.bareHandTier(16) == 2
                        && MiningTuning.bareHandTier(20) == 3 && MiningTuning.bareHandTier(24) == 4
                        && MiningTuning.bareHandTier(28) == 5 && MiningTuning.bareHandTier(100) == MiningTuning.TIER_MAX, "");
        r.check("power damage multiplier is bounded 1..2 (0->1, .25->1.25, .5->1.5, .75->1.75, 1->2, 5->2, -1->1, NaN->1)",
                MiningTuning.powerDamageMultiplier(0f) == 1f && MiningTuning.powerDamageMultiplier(0.25f) == 1.25f
                        && MiningTuning.powerDamageMultiplier(0.5f) == 1.5f && MiningTuning.powerDamageMultiplier(0.75f) == 1.75f
                        && MiningTuning.powerDamageMultiplier(1f) == 2f && MiningTuning.powerDamageMultiplier(5f) == 2f
                        && MiningTuning.powerDamageMultiplier(-1f) == 1f && MiningTuning.powerDamageMultiplier(Float.NaN) == 1f, "");
        r.check("force load (stress only): 1 at force 0; STR 10 max -> 2; STR 100 max -> 11",
                MiningTuning.forceLoad(0f, 100) == 1f && MiningTuning.forceLoad(1f, 10) == 2f && MiningTuning.forceLoad(1f, 100) == 11f, "");
        r.check("MiningIntentPayload carries no force value on the wire",
                java.util.Arrays.stream(zcylas.totality.networking.mining.MiningIntentPayload.class.getRecordComponents())
                        .map(c -> c.getName()).toList().equals(List.of("action")), "");
        BlockDamageStorage.Entry e = new BlockDamageStorage.Entry(new BlockPos(1, 2, 3),
                net.minecraft.resources.Identifier.withDefaultNamespace("stone"), 100f, 42f, 77L);
        var enc = BlockDamageStorage.Entry.CODEC.encodeStart(JsonOps.INSTANCE, e).result();
        var dec = enc.flatMap(j -> BlockDamageStorage.Entry.CODEC.parse(JsonOps.INSTANCE, j).result());
        r.check("persistence: entry codec round-trips", dec.isPresent() && dec.get().integrity == 42f
                && dec.get().lastImpactTick == 77L && dec.get().pos.equals(new BlockPos(1, 2, 3)), "" + dec);
        var list = BlockDamageStorage.Data.CODEC.encodeStart(JsonOps.INSTANCE, new BlockDamageStorage.Data(List.of(e))).result();
        r.check("persistence: data codec round-trips", list.flatMap(j -> BlockDamageStorage.Data.CODEC.parse(JsonOps.INSTANCE, j).result())
                .map(d -> d.entries.size() == 1).orElse(false), "");
    }

    /** Real save -> new storage instance -> load round trip through vanilla's SavedDataStorage (the path that failed live). */
    private static void persistence(VerificationReporter r, MinecraftServer server, ServerLevel level, BlockPos pos) {
        java.nio.file.Path dir = null;
        try {
            dir = java.nio.file.Files.createTempDirectory("totality_block_damage_test");
            var id = net.minecraft.resources.Identifier.withDefaultNamespace("stone");
            long impactTick = 123456L;

            var first = new net.minecraft.world.level.storage.SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess());
            var written = BlockDamageStorage.open(first);
            var entry = new BlockDamageStorage.Entry(pos, id, 100f, 28f, impactTick);
            written.get().entries.put(pos.asLong(), entry);
            written.set(written.get());
            first.close();                                    // = server shutdown save

            var second = new net.minecraft.world.level.storage.SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess());
            var loaded = BlockDamageStorage.open(second);     // = world reopened
            var got = loaded.get().entries.get(pos.asLong());
            r.check("persistence: damaged block survives a real save/reload", got != null, "entries=" + loaded.get().entries.size());
            if (got != null) {
                r.check("persistence: block identity, max, integrity and last-impact tick preserved",
                        got.block.equals(id) && got.max == 100f && got.integrity == 28f && got.lastImpactTick == impactTick, "");
                r.check("persistence: reload itself does not heal (same tick -> same integrity)",
                        got.currentIntegrity(impactTick) == 28f && got.currentIntegrity(impactTick + 100) == 28f, "");
                r.check("persistence: healing only after the authored delay",
                        got.currentIntegrity(impactTick + MiningTuning.RECOVERY_DELAY_TICKS + 60) > 28f, "");
                BlockDamageStorage reattached = BlockDamageStorage.over(level, loaded);
                r.check("persistence: reloaded state re-attaches to the same block", reattached.get(pos, Blocks.STONE.defaultBlockState()) != null, "");
                r.check("persistence: reloaded state is invalid for a replacement block at the same pos",
                        reattached.get(pos, Blocks.DIRT.defaultBlockState()) == null && loaded.get().entries.isEmpty(), "");
            }
            second.close();
        } catch (Exception ex) {
            r.check("persistence: round trip threw", false, ex.toString());
        } finally {
            if (dir != null) try (var walk = java.nio.file.Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            } catch (java.io.IOException ignored) {}
        }
    }

    private static void durability(VerificationReporter r, ServerLevel level, BlockPos pos) {
        var stone = BlockDurability.resolve(level, pos, Blocks.STONE.defaultBlockState());
        r.check("durability fallback: stone = 100, tier 1", Math.abs(stone.max() - 100f) < 0.01f && stone.requiredTier() == 1, "" + stone);
        r.check("durability fallback: iron ore needs tier 2", BlockDurability.resolve(level, pos, Blocks.IRON_ORE.defaultBlockState()).requiredTier() == 2, "");
        r.check("durability fallback: diamond ore needs tier 3", BlockDurability.resolve(level, pos, Blocks.DIAMOND_ORE.defaultBlockState()).requiredTier() == 3, "");
        r.check("durability fallback: dirt needs no tier", BlockDurability.resolve(level, pos, Blocks.DIRT.defaultBlockState()).requiredTier() == 0, "");
        r.check("durability fallback: bedrock unbreakable", BlockDurability.resolve(level, pos, Blocks.BEDROCK.defaultBlockState()).unbreakable(), "");
    }

    private static void integrity(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
        float[] expected = {82, 64, 46, 28, 10};
        boolean ok = true;
        String detail = "";
        for (float exp : expected) {
            MiningResult res = BlockBreaking.applyImpact(level, impact(pos, 18f, 1, MiningSource.Kind.MOB));
            ok &= res.outcome() == MiningResult.Outcome.DAMAGED && Math.abs(res.remaining() - exp) < 0.01f;
            detail += res.outcome() + ":" + res.remaining() + " ";
        }
        r.check("stone 100 -> 82 -> 64 -> 46 -> 28 -> 10 with absolute damage", ok, detail);
        var entry = storage.get(pos, level.getBlockState(pos));
        r.check("damage is stored per position", entry != null && Math.abs(entry.integrity - 10f) < 0.01f, "");
        MiningResult last = BlockBreaking.applyImpact(level, impact(pos, 18f, 1, MiningSource.Kind.MOB));
        r.check("final impact breaks the block and clears state", last.outcome() == MiningResult.Outcome.BROKEN
                && level.getBlockState(pos).isAir() && storage.get(pos, Blocks.STONE.defaultBlockState()) == null, "" + last);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    private static void sharedAndIdentity(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
        BlockBreaking.applyImpact(level, impact(pos, 30f, 1, MiningSource.Kind.MOB));
        MiningResult second = BlockBreaking.applyImpact(level, impact(pos, 30f, 1, MiningSource.Kind.MACHINE));
        r.check("two different sources share one integrity (100-30-30)", Math.abs(second.remaining() - 40f) < 0.01f, "" + second);

        var cracks = storage.cracksNear(Vec3.atCenterOf(pos));
        r.check("a (re)joining player gets the persisted crack immediately (stage for 40/100 integrity)",
                cracks.size() == 1 && cracks.get(0).getProgress() == BlockDurability.crackStage(40f, 100f)
                        && cracks.get(0).getPos().equals(pos) && cracks.get(0).getId() < 0,
                "" + cracks.size());
        level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());   // replaced at the same BlockPos
        MiningResult dirt = BlockBreaking.applyImpact(level, impact(pos, 10f, 0, MiningSource.Kind.MOB));
        float dirtMax = BlockDurability.resolve(level, pos, Blocks.DIRT.defaultBlockState()).max();
        r.check("replacement block does not inherit old damage", Math.abs(dirt.remaining() - (dirtMax - 10f)) < 0.01f,
                "remaining=" + dirt.remaining() + " dirtMax=" + dirtMax);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    private static void tiers(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
        MiningResult low = BlockBreaking.applyImpact(level, impact(pos, 50f, 1, MiningSource.Kind.MOB));
        r.check("insufficient tier: ineffective, no integrity lost",
                low.outcome() == MiningResult.Outcome.INEFFECTIVE && storage.get(pos, level.getBlockState(pos)) == null, "" + low);
        level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
        r.check("unbreakable block is an invalid target",
                BlockBreaking.applyImpact(level, impact(pos, 1e6f, 5, MiningSource.Kind.MOB)).outcome() == MiningResult.Outcome.INVALID, "");
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // Force tolerance / tool stress (pure)
    // ─────────────────────────────────────────────────────────────────────────────────────────

    private static boolean destroyed(ItemStack tool, int stress) { return stress >= tool.getMaxDamage(); }

    private static void forceTolerance(VerificationReporter r) {
        ItemStack[] picks = {
                new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE)};
        String[] names = {"wooden", "golden", "stone", "iron", "diamond", "netherite"};
        ItemStack wood = picks[0], gold = picks[1], stone = picks[2], iron = picks[3], diamond = picks[4], netherite = picks[5];

        r.check("tool tiers: hand 0, wood 1, gold 1, stone 2, iron 3, diamond 4, netherite 4",
                MiningTier.ofTool(ItemStack.EMPTY) == 0 && MiningTier.ofTool(wood) == 1 && MiningTier.ofTool(gold) == 1
                        && MiningTier.ofTool(stone) == 2 && MiningTier.ofTool(iron) == 3 && MiningTier.ofTool(diamond) == 4
                        && MiningTier.ofTool(netherite) == 4, "");
        float[] tol = new float[picks.length];
        for (int i = 0; i < picks.length; i++) tol[i] = MiningTuning.forceTolerance(picks[i]);
        r.check("force tolerance ordering: gold < wood < stone < iron < diamond < netherite",
                tol[1] < tol[0] && tol[0] < tol[2] && tol[2] < tol[3] && tol[3] < tol[4] && tol[4] < tol[5], java.util.Arrays.toString(tol));
        r.check("force tolerance is not item durability (gold: lowest tolerance although not the least durable rank-wise)",
                gold.getMaxDamage() < wood.getMaxDamage() && tol[1] < tol[0], "");

        float l10n = MiningTuning.forceLoad(0f, 10), l10r = MiningTuning.forceLoad(1f, 10);
        float l100n = MiningTuning.forceLoad(0f, 100), l100r = MiningTuning.forceLoad(1f, 100);
        int[] s100r = new int[picks.length];
        StringBuilder line = new StringBuilder("STR 100 red-zone stress:");
        for (int i = 0; i < picks.length; i++) {
            ItemStack t = picks[i];
            r.check(names[i] + " pickaxe: STR 10 normal force costs nothing", MiningTuning.toolStress(t, l10n) == 0, "");
            int s10r = MiningTuning.toolStress(t, l10r);
            r.check(names[i] + " pickaxe: STR 10 red zone is safe (< 10% durability, not destroyed)",
                    !destroyed(t, s10r) && s10r < t.getMaxDamage() * 0.10f, "stress=" + s10r + "/" + t.getMaxDamage());
            r.check(names[i] + " pickaxe: STR 100 normal force costs nothing", MiningTuning.toolStress(t, l100n) == 0, "");
            s100r[i] = MiningTuning.toolStress(t, l100r);
            line.append(' ').append(names[i]).append('=').append(s100r[i]).append('/').append(t.getMaxDamage());
        }
        Totality.LOGGER.info("[MiningVerification] {}", line);
        r.check("STR 100 red zone destroys wooden, golden, stone and iron pickaxes",
                destroyed(wood, s100r[0]) && destroyed(gold, s100r[1]) && destroyed(stone, s100r[2]) && destroyed(iron, s100r[3]),
                java.util.Arrays.toString(s100r));
        r.check("STR 100 red zone: diamond loses severe durability (30-60%) but survives",
                !destroyed(diamond, s100r[4]) && s100r[4] >= diamond.getMaxDamage() * 0.30f && s100r[4] <= diamond.getMaxDamage() * 0.60f,
                "stress=" + s100r[4] + "/" + diamond.getMaxDamage());
        r.check("STR 100 red zone: netherite wear is noticeable (40-100) yet far below diamond",
                s100r[5] >= 40 && s100r[5] <= 100 && s100r[5] < s100r[4] / 4, "stress=" + s100r[5]);
        r.check("netherite is not indestructible (STR 300 red zone overloads it)",
                MiningTuning.toolStress(netherite, MiningTuning.forceLoad(1f, 300)) > netherite.getMaxDamage() * 0.5f, "");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // Corrected invariants (bare hands, tiers, sources, damage/cadence separation, wear, drops...)
    // ─────────────────────────────────────────────────────────────────────────────────────────

    private static void setScore(ServerPlayer p, zcylas.totality.api.rpg.stats.AbilityScore score, int value) {
        var stats = zcylas.totality.api.rpg.stats.StatsComponents.getStats(p);
        stats.setSpentPointsDirectly(score, value - 10);
        stats.recalculate();
    }

    private static void equip(ServerPlayer p, ItemStack stack) { p.setItemSlot(EquipmentSlot.MAINHAND, stack); }

    private static net.minecraft.world.phys.BlockHitResult topHit(BlockPos pos) {
        return new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    /** Item entities near {@code pos}. Uses the level's full entity list: the test chunk is not near any player,
     *  so its entities are not visible to ordinary area queries. */
    private static java.util.List<net.minecraft.world.entity.item.ItemEntity> itemsNear(ServerLevel level, BlockPos pos) {
        var out = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        for (var e : level.getAllEntities())
            if (e instanceof net.minecraft.world.entity.item.ItemEntity i && i.blockPosition().distManhattan(pos) <= 6) out.add(i);
        return out;
    }

    private static void clearItems(ServerLevel level, BlockPos pos) { for (var e : itemsNear(level, pos)) e.discard(); }

    private static int stacksOf(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item item) {
        int n = 0;
        for (var e : itemsNear(level, pos)) if (e.getItem().is(item)) n += e.getItem().getCount();
        return n;
    }

    /** What {@code Player.hasCorrectToolForDrops} said INSIDE {@code ServerPlayerGameMode.destroyBlock} (Fabric's BEFORE
     *  event fires there, just before vanilla decides whether to run the harvest/drop path). Null = event did not fire. */
    private static volatile ServerPlayer recordFor;
    private static volatile Boolean recordedEligible;

    private static void registerHarvestProbe() {
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((lvl, player, bp, st, be) -> {
            if (player == recordFor) recordedEligible = player.hasCorrectToolForDrops(st);
            return true;
        });
    }

    private static Boolean probeEligibility(ServerPlayer p, Runnable breakAction) {
        recordFor = p; recordedEligible = null;
        try { breakAction.run(); } finally { recordFor = null; }
        return recordedEligible;
    }

    private static void resetSpeedModifiers(ServerPlayer p) {
        p.removeAllEffects();
        var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY);
        if (attr != null) attr.removeModifier(EFFICIENCY_ID);
    }

    private static final net.minecraft.resources.Identifier EFFICIENCY_ID =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(Totality.MOD_ID, "verification_efficiency");

    private static void corrected(VerificationReporter r, MinecraftServer server, ServerLevel level) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        var DEX = zcylas.totality.api.rpg.stats.AbilityScore.DEX;
        BlockPos pos = new BlockPos(100, level.getMaxY() - 8, 100);   // clear of spawn protection
        level.getChunkAt(pos);
        BlockState original = level.getBlockState(pos);
        // harvestGrantScope / wearAndSwap also place blocks at east(3) / west(1); restore those too.
        BlockPos east = pos.east(3), west = pos.west(1);
        BlockState originalEast = level.getBlockState(east), originalWest = level.getBlockState(west);
        BlockDamageStorage storage = BlockDamageStorage.get(level);
        ServerPlayer p = TotalityFakePlayer.create(level, "[MiningVerification2]");
        try {
            p.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            p.setXRot(90f);                       // looking straight down at the block under the feet
            p.setOnGround(true);
            BlockState stoneState = Blocks.STONE.defaultBlockState();

            // ---- bare-hand damage: 1 + STR modifier ----
            equip(p, ItemStack.EMPTY);
            setScore(p, DEX, 10);
            int[] strs = {10, 12, 14, 16};
            float[] expect = {1, 2, 3, 4};
            boolean ok = true; String d = "";
            for (int i = 0; i < strs.length; i++) {
                setScore(p, STR, strs[i]);
                var res = PlayerMiningPower.compute(p, stoneState, 0f);
                ok &= res.damage() == expect[i] && res.kind() == MiningSource.Kind.BARE_HANDS;
                d += strs[i] + "->" + res.damage() + " ";
            }
            r.check("bare-hand damage = 1 + STR modifier (STR 10/12/14/16 -> 1/2/3/4)", ok, d);
            setScore(p, STR, 1);
            r.check("negative STR modifier is clamped, never negative damage",
                    PlayerMiningPower.compute(p, stoneState, 0f).damage() == MiningTuning.MIN_IMPACT_DAMAGE, "");
            setScore(p, STR, 100);
            r.check("STR 100 bare hands is no longer absurd (1 + 45 = 46, not a one-shot)",
                    PlayerMiningPower.compute(p, stoneState, 0f).damage() == 46f, "");

            // ---- source classification ----
            ItemStack stick = new ItemStack(Items.STICK);
            r.check("empty hand and an arbitrary non-tool item are bare-hand sources",
                    !MiningTier.isToolSource(ItemStack.EMPTY) && !MiningTier.isToolSource(stick), "");
            r.check("pickaxe, axe, shovel, hoe and shears are tool sources (real TOOL component), independent of pickaxe tier probing",
                    MiningTier.isToolSource(new ItemStack(Items.IRON_PICKAXE)) && MiningTier.isToolSource(new ItemStack(Items.IRON_AXE))
                            && MiningTier.isToolSource(new ItemStack(Items.WOODEN_SHOVEL)) && MiningTier.isToolSource(new ItemStack(Items.IRON_HOE))
                            && MiningTier.isToolSource(new ItemStack(Items.SHEARS)), "");
            r.check("an axe probes pickaxe Tier 0 yet is still a tool source",
                    MiningTier.ofTool(new ItemStack(Items.IRON_AXE)) == 0 && MiningTier.isToolSource(new ItemStack(Items.IRON_AXE)), "");
            setScore(p, STR, 10);
            equip(p, new ItemStack(Items.IRON_AXE));
            var axeOnLog = PlayerMiningPower.compute(p, Blocks.OAK_LOG.defaultBlockState(), 0f);
            // Tier fix pass: an Iron Axe's Mining Tier is now the authored Iron material Tier (same as
            // an Iron Pickaxe), never the old pickaxe-probe-only 0 — see targetEffectivenessMath for the
            // dedicated Pickaxe==Axe Tier equality checks across all six materials.
            r.check("axe on wood: PLAYER_TOOL, PROFILED authored Iron Mining Damage (50), independent of target block",
                    axeOnLog.kind() == MiningSource.Kind.PLAYER_TOOL && axeOnLog.damage() == 50f
                            && axeOnLog.tier() == MiningTier.ofTool(new ItemStack(Items.IRON_PICKAXE))
                            && axeOnLog.profiled(), "" + axeOnLog);
            // Shovel vertical-slice pass (§5-9): Wooden Shovel is now PROFILED (authored Wood Mining
            // Damage 20) and Dirt is #minecraft:mineable/shovel, so this is a MATCHING hit, x1.00.
            equip(p, new ItemStack(Items.WOODEN_SHOVEL));
            var shovelOnDirt = PlayerMiningPower.compute(p, Blocks.DIRT.defaultBlockState(), 0f);
            r.check("shovel on dirt: PLAYER_TOOL, PROFILED authored Wood Mining Damage (20), matching tool",
                    shovelOnDirt.kind() == MiningSource.Kind.PLAYER_TOOL && shovelOnDirt.damage() == 20f && shovelOnDirt.profiled(), "" + shovelOnDirt);
            equip(p, new ItemStack(Items.IRON_PICKAXE));
            var pickOnDirt = PlayerMiningPower.compute(p, Blocks.DIRT.defaultBlockState(), 0f);
            // Wrong-tool fix pass (§2), tightened to x0.10 in the playtest-correction pass (§1): Dirt
            // is not #minecraft:mineable/pickaxe, so the authored 50 is scaled by the x0.10 wrong-tool
            // multiplier -> 5, not the flat 50 pre-effectiveness value.
            r.check("pickaxe on dirt stays a tool source, PROFILED authored Iron Mining Damage (50 x 0.10 wrong-tool = 5)",
                    pickOnDirt.kind() == MiningSource.Kind.PLAYER_TOOL && pickOnDirt.damage() == 5f && pickOnDirt.profiled(), "" + pickOnDirt);
            equip(p, stick);
            r.check("holding a stick: bare hands", PlayerMiningPower.compute(p, stoneState, 0f).kind() == MiningSource.Kind.BARE_HANDS, "");

            // ---- tier comes from the ACTIVE source only ----
            setScore(p, DEX, 28);
            equip(p, new ItemStack(Items.WOODEN_PICKAXE));
            var woodHighDex = PlayerMiningPower.compute(p, Blocks.IRON_ORE.defaultBlockState(), 0f);
            r.check("high DEX does NOT upgrade a held wooden pickaxe (tier stays 1)", woodHighDex.tier() == 1
                    && woodHighDex.kind() == MiningSource.Kind.PLAYER_TOOL, "" + woodHighDex);
            equip(p, ItemStack.EMPTY);
            r.check("DEX 28 bare hands: tier 5; tier is DEX-derived, not STR", PlayerMiningPower.compute(p, stoneState, 0f).tier() == 5, "");
            setScore(p, DEX, 10);
            setScore(p, STR, 100);
            r.check("STR 100 with DEX 10: bare-hand tier 0 (high STR does not grant tier)", PlayerMiningPower.compute(p, stoneState, 0f).tier() == 0, "");

            // ---- V2: STR no longer affects a PROFILED tool's normal Mining Damage ----
            equip(p, new ItemStack(Items.NETHERITE_PICKAXE));
            setScore(p, STR, 14);
            float d14 = PlayerMiningPower.compute(p, stoneState, 0f).damage();
            setScore(p, STR, 16);
            float d16 = PlayerMiningPower.compute(p, stoneState, 0f).damage();
            Totality.LOGGER.info("[MiningVerification] netherite pickaxe on stone: STR 14 -> {} damage, STR 16 -> {} damage", d14, d16);
            r.check("V2 §2: STR does NOT affect a profiled tool's normal Mining Damage (Netherite stays 100 at STR 14 and 16)",
                    d14 == 100f && d16 == 100f, d14 + " / " + d16);

            // ---- Efficiency / Haste / Fatigue on a NON-PROFILED (specialized) tool: legacy cadence-ratio path, UNCHANGED ----
            // Pass 2: Gold is profiled now, so the legacy path is exercised with Shears on Wool (intrinsic speed 5,
            // so vanilla MINING_EFFICIENCY applies exactly as it did for Gold on Stone).
            BlockState legacyState = Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
            setScore(p, STR, 10);
            equip(p, new ItemStack(Items.SHEARS));
            resetSpeedModifiers(p);
            int swing = p.getMainHandItem().getSwingAnimation().duration();
            float baseDmg = PlayerMiningPower.compute(p, legacyState, 0f).damage();
            float basePow = PlayerMiningPower.compute(p, legacyState, 1f).damage();
            float baseLoad = PlayerMiningPower.compute(p, legacyState, 1f).forceLoad();
            float ratioBase = PlayerMiningPower.cadenceRatio(p, legacyState);
            var eff = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY);
            eff.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(EFFICIENCY_ID, 26.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            float dmgE = PlayerMiningPower.compute(p, legacyState, 0f).damage(), powE = PlayerMiningPower.compute(p, legacyState, 1f).damage();
            float loadE = PlayerMiningPower.compute(p, legacyState, 1f).forceLoad(), ratioE = PlayerMiningPower.cadenceRatio(p, legacyState);
            eff.removeModifier(EFFICIENCY_ID);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HASTE, 4000, 1));
            float dmgH = PlayerMiningPower.compute(p, legacyState, 0f).damage(), powH = PlayerMiningPower.compute(p, legacyState, 1f).damage();
            float loadH = PlayerMiningPower.compute(p, legacyState, 1f).forceLoad(), ratioH = PlayerMiningPower.cadenceRatio(p, legacyState);
            eff.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(EFFICIENCY_ID, 26.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            float dmgEH = PlayerMiningPower.compute(p, legacyState, 0f).damage(), powEH = PlayerMiningPower.compute(p, legacyState, 1f).damage();
            float ratioEH = PlayerMiningPower.cadenceRatio(p, legacyState);
            resetSpeedModifiers(p);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, 4000, 0));
            float dmgF = PlayerMiningPower.compute(p, legacyState, 0f).damage(), powF = PlayerMiningPower.compute(p, legacyState, 1f).damage();
            float loadF = PlayerMiningPower.compute(p, legacyState, 1f).forceLoad(), ratioF = PlayerMiningPower.cadenceRatio(p, legacyState);
            resetSpeedModifiers(p);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, 4000, 3));
            float ratioExtremeFatigue = PlayerMiningPower.cadenceRatio(p, legacyState);
            resetSpeedModifiers(p);
            eff.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(EFFICIENCY_ID, 100000.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            float ratioExtremeEff = PlayerMiningPower.cadenceRatio(p, legacyState);
            resetSpeedModifiers(p);

            r.check("Shears (specialized, non-profiled): damage per hit is IDENTICAL with Efficiency V / Haste II / Efficiency+Haste / Mining Fatigue I",
                    dmgE == baseDmg && dmgH == baseDmg && dmgEH == baseDmg && dmgF == baseDmg && baseDmg == 30f,
                    baseDmg + " " + dmgE + " " + dmgH + " " + dmgEH + " " + dmgF);
            r.check("Shears (specialized, non-profiled): Power Mining damage is identical under Efficiency / Haste / Fatigue (60 = 30 x 2)",
                    powE == basePow && powH == basePow && powEH == basePow && powF == basePow && basePow == 60f, "");
            r.check("Power force load (tool stress input) is independent of Efficiency / Haste / Fatigue",
                    loadE == baseLoad && loadH == baseLoad && loadF == baseLoad, "");
            int cBase = MiningTuning.cycleTicks(swing, ratioBase), cE = MiningTuning.cycleTicks(swing, ratioE);
            int cH = MiningTuning.cycleTicks(swing, ratioH), cEH = MiningTuning.cycleTicks(swing, ratioEH);
            int cF = MiningTuning.cycleTicks(swing, ratioF);
            Totality.LOGGER.info("[MiningVerification] cadence ratio/cycle ticks: base {}/{}, Efficiency {}/{}, Haste II {}/{}, Eff+Haste {}/{}, Fatigue I {}/{}",
                    ratioBase, cBase, ratioE, cE, ratioH, cH, ratioEH, cEH, ratioF, cF);
            r.check("cadence: Efficiency V is faster than baseline", ratioE > ratioBase && cE < cBase, "");
            r.check("cadence: Haste II is faster than baseline", ratioH > ratioBase && cH < cBase, "");
            r.check("cadence: Mining Fatigue I is slower than baseline", ratioF < ratioBase && cF > cBase, "");
            r.check("cadence: Efficiency and Haste combine", ratioEH > ratioE && ratioEH > ratioH && cEH <= Math.min(cE, cH), "");
            r.check("cadence is clamped: extreme Efficiency never makes swings faster than the floor, extreme Fatigue never stalls forever",
                    MiningTuning.cycleTicks(swing, ratioExtremeEff) >= MiningTuning.MIN_WINDUP_TICKS + MiningTuning.MIN_RECOVERY_TICKS
                            && MiningTuning.cycleTicks(swing, ratioExtremeFatigue) <= 400
                            && ratioExtremeEff <= MiningTuning.MAX_CADENCE_RATIO && ratioExtremeFatigue >= MiningTuning.MIN_CADENCE_RATIO, "");
            r.check("power swings ignore cadence entirely (wind-up identical whatever the ratio)",
                    MiningTuning.windUpTicks(swing, true, 0.05f) == MiningTuning.windUpTicks(swing, true, 20f), "");

            // ---- Pass 3: Swords and Shears are excluded from Alt/Power Mining ----
            for (ItemStack excluded : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.SHEARS))) {
                equip(p, excluded);
                PlayerMiningManager.forget(p);
                PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_START));
                PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_RELEASE));
                r.check("Pass 3: " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(excluded.getItem()) + " cannot start or release a Power swing (no queued force)",
                        MiningTier.excludedFromPowerMining(excluded) && PlayerMiningManager.queuedForce(p) < 0f, "queued=" + PlayerMiningManager.queuedForce(p));
            }
            equip(p, new ItemStack(Items.IRON_PICKAXE));
            PlayerMiningManager.forget(p);
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_START));
            equip(p, new ItemStack(Items.IRON_SWORD));                    // swapped to a Sword before releasing
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_RELEASE));
            r.check("Pass 3: a Power hold started with a Pickaxe cannot be released through a Sword", PlayerMiningManager.queuedForce(p) < 0f, "");
            r.check("Pass 3: bare hands and profiled tools stay Power-eligible (Iron Pickaxe, Gold Hoe, empty hand, stick)",
                    !MiningTier.excludedFromPowerMining(new ItemStack(Items.IRON_PICKAXE)) && !MiningTier.excludedFromPowerMining(new ItemStack(Items.GOLDEN_HOE))
                            && !MiningTier.excludedFromPowerMining(ItemStack.EMPTY) && !MiningTier.excludedFromPowerMining(new ItemStack(Items.STICK)), "");
            equip(p, new ItemStack(Items.IRON_PICKAXE));

            // ---- server-authoritative Power force (kept) ----
            PlayerMiningManager.forget(p);
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_RELEASE));
            r.check("power swing without a server-seen POWER_START is ignored", PlayerMiningManager.queuedForce(p) < 0f, "");
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_START));
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_RELEASE));
            float queued = PlayerMiningManager.queuedForce(p);
            r.check("power force is derived by the server and stays within [0,1]", queued >= 0f && queued <= 1f, "queued=" + queued);
            PlayerMiningManager.forget(p);
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_START));
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_CANCEL));
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(zcylas.totality.networking.mining.MiningIntentPayload.Action.POWER_RELEASE));
            r.check("a cancelled power hold cannot be released into a swing", PlayerMiningManager.queuedForce(p) < 0f, "");
            boolean inRange = true;
            for (long t = 0; t < 5000; t += 7) { float v = MiningTuning.meterValue(t); inRange &= v >= 0f && v <= 1f; }
            inRange &= MiningTuning.meterValue(Long.MAX_VALUE) >= 0f && MiningTuning.meterValue(Long.MAX_VALUE) <= 1f;
            r.check("meter value can never leave [0,1] whatever the hold length", inRange, "");
            PlayerMiningManager.forget(p);

            // ---- durability accounting through the real strike path ----
            setScore(p, STR, 10);
            setScore(p, DEX, 10);
            durability(r, level, p, pos, storage);

            // ---- drops for qualified bare hands, tier gate, no vanilla leak ----
            drops(r, level, p, pos, storage);

            // ---- ownership symmetry ----
            var stone = Blocks.STONE.defaultBlockState();
            ItemStack spear = new ItemStack(Items.WOODEN_SPEAR);
            level.setBlockAndUpdate(pos, stone);
            r.check("ownership: survival + pickaxe + stone owned; spear (PIERCING_WEAPON), creative, adventure, spectator, torch, bedrock are not",
                    MiningOwnership.owns(net.minecraft.world.level.GameType.SURVIVAL, new ItemStack(Items.IRON_PICKAXE), level, pos, stone)
                            && !MiningOwnership.owns(net.minecraft.world.level.GameType.SURVIVAL, spear, level, pos, stone)
                            && !MiningOwnership.owns(net.minecraft.world.level.GameType.CREATIVE, new ItemStack(Items.IRON_PICKAXE), level, pos, stone)
                            && !MiningOwnership.owns(net.minecraft.world.level.GameType.ADVENTURE, new ItemStack(Items.IRON_PICKAXE), level, pos, stone)
                            && !MiningOwnership.owns(net.minecraft.world.level.GameType.SPECTATOR, ItemStack.EMPTY, level, pos, stone)
                            && !MiningOwnership.owns(net.minecraft.world.level.GameType.SURVIVAL, ItemStack.EMPTY, level, pos, Blocks.TORCH.defaultBlockState())
                            && !MiningOwnership.owns(net.minecraft.world.level.GameType.SURVIVAL, ItemStack.EMPTY, level, pos, Blocks.BEDROCK.defaultBlockState()), "");
            equip(p, spear);
            boolean serverSpear = PlayerMiningManager.ownsMining(p, level, pos);
            equip(p, new ItemStack(Items.IRON_PICKAXE));
            boolean serverPick = PlayerMiningManager.ownsMining(p, level, pos);
            r.check("server ownership uses the same shared rule (spear -> vanilla, pickaxe -> Totality) as the client",
                    !serverSpear && serverPick, "spear=" + serverSpear + " pick=" + serverPick);

            // ---- exact wear with damagePerBlock=2, mid-swing tool swap, animation timeline ----
            wearAndSwap(r, level, p, pos, storage);
            authoredMiningStatsV2(r, level);
            profiledCadenceStateMachine(r, server, level, p, pos);
            targetEffectivenessMath(r, level, p, pos, storage);
            conventionalToolCompletion(r, server, level, p, pos);
            powerFourBands(r, level, p, pos, storage);
            vanillaItemPresentation(r);
            animation(r, server);

            // ---- persisted crack sync prunes stale entries ----
            equip(p, ItemStack.EMPTY);
            crackSync(r, level, pos, storage);

            // ---- Combat Text presentation ----
            presentation(r, server);
        } catch (Exception ex) {
            r.check("corrected-invariants suite threw", false, ex.toString());
            Totality.LOGGER.error("[MiningVerification] suite exception", ex);
        } finally {
            resetSpeedModifiers(p);
            var e = storage.get(pos, level.getBlockState(pos));
            if (e != null) storage.remove(e);
            clearItems(level, pos);
            level.setBlockAndUpdate(pos, original);
            for (BlockPos side : new BlockPos[]{east, west}) {
                var se = storage.get(side, level.getBlockState(side));
                if (se != null) storage.remove(se);
            }
            level.setBlockAndUpdate(east, originalEast);
            level.setBlockAndUpdate(west, originalWest);
        }
    }

    private static void durability(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos, BlockDamageStorage storage) {
        // (a) V2 authored Iron Mining Damage (50): 50 + final(50 left) = exactly 2 impacts -> exactly 2 durability
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        equip(p, iron);
        var o1 = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        int w1 = p.getMainHandItem().getDamageValue();
        var o2 = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        int w2 = p.getMainHandItem().getDamageValue();
        r.check("partial impact then final break each cost exactly 1 durability (1,2 total); authored Iron damage (50) breaks 100-Durability Stone in exactly 2 hits",
                o1 == MiningResult.Outcome.DAMAGED && o2 == MiningResult.Outcome.BROKEN && w1 == 1 && w2 == 2,
                o1 + "/" + o2 + " wear " + w1 + "," + w2);
        clearItems(level, pos);

        // (b) one-shot final break: 1 durability, not 2. Netherrack (hardness 0.4, matching-tool
        // #minecraft:mineable/pickaxe, fallback durability ~26.7) rather than Glass — Glass is NOT
        // pickaxe-tagged, so it would silently exercise the wrong-tool x0.10 multiplier instead of
        // this sub-test's actual subject (the terminal-break wear charge).
        setScore(p, zcylas.totality.api.rpg.stats.AbilityScore.STR, 100);
        level.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
        equip(p, new ItemStack(Items.IRON_PICKAXE));
        var one = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        r.check("one-shot final break costs exactly 1 durability (no double charge)",
                one == MiningResult.Outcome.BROKEN && p.getMainHandItem().getDamageValue() == 1, one + " wear=" + p.getMainHandItem().getDamageValue());
        setScore(p, zcylas.totality.api.rpg.stats.AbilityScore.STR, 10);

        // (c)/(d) V2: Power Mining zone-based STR extra wear for a PROFILED tool (Iron Pickaxe, STR 20 -> +5 modifier,
        // ORANGE zone force 0.7): 50 + 2x5 = 60 damage/hit, 1 base + zone extra wear (5) per successful hit.
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        setScore(p, zcylas.totality.api.rpg.stats.AbilityScore.STR, 20);
        ItemStack pw = new ItemStack(Items.IRON_PICKAXE);
        equip(p, pw);
        int strMod20 = 5;
        int zoneExtra = MiningTuning.powerZoneExtraWear(MiningTuning.BAND_ORANGE, strMod20);
        var pp = PlayerMiningManager.strike(p, topHit(pos), true, 0.7f).outcome();
        int wp = p.getMainHandItem().getDamageValue();
        r.check("V2 power partial hit (profiled tool, ORANGE zone) = 1 base + zone extra wear",
                pp == MiningResult.Outcome.DAMAGED && wp == 1 + zoneExtra, "wear=" + wp + " expected=" + (1 + zoneExtra));
        var pf = PlayerMiningManager.strike(p, topHit(pos), true, 0.7f).outcome();
        int wf = p.getMainHandItem().getDamageValue();
        r.check("V2 power final hit: zone extra wear applies on BROKEN too (no extra base charge)",
                pf == MiningResult.Outcome.BROKEN && wf == wp + 1 + zoneExtra, "wear=" + wf + " expected=" + (wp + 1 + zoneExtra));
        clearItems(level, pos);
        setScore(p, zcylas.totality.api.rpg.stats.AbilityScore.STR, 10);

        // (e) miss = 0
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        equip(p, new ItemStack(Items.IRON_PICKAXE));
        p.setXRot(-90f);
        var miss = PlayerMiningManager.contactNow(p, false, 0f, p.getMainHandItem().copy());
        p.setXRot(90f);
        r.check("a swing that misses (looking at the sky) costs nothing and damages nothing",
                miss == null && p.getMainHandItem().getDamageValue() == 0 && storage.get(pos, level.getBlockState(pos)) == null, "");
        // contact with the real raycast at the block does hit
        var real = PlayerMiningManager.contactNow(p, false, 0f, p.getMainHandItem().copy());
        r.check("the same contact frame with the crosshair on the block does hit (DAMAGED, 1 wear)",
                real != null && real.outcome() == MiningResult.Outcome.DAMAGED && p.getMainHandItem().getDamageValue() == 1, "" + real);

        // (f) ineffective = 0 base wear. Tier fix pass: Iron Axe vs Stone is no longer Tier-ineffective
        // (an Axe's authored Mining Tier now matches its Pickaxe's, so it passes Stone's required Tier 1
        // — it becomes a genuine wrong-tool DAMAGED hit instead, see targetEffectivenessMath). Wooden
        // Axe vs Iron Ore (authored Tier 1 < required Tier 3) is used here instead: a genuinely
        // insufficient material Tier, independent of target effectiveness.
        level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
        var e0 = storage.get(pos, level.getBlockState(pos));
        if (e0 != null) storage.remove(e0);
        equip(p, new ItemStack(Items.WOODEN_AXE));
        var ineff = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        var ineffPower = PlayerMiningManager.strike(p, topHit(pos), true, 1f).outcome();
        r.check("ineffective hits (insufficient-Tier Axe vs Iron Ore), normal or power, cost no durability",
                ineff == MiningResult.Outcome.INEFFECTIVE && ineffPower == MiningResult.Outcome.INEFFECTIVE
                        && p.getMainHandItem().getDamageValue() == 0, ineff + "/" + ineffPower);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        // (g) outcome policy
        ItemStack probe = new ItemStack(Items.IRON_PICKAXE);
        r.check("base wear policy: only DAMAGED charges (BROKEN charged by vanilla; DENIED, INEFFECTIVE, INVALID free)",
                MiningTuning.baseWear(MiningResult.Outcome.DAMAGED, probe) == 1
                        && MiningTuning.baseWear(MiningResult.Outcome.BROKEN, probe) == 0
                        && MiningTuning.baseWear(MiningResult.Outcome.DENIED, probe) == 0
                        && MiningTuning.baseWear(MiningResult.Outcome.INEFFECTIVE, probe) == 0
                        && MiningTuning.baseWear(MiningResult.Outcome.INVALID, probe) == 0, "");
        var e1 = storage.get(pos, level.getBlockState(pos));
        if (e1 != null) storage.remove(e1);
    }

    private static void drops(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos, BlockDamageStorage storage) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        var DEX = zcylas.totality.api.rpg.stats.AbilityScore.DEX;
        BlockState stone = Blocks.STONE.defaultBlockState();
        equip(p, ItemStack.EMPTY);

        // controls: an ordinary pickaxe is harvest-eligible inside destroyBlock; a plain vanilla bare-hand break is not
        level.setBlockAndUpdate(pos, stone);
        equip(p, new ItemStack(Items.IRON_PICKAXE));
        Boolean pickEligible = probeEligibility(p, () -> p.gameMode.destroyBlock(pos));
        r.check("control: an iron pickaxe is harvest-eligible inside vanilla destroyBlock", Boolean.TRUE.equals(pickEligible), "" + pickEligible);
        level.setBlockAndUpdate(pos, stone);
        equip(p, ItemStack.EMPTY);
        Boolean vanillaHand = probeEligibility(p, () -> p.gameMode.destroyBlock(pos));
        r.check("negative control: a plain vanilla bare-hand destroyBlock of Stone is NOT harvest-eligible (no leak)",
                Boolean.FALSE.equals(vanillaHand), "" + vanillaHand);
        var drops = net.minecraft.world.level.block.Block.getDrops(stone, level, pos, null, p, ItemStack.EMPTY);
        r.check("Stone's loot table with an empty tool yields cobblestone (so eligibility is all that stood in the way)",
                drops.stream().anyMatch(d -> d.is(Items.COBBLESTONE)), "" + drops);
        clearItems(level, pos);

        // low DEX + high STR: gated by Tier
        level.setBlockAndUpdate(pos, stone);
        clearItems(level, pos);
        setScore(p, STR, 100);
        setScore(p, DEX, 10);
        var low = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        r.check("low DEX bare hands vs Stone: INEFFECTIVE, no damage, even at STR 100",
                low.outcome() == MiningResult.Outcome.INEFFECTIVE && storage.get(pos, level.getBlockState(pos)) == null
                        && level.getBlockState(pos).is(Blocks.STONE), "" + low);

        // sufficient DEX: damage allowed
        setScore(p, DEX, 12);
        setScore(p, STR, 10);
        var one = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        r.check("sufficient DEX bare hands vs Stone: damage allowed (1 damage -> 99 left)",
                one.outcome() == MiningResult.Outcome.DAMAGED && Math.abs(one.remaining() - 99f) < 0.01f, "" + one);

        // eventually breaks and is harvest-eligible (vanilla drop path)
        setScore(p, STR, 100);
        final MiningResult[] lastRef = {one};
        int served0 = HarvestGrant.served();
        MiningResult last = one;
        for (int i = 0; i < 5 && last.outcome() != MiningResult.Outcome.BROKEN; i++)
            last = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        int served = HarvestGrant.served() - served0;
        r.check("sufficient-Tier bare hands break Stone through vanilla destroyBlock and vanilla was told 'eligible' exactly for this break",
                last.outcome() == MiningResult.Outcome.BROKEN && level.getBlockState(pos).isAir() && served >= 1,
                last.outcome() + " served=" + served);
        r.check("no vanilla leak: the grant is closed and a bare hand is still not the 'correct tool' for Stone",
                !HarvestGrant.isOpen() && HarvestGrant.openCount() == 0 && !p.hasCorrectToolForDrops(stone), "");
        harvestGrantScope(r, level, p, pos);
        clearItems(level, pos);

        // a weak held tool is not rescued by high DEX
        setScore(p, DEX, 28);
        level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
        equip(p, new ItemStack(Items.WOODEN_PICKAXE));
        var ore = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        r.check("DEX 28 with a wooden pickaxe vs iron ore: INEFFECTIVE (tool tier decides, not DEX)",
                ore.outcome() == MiningResult.Outcome.INEFFECTIVE && level.getBlockState(pos).is(Blocks.IRON_ORE), "" + ore);
        var rem = storage.get(pos, level.getBlockState(pos));
        if (rem != null) storage.remove(rem);
        setScore(p, DEX, 10);
        setScore(p, STR, 10);
        equip(p, ItemStack.EMPTY);
    }

    /** Exact-position grant: consulted by destroyBlock's own eligibility query with destroyBlock's own position. */
    private static void harvestGrantScope(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos other = pos.east(3);
        ServerPlayer stranger = TotalityFakePlayer.create(level, "[MiningVerification3]");
        stranger.setPos(other.getX() + 0.5, other.getY() + 1, other.getZ() + 0.5);
        equip(p, ItemStack.EMPTY);
        equip(stranger, ItemStack.EMPTY);
        int served0 = HarvestGrant.served();
        boolean intended, samePlayerOtherPos, otherPlayer, otherBlockType, second, afterClose;
        try (HarvestGrant g = HarvestGrant.open(p, level, pos, Blocks.STONE)) {
            samePlayerOtherPos = HarvestGrant.grants(p, other, stone);                       // same player, same type, another position
            otherPlayer = HarvestGrant.grants(stranger, pos, stone);                         // another player at the granted position
            otherBlockType = HarvestGrant.grants(p, pos, Blocks.IRON_ORE.defaultBlockState());
            intended = HarvestGrant.grants(p, pos, stone);                                   // the exact break
            second = HarvestGrant.grants(p, pos, stone);                                     // single use
        }
        afterClose = HarvestGrant.grants(p, pos, stone);
        r.check("exact-position grant: only the intended player at the intended position and block type is answered 'eligible'",
                intended && !samePlayerOtherPos && !otherPlayer && !otherBlockType, intended + "/" + samePlayerOtherPos + "/" + otherPlayer + "/" + otherBlockType);
        r.check("grant is single-use and gone after the scope closes; nothing else was ever answered",
                !second && !afterClose && !HarvestGrant.isOpen() && HarvestGrant.served() - served0 == 1, "served=" + (HarvestGrant.served() - served0));

        // end to end through vanilla destroyBlock: only the granted position counts, a same-type block elsewhere does not
        level.setBlockAndUpdate(pos, stone);
        level.setBlockAndUpdate(other, stone);
        int sA = HarvestGrant.served();
        boolean brokeOther;
        int servedOther, servedGranted;
        try (HarvestGrant g = HarvestGrant.open(p, level, pos, Blocks.STONE)) {
            brokeOther = p.gameMode.destroyBlock(other);                 // same player, same block type, different position
            servedOther = HarvestGrant.served() - sA;
            p.gameMode.destroyBlock(pos);                                // the exact break
            servedGranted = HarvestGrant.served() - sA;
        }
        r.check("through vanilla destroyBlock: a same-type block elsewhere is NOT covered; the granted break is",
                brokeOther && servedOther == 0 && servedGranted == 1, "other=" + servedOther + " granted=" + servedGranted);
        r.check("Player.hasCorrectToolForDrops itself is never altered, even inside an open scope",
                !HarvestGrant.isOpen() && !p.hasCorrectToolForDrops(stone), "");
        level.setBlockAndUpdate(pos, stone);
        try (HarvestGrant g = HarvestGrant.open(p, level, pos, Blocks.STONE)) {
            r.check("Player.hasCorrectToolForDrops is not consulted by the grant inside the scope", !p.hasCorrectToolForDrops(stone), "");
        }

        // exception safety
        try (HarvestGrant g = HarvestGrant.open(p, level, pos, Blocks.STONE)) {
            throw new IllegalStateException("simulated failure inside a break");
        } catch (IllegalStateException expected) {
            r.check("grant is cleaned up when the scoped operation throws", !HarvestGrant.isOpen(), "");
        }

        // nesting: inner close must not disturb the outer grant; each answers only its own player + position
        boolean innerOk, outerOk, crossed, outerAfterInner, cleanAfterBoth;
        try (HarvestGrant outer = HarvestGrant.open(p, level, pos, Blocks.STONE)) {
            try (HarvestGrant inner = HarvestGrant.open(stranger, level, other, Blocks.STONE)) {
                crossed = HarvestGrant.grants(p, other, stone) || HarvestGrant.grants(stranger, pos, stone);
                innerOk = HarvestGrant.grants(stranger, other, stone) && HarvestGrant.openCount() == 2;
            }
            outerAfterInner = HarvestGrant.openCount() == 1 && !HarvestGrant.grants(stranger, other, stone);
            outerOk = HarvestGrant.grants(p, pos, stone);
        }
        cleanAfterBoth = HarvestGrant.openCount() == 0 && !HarvestGrant.grants(p, pos, stone) && !HarvestGrant.grants(stranger, other, stone);
        r.check("nested/re-entrant grants keep their own player+position scope (no cross-answering; inner close leaves outer intact)",
                innerOk && outerOk && !crossed && outerAfterInner && cleanAfterBoth,
                innerOk + "/" + outerOk + "/" + crossed + "/" + outerAfterInner + "/" + cleanAfterBoth);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
    }

    private static void crackSync(VerificationReporter r, ServerLevel level, BlockPos pos, BlockDamageStorage storage) {
        Vec3 at = Vec3.atCenterOf(pos);
        // replaced block
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        BlockBreaking.applyImpact(level, impact(pos, 10f, 1, MiningSource.Kind.MOB));
        int before = storage.size();
        level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        var afterReplace = storage.cracksNear(at);
        r.check("immediate join sync prunes an entry whose block was replaced (nothing sent, entry removed)",
                afterReplace.isEmpty() && storage.size() == before - 1, "sent=" + afterReplace.size() + " size " + before + "->" + storage.size());
        // air
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        BlockBreaking.applyImpact(level, impact(pos, 10f, 1, MiningSource.Kind.MOB));
        before = storage.size();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        r.check("immediate join sync prunes an entry whose block is now air",
                storage.cracksNear(at).isEmpty() && storage.size() == before - 1, "");
        // recovered
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        BlockBreaking.applyImpact(level, impact(pos, 10f, 1, MiningSource.Kind.MOB));
        var entry = storage.get(pos, level.getBlockState(pos));
        entry.lastImpactTick = level.getGameTime() - MiningTuning.RECOVERY_DELAY_TICKS - 5000;   // long healed
        before = storage.size();
        r.check("immediate join sync prunes a fully recovered entry",
                storage.cracksNear(at).isEmpty() && storage.size() == before - 1 && storage.get(pos, level.getBlockState(pos)) == null, "");
        // valid still sent
        BlockBreaking.applyImpact(level, impact(pos, 10f, 1, MiningSource.Kind.MOB));
        var sent = storage.cracksNear(at);
        r.check("a valid damaged entry is still sent immediately", sent.size() == 1 && sent.get(0).getProgress() >= 0, "" + sent.size());
        var v = storage.get(pos, level.getBlockState(pos));
        if (v != null) storage.remove(v);
    }

    private static void presentation(VerificationReporter r, MinecraftServer server) {
        // Playtest-correction pass §11: this function is only ever called for a genuine Power
        // swing, so force=0 is the real WHITE zone (1), never BAND_DEFAULT (0) — see
        // presentationBand's own Javadoc and bodyStrain()'s dedicated band-boundary test.
        r.check("force bands: white (incl. force=0), green, orange, red zone / overload -> danger",
                MiningTuning.presentationBand(0f, false) == 1 && MiningTuning.presentationBand(0.2f, false) == 1
                        && MiningTuning.presentationBand(0.5f, false) == 2 && MiningTuning.presentationBand(0.7f, false) == 3
                        && MiningTuning.presentationBand(0.8f, false) == 4 && MiningTuning.presentationBand(0.1f, true) == 4, "");
        var buf = net.minecraft.network.RegistryFriendlyByteBuf.decorator(server.registryAccess()).apply(io.netty.buffer.Unpooled.buffer());
        var text = new zcylas.totality.networking.combat.CombatTextPayload(zcylas.totality.client.combat.CombatTextEntry.TextType.BLOCK_DAMAGE,
                null, 42f, null, 1, 2, 3, false, false, -1, -1, 3);
        zcylas.totality.networking.combat.CombatTextPayload.CODEC.encode(buf, text);
        var back = zcylas.totality.networking.combat.CombatTextPayload.CODEC.decode(buf);
        r.check("BLOCK_DAMAGE payload round-trips its presentation band (single semantic type, no per-band types)",
                back.style() == 3 && back.textType() == zcylas.totality.client.combat.CombatTextEntry.TextType.BLOCK_DAMAGE
                        && back.amount() == 42f && back.x() == 1 && back.z() == 3, "" + back);
        var legacy = new zcylas.totality.networking.combat.CombatTextPayload(zcylas.totality.client.combat.CombatTextEntry.TextType.DAMAGE,
                null, 7f, "x", 0, 0, 0, false, true, 5, 6);
        var buf2 = net.minecraft.network.RegistryFriendlyByteBuf.decorator(server.registryAccess()).apply(io.netty.buffer.Unpooled.buffer());
        zcylas.totality.networking.combat.CombatTextPayload.CODEC.encode(buf2, legacy);
        var back2 = zcylas.totality.networking.combat.CombatTextPayload.CODEC.decode(buf2);
        r.check("existing entity Combat Text is unchanged (default style 0, all fields intact)",
                back2.style() == 0 && back2.textType() == zcylas.totality.client.combat.CombatTextEntry.TextType.DAMAGE
                        && back2.amount() == 7f && back2.vulnerable() && back2.entityId() == 5 && back2.attackerEntityId() == 6
                        && "x".equals(back2.label()), "" + back2);
    }

    private static ItemStack pickaxeWithDamagePerBlock(int damagePerBlock) {
        ItemStack st = new ItemStack(Items.IRON_PICKAXE);
        var tool = st.get(net.minecraft.core.component.DataComponents.TOOL);
        st.set(net.minecraft.core.component.DataComponents.TOOL, new net.minecraft.world.item.component.Tool(
                tool.rules(), tool.defaultMiningSpeed(), damagePerBlock, tool.canDestroyBlocksInCreative()));
        return st;
    }

    private static void wearAndSwap(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos, BlockDamageStorage storage) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        setScore(p, STR, 10);
        // ---- terminal hit costs exactly 1 for damagePerBlock 0, 1 and 2 (0 would otherwise be free), and is restored.
        // V2: authored Iron Mining Damage (50) breaks a 100-Durability Stone in exactly 2 hits (1 partial + terminal). ----
        for (int dpb : new int[]{0, 1, 2}) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            ItemStack t = pickaxeWithDamagePerBlock(dpb);
            equip(p, t);
            PlayerMiningManager.strike(p, topHit(pos), false, 0f);
            int partial = p.getMainHandItem().getDamageValue();
            var last = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
            int total = p.getMainHandItem().getDamageValue();
            var comp = p.getMainHandItem().get(net.minecraft.core.component.DataComponents.TOOL);
            r.check("damagePerBlock=" + dpb + ": partial impact costs 1 and the terminal hit exactly 1 (total 2); component restored, no leak",
                    last.outcome() == MiningResult.Outcome.BROKEN && partial == 1 && total == 2 && comp != null && comp.damagePerBlock() == dpb,
                    "partial=" + partial + " total=" + total + " comp=" + (comp == null ? null : comp.damagePerBlock()));
            clearItems(level, pos);
        }
        // ---- exactly ONE base durability per successful impact, whatever Tool.damagePerBlock says (2 hits: V2 authored Iron damage) ----
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack heavy = pickaxeWithDamagePerBlock(2);
        equip(p, heavy);
        PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        int w1 = p.getMainHandItem().getDamageValue();
        var fin = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        int w2 = p.getMainHandItem().getDamageValue();
        var restored = p.getMainHandItem().get(net.minecraft.core.component.DataComponents.TOOL);
        r.check("damagePerBlock=2 tool: partial impact costs exactly 1, terminal hit brings the total to 2 (not 3), no double charge",
                fin.outcome() == MiningResult.Outcome.BROKEN && w1 == 1 && w2 == 2, "wear " + w1 + "," + w2);
        r.check("the tool component is restored after the terminal break (damagePerBlock still 2)",
                restored != null && restored.damagePerBlock() == 2, "" + restored);
        clearItems(level, pos);
        // power: V2 zone-based extra wear (profiled tools no longer use legacy Force Stress at all — §3), applied
        // independently of the Tool component's damagePerBlock. STR 20 (+5 modifier), RED zone (force 1.0).
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack heavy2 = pickaxeWithDamagePerBlock(2);
        equip(p, heavy2);
        setScore(p, STR, 20);
        int redExtra = MiningTuning.powerZoneExtraWear(MiningTuning.BAND_RED, 5);
        PlayerMiningManager.strike(p, topHit(pos), true, 1f);
        r.check("damagePerBlock=2 tool, V2 power RED-zone hit: 1 base + zone extra wear, independent of damagePerBlock",
                p.getMainHandItem().getDamageValue() == 1 + redExtra, "wear=" + p.getMainHandItem().getDamageValue() + " expected=" + (1 + redExtra));
        setScore(p, STR, 10);
        var e0 = storage.get(pos, level.getBlockState(pos));
        if (e0 != null) storage.remove(e0);

        sourceIdentity(r, level);
        bodyStrain(r, level, p, pos, storage);

        // ---- mid-swing tool swap: the source is snapshotted at swing start, the TARGET is not ----
        // Wooden Pickaxe (authored damage 20) deliberately, so the fresh 100-Durability Stone survives both
        // "same" and "worn" below as DAMAGED (an Iron/Netherite one-shot-class tool would break it too soon).
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack toolA = new ItemStack(Items.WOODEN_PICKAXE);
        equip(p, toolA);
        ItemStack snapshotA = p.getMainHandItem().copy();
        var same = PlayerMiningManager.contactNow(p, false, 0f, snapshotA);
        r.check("same tool from swing start to contact: the impact applies", same != null && same.outcome() == MiningResult.Outcome.DAMAGED, "" + same);

        p.getMainHandItem().setDamageValue(5);   // ordinary wear on the SAME tool during the swing
        var worn = PlayerMiningManager.contactNow(p, false, 0f, snapshotA);
        r.check("durability change on the same held tool does not look like a swap", worn != null && worn.outcome() == MiningResult.Outcome.DAMAGED, "" + worn);

        var ent = storage.get(pos, level.getBlockState(pos));
        float integrityBefore = ent == null ? -1 : ent.integrity;
        ItemStack toolB = new ItemStack(Items.NETHERITE_PICKAXE);
        equip(p, toolB);
        var swapped = PlayerMiningManager.contactNow(p, false, 0f, snapshotA);       // swing began with A, hand now holds B
        var ent2 = storage.get(pos, level.getBlockState(pos));
        r.check("tool swapped before contact: the impact is cancelled (no damage, no wear, no stress)",
                swapped == null && toolB.getDamageValue() == 0 && ent2 != null && ent2.integrity == integrityBefore, "" + swapped);
        var swappedPower = PlayerMiningManager.contactNow(p, true, 1f, snapshotA);
        r.check("a swapped POWER swing is cancelled as well (no force stress on the new tool)",
                swappedPower == null && toolB.getDamageValue() == 0, "");
        var handSwap = PlayerMiningManager.contactNow(p, false, 0f, ItemStack.EMPTY);   // swing began bare-handed, now a pickaxe
        r.check("swapping from bare hands to a tool also cancels", handSwap == null, "");

        // next swing with the new tool works with the NEW tool's properties (fresh Stone, V2 authored Netherite
        // damage 100 exactly crosses the 100-Durability breakpoint -> one-shot BROKEN, by design — §4)
        if (ent2 != null) storage.remove(ent2);
        var next = PlayerMiningManager.contactNow(p, false, 0f, toolB.copy());
        r.check("next swing with the newly held tool applies normally (netherite pickaxe: authored 100 damage one-shots a fresh 100-Durability Stone)",
                next != null && next.outcome() == MiningResult.Outcome.BROKEN && Math.abs(next.applied() - 100f) < 0.01f, "" + next);

        // target changes with the same tool are still allowed
        BlockPos second = pos.west(1);
        level.setBlockAndUpdate(second, Blocks.STONE.defaultBlockState());
        p.setPos(second.getX() + 0.5, second.getY() + 1, second.getZ() + 0.5);
        var retarget = PlayerMiningManager.contactNow(p, false, 0f, toolB.copy());
        r.check("same tool, target changed before contact: the impact lands on the NEW target (one-shot BROKEN there, original pos untouched)",
                retarget != null && retarget.outcome() == MiningResult.Outcome.BROKEN && level.getBlockState(second).isAir(), "" + retarget);
        p.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        var es = storage.get(second, level.getBlockState(second));
        if (es != null) storage.remove(es);
        level.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
        var e1 = storage.get(pos, level.getBlockState(pos));
        if (e1 != null) storage.remove(e1);
        equip(p, ItemStack.EMPTY);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // First-person animation timeline (pure math only; rendering itself cannot be verified headlessly)
    // ─────────────────────────────────────────────────────────────────────────────────────────

    private static boolean allFinite(zcylas.totality.client.mining.MiningHandAnimation a, int ticks) {
        for (int t = 0; t < ticks; t++) {
            for (float pt = 0f; pt <= 1.0f; pt += 0.25f) {
                var tr = a.sample(pt);
                if (!tr.isFinite()) return false;
                float pr = a.progress(pt);
                if (!(pr >= 0f && pr <= 1f)) return false;
            }
            a.tick();
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // Block Breaking V2 — authored Mining Damage/Speed, Impact, Efficiency/Haste/cap, Power zones (pure)
    // ─────────────────────────────────────────────────────────────────────────────────────────

    private static int hitsToBreak(float damage) {
        return (int) Math.ceil(100f / damage);
    }

    private static void authoredMiningStatsV2(VerificationReporter r, ServerLevel level) {
        var wood = MiningSourceProfile.resolve(new ItemStack(Items.WOODEN_PICKAXE)).orElseThrow();
        var stone = MiningSourceProfile.resolve(new ItemStack(Items.STONE_PICKAXE)).orElseThrow();
        var copper = MiningSourceProfile.resolve(new ItemStack(Items.COPPER_PICKAXE)).orElseThrow();
        var iron = MiningSourceProfile.resolve(new ItemStack(Items.IRON_PICKAXE)).orElseThrow();
        var diamond = MiningSourceProfile.resolve(new ItemStack(Items.DIAMOND_PICKAXE)).orElseThrow();
        var netherite = MiningSourceProfile.resolve(new ItemStack(Items.NETHERITE_PICKAXE)).orElseThrow();

        r.check("authored Mining Damage: Wood 20 / Stone 35 / Copper 40 / Iron 50 / Diamond 75 / Netherite 100",
                wood.miningDamage() == 20f && stone.miningDamage() == 35f && copper.miningDamage() == 40f
                        && iron.miningDamage() == 50f && diamond.miningDamage() == 75f && netherite.miningDamage() == 100f, "");
        r.check("authored Mining Speed: Wood 1.5 / Stone 2.0 / Copper 2.1 / Iron 2.25 / Diamond 2.4 / Netherite 2.5",
                wood.miningSpeed() == 1.5f && stone.miningSpeed() == 2.0f && copper.miningSpeed() == 2.1f
                        && iron.miningSpeed() == 2.25f && diamond.miningSpeed() == 2.4f && netherite.miningSpeed() == 2.5f, "");
        r.check("axes mirror the pickaxe values for the same material",
                MiningSourceProfile.resolve(new ItemStack(Items.IRON_AXE)).orElseThrow().equals(iron)
                        && MiningSourceProfile.resolve(new ItemStack(Items.NETHERITE_AXE)).orElseThrow().equals(netherite), "");
        // Shovel vertical-slice pass (§5-6): Shovels joined the authored table with the exact same
        // per-material Damage/Speed/Tier as their Pickaxe/Axe counterparts.
        r.check("shovels mirror the pickaxe values for the same material (Shovel vertical-slice pass)",
                MiningSourceProfile.resolve(new ItemStack(Items.IRON_SHOVEL)).orElseThrow().equals(iron)
                        && MiningSourceProfile.resolve(new ItemStack(Items.NETHERITE_SHOVEL)).orElseThrow().equals(netherite)
                        && MiningSourceProfile.resolve(new ItemStack(Items.WOODEN_SHOVEL)).orElseThrow().equals(wood)
                        && MiningSourceProfile.resolve(new ItemStack(Items.COPPER_SHOVEL)).orElseThrow().equals(copper), "");
        // Block Breaking V2 Pass 2: Gold's accepted row, and Hoes in every material row.
        var gold = MiningSourceProfile.resolve(new ItemStack(Items.GOLDEN_PICKAXE)).orElse(null);
        r.check("Pass 2: Gold = Mining Damage 25, Mining Speed 3.0/s, Mining Tier 1, identical for Pickaxe/Axe/Shovel/Hoe",
                gold != null && gold.equals(new MiningSourceProfile.Entry(25f, 3.0f, 1))
                        && gold.equals(MiningSourceProfile.resolve(new ItemStack(Items.GOLDEN_AXE)).orElse(null))
                        && gold.equals(MiningSourceProfile.resolve(new ItemStack(Items.GOLDEN_SHOVEL)).orElse(null))
                        && gold.equals(MiningSourceProfile.resolve(new ItemStack(Items.GOLDEN_HOE)).orElse(null)), "" + gold);
        r.check("Pass 2: Gold tools keep vanilla item durability 32",
                new ItemStack(Items.GOLDEN_PICKAXE).getMaxDamage() == 32 && new ItemStack(Items.GOLDEN_HOE).getMaxDamage() == 32, "");
        r.check("Pass 2: hoes mirror their material's Pickaxe values (Wood/Stone/Copper/Iron/Diamond/Netherite)",
                MiningSourceProfile.resolve(new ItemStack(Items.WOODEN_HOE)).orElseThrow().equals(wood)
                        && MiningSourceProfile.resolve(new ItemStack(Items.STONE_HOE)).orElseThrow().equals(stone)
                        && MiningSourceProfile.resolve(new ItemStack(Items.COPPER_HOE)).orElseThrow().equals(copper)
                        && MiningSourceProfile.resolve(new ItemStack(Items.IRON_HOE)).orElseThrow().equals(iron)
                        && MiningSourceProfile.resolve(new ItemStack(Items.DIAMOND_HOE)).orElseThrow().equals(diamond)
                        && MiningSourceProfile.resolve(new ItemStack(Items.NETHERITE_HOE)).orElseThrow().equals(netherite), "");
        r.check("Pass 2: tier progression — Diamond and Netherite both 4, nothing authored above 4 (5 reserved for Totality Core)",
                diamond.tier() == 4 && netherite.tier() == 4 && MiningSourceProfile.participatingItems().stream()
                        .allMatch(item -> MiningSourceProfile.resolve(new ItemStack(item)).orElseThrow().tier() <= 4), "");

        r.check("100-Durability hit counts: Wood 5 / Stone 3 / Copper 3 / Iron 2 / Diamond 2 / Netherite 1",
                hitsToBreak(wood.miningDamage()) == 5 && hitsToBreak(stone.miningDamage()) == 3
                        && hitsToBreak(copper.miningDamage()) == 3 && hitsToBreak(iron.miningDamage()) == 2
                        && hitsToBreak(diamond.miningDamage()) == 2 && hitsToBreak(netherite.miningDamage()) == 1, "");
        r.check("Stone/Cobblestone/Diorite/Andesite are all registered at 100 Block Durability",
                BlockDurability.resolveStatic(Blocks.STONE.defaultBlockState()).max() == 100f
                        && BlockDurability.resolveStatic(Blocks.COBBLESTONE.defaultBlockState()).max() == 100f
                        && BlockDurability.resolveStatic(Blocks.DIORITE.defaultBlockState()).max() == 100f
                        && BlockDurability.resolveStatic(Blocks.ANDESITE.defaultBlockState()).max() == 100f, "");
        // Shovel vertical-slice pass, §4: Dirt + Grass Block = 100 Block Durability, Required Tier
        // still 0 (the pre-existing hardness-fallback value — neither requires the correct tool for
        // drops in vanilla, so bare hands could already work them; that legacy value is untouched).
        r.check("Dirt/Grass Block are registered at 100 Block Durability, Required Tier 0 (unchanged legacy value)",
                BlockDurability.resolveStatic(Blocks.DIRT.defaultBlockState()).max() == 100f
                        && BlockDurability.resolveStatic(Blocks.GRASS_BLOCK.defaultBlockState()).max() == 100f
                        && BlockDurability.resolveStatic(Blocks.DIRT.defaultBlockState()).requiredTier() == 0
                        && BlockDurability.resolveStatic(Blocks.GRASS_BLOCK.defaultBlockState()).requiredTier() == 0, "");
        r.check("Shovel 100-Durability hit counts (Dirt/Grass): Wood 5 / Stone 3 / Copper 3 / Iron 2 / Diamond 2 / Netherite 1",
                hitsToBreak(MiningSourceProfile.resolve(new ItemStack(Items.WOODEN_SHOVEL)).orElseThrow().miningDamage()) == 5
                        && hitsToBreak(MiningSourceProfile.resolve(new ItemStack(Items.STONE_SHOVEL)).orElseThrow().miningDamage()) == 3
                        && hitsToBreak(MiningSourceProfile.resolve(new ItemStack(Items.COPPER_SHOVEL)).orElseThrow().miningDamage()) == 3
                        && hitsToBreak(MiningSourceProfile.resolve(new ItemStack(Items.IRON_SHOVEL)).orElseThrow().miningDamage()) == 2
                        && hitsToBreak(MiningSourceProfile.resolve(new ItemStack(Items.DIAMOND_SHOVEL)).orElseThrow().miningDamage()) == 2
                        && hitsToBreak(MiningSourceProfile.resolve(new ItemStack(Items.NETHERITE_SHOVEL)).orElseThrow().miningDamage()) == 1, "");
        ItemStack diamondShovelImpact = new ItemStack(Items.DIAMOND_SHOVEL);
        diamondShovelImpact.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(zcylas.totality.init.ModEnchantments.IMPACT), 5);
        ResolvedMiningSource diamondShovelSource = ResolvedMiningSource.of(diamondShovelImpact, Blocks.DIRT.defaultBlockState());
        float diamondShovelImpactDamage = MiningDamageCalculator.compute(diamondShovelSource,
                EnchantLevel.of(diamondShovelImpact, zcylas.totality.init.ModEnchantments.IMPACT)).value();
        r.check("Diamond Shovel + Impact V: 75 + 25 = 100 Mining Damage -> one-hits a 100-Durability Dirt block",
                diamondShovelImpactDamage == 100f && hitsToBreak(diamondShovelImpactDamage) == 1, "" + diamondShovelImpactDamage);
        // Pass 3 reconciliation: Nether stems are their own 100-HP Nether-wood family, still excluded from the Overworld log family.
        r.check("ordinary logs/wood (+stripped) are registered at 100 Block Durability; Crimson Stem is excluded from that family (own Nether wood)",
                BlockDurability.resolveStatic(Blocks.OAK_LOG.defaultBlockState()).max() == 100f
                        && BlockDurability.resolveStatic(Blocks.STRIPPED_OAK_WOOD.defaultBlockState()).max() == 100f
                        && !BlockDurabilityDefinitions.OVERWORLD_LOG.equals(BlockProfiles.resolveStatic(Blocks.CRIMSON_STEM.defaultBlockState()).material()), "");
        // Granite was deliberately NOT registered (§4) — it still resolves through the plain hardness
        // fallback (its fallback value happening to equal 100, same hardness as Stone, is coincidence).
        BlockState granite = Blocks.GRANITE.defaultBlockState();
        r.check("Granite is untouched: still resolves via the hardness fallback formula, not an explicit registration",
                BlockDurability.resolveStatic(granite).max() == granite.getBlock().defaultDestroyTime() * MiningTuning.DURABILITY_PER_HARDNESS, "");

        // ---- Impact ----
        r.check("Impact bonus table: I=5 II=10 III=15 IV=20 V=25",
                MiningDamageCalculator.impactBonus(1) == 5f && MiningDamageCalculator.impactBonus(2) == 10f
                        && MiningDamageCalculator.impactBonus(3) == 15f && MiningDamageCalculator.impactBonus(4) == 20f
                        && MiningDamageCalculator.impactBonus(5) == 25f, "");
        ResolvedMiningSource diamondSource = new ResolvedMiningSource(diamond.miningDamage(), diamond.miningSpeed(), 4, 9f, true);
        ResolvedMiningSource netheriteSource = new ResolvedMiningSource(netherite.miningDamage(), netherite.miningSpeed(), 5, 16f, true);
        StatBreakdown diamondImpactV = MiningDamageCalculator.compute(diamondSource, 5);
        r.check("Diamond + Impact V: 75 + 25 = 100 Mining Damage -> one-hits a 100-Durability block",
                diamondImpactV.value() == 100f && hitsToBreak(diamondImpactV.value()) == 1, "" + diamondImpactV.value());
        var enchants = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var impactEnchant = enchants.getOrThrow(zcylas.totality.init.ModEnchantments.IMPACT).value();
        r.check("Impact has no attribute effect component at all, so it cannot affect combat/attack damage",
                !impactEnchant.effects().has(net.minecraft.world.item.enchantment.EnchantmentEffectComponents.ATTRIBUTES), "");

        // ---- Efficiency / Haste / speed cap ----
        r.check("Efficiency bonus table: I=0.5 II=1.0 III=2.0 IV=3.5 V=5.0",
                MiningSpeedCalculator.efficiencyBonus(1) == 0.5f && MiningSpeedCalculator.efficiencyBonus(2) == 1.0f
                        && MiningSpeedCalculator.efficiencyBonus(3) == 2.0f && MiningSpeedCalculator.efficiencyBonus(4) == 3.5f
                        && MiningSpeedCalculator.efficiencyBonus(5) == 5.0f, "");
        StatBreakdown netEffV = MiningSpeedCalculator.compute(netheriteSource, 5, null, 1f);
        r.check("Netherite + Efficiency V: 2.5 + 5 = 7.5/s", netEffV.value() == 7.5f, "" + netEffV.value());
        StatBreakdown netEffHasteII = MiningSpeedCalculator.compute(netheriteSource, 5, 2, 1f);
        r.check("Netherite + Efficiency V + Haste II: 7.5 x 1.4 = 10.5 raw, capped at 10.0/s",
                Math.abs(netEffHasteII.value() - 10.0f) < 1e-4f, "" + netEffHasteII.value());
        StatBreakdown diaEffHasteII = MiningSpeedCalculator.compute(diamondSource, 5, 2, 1f);
        r.check("Diamond + Efficiency V + Haste II: (2.4+5.0) x 1.4 = 10.36 raw, capped at 10.0/s",
                Math.abs(diaEffHasteII.value() - 10.0f) < 1e-4f, "" + diaEffHasteII.value());
        r.check("Efficiency does NOT increase Mining Damage (the damage calculator takes no Efficiency input at all)",
                MiningDamageCalculator.compute(diamondSource, 0).value() == 75f, "");
        r.check("Impact and Efficiency coexist (both read independently off the same stack)",
                MiningDamageCalculator.compute(diamondSource, 5).value() == 100f
                        && MiningSpeedCalculator.compute(diamondSource, 5, null, 1f).value() == 7.4f, "");

        // ---- Power Mining STR zones (§3 of the balance pass; WHITE separated from GREEN in the
        // playtest-correction pass §11): STR 20 -> +5 modifier, Diamond base 75 ----
        int strMod = 5;
        r.check("Power Mining STR 20, Diamond base 75 — WHITE: 75+0=75, no extra durability (released too weak)",
                75f + MiningTuning.powerZoneDamageBonus(MiningTuning.BAND_WHITE, strMod) == 75f
                        && MiningTuning.powerZoneExtraWear(MiningTuning.BAND_WHITE, strMod) == 0, "");
        r.check("Power Mining STR 20, Diamond base 75 — GREEN: 75+5=80, no extra durability",
                75f + MiningTuning.powerZoneDamageBonus(MiningTuning.BAND_GREEN, strMod) == 80f
                        && MiningTuning.powerZoneExtraWear(MiningTuning.BAND_GREEN, strMod) == 0, "");
        r.check("Power Mining STR 20, Diamond base 75 — ORANGE: 75+10=85, extra durability 5",
                75f + MiningTuning.powerZoneDamageBonus(MiningTuning.BAND_ORANGE, strMod) == 85f
                        && MiningTuning.powerZoneExtraWear(MiningTuning.BAND_ORANGE, strMod) == 5, "");
        r.check("Power Mining STR 20, Diamond base 75 — RED: 75+25=100, extra durability 25",
                75f + MiningTuning.powerZoneDamageBonus(MiningTuning.BAND_RED, strMod) == 100f
                        && MiningTuning.powerZoneExtraWear(MiningTuning.BAND_RED, strMod) == 25, "");
        r.check("negative STR modifier never creates negative extra wear (clamped to 0)",
                MiningTuning.powerZoneExtraWear(MiningTuning.BAND_RED, -3) == 0
                        && MiningTuning.powerZoneExtraWear(MiningTuning.BAND_ORANGE, -3) == 0, "");
        r.check("SPEED_CAP is exactly the 2-tick ordinary-swing floor (20 ticks/s / 10 = 2)", MiningTuning.SPEED_CAP == 10f, "");

        // ---- §4 fix pass: Impact/authored-profile membership is EXPLICIT item identity, never a
        // repair-material probe — a foreign/modded tool must never silently inherit a vanilla profile.
        // Shovel vertical-slice pass (§5-8): Shovels joined Pickaxe/Axe in the same authored table,
        // so participatingItems() is now 18 (6 materials x 3 tool categories), not 12.
        // (No vanilla item other than the authored 18 shares a repair material AND is a pickaxe/axe/
        // shovel, so this is verified as a set-equality invariant: exactly the participating items
        // resolve, and a representative set of non-participating tool-ish items — including Gold,
        // which DOES share a repair-material family concept with nothing here since resolution no
        // longer probes repair material at all — do not.)
        java.util.Set<net.minecraft.world.item.Item> expected = new java.util.HashSet<>(MiningSourceProfile.participatingItems());
        r.check("participatingItems() is exactly the 28 authored Wood/Stone/Copper/Iron/Diamond/Netherite/Gold x Pickaxe/Axe/Shovel/Hoe items",
                expected.size() == 28, "" + expected.size());
        boolean allParticipantsResolve = expected.stream().allMatch(item -> MiningSourceProfile.resolve(new ItemStack(item)).isPresent());
        r.check("every participating item resolves to a profile", allParticipantsResolve, "");
        ItemStack[] nonParticipants = {
                new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.SHEARS), new ItemStack(Items.STICK)
        };
        boolean noneResolve = java.util.Arrays.stream(nonParticipants).noneMatch(t -> MiningSourceProfile.resolve(t).isPresent());
        r.check("non-participating items (swords, shears, non-tools) never resolve a profile",
                noneResolve, "");
        r.check("Force Tolerance keeps using its own repair-material resolver, unaffected by this correction",
                MiningTuning.forceTolerance(new ItemStack(Items.DIAMOND_PICKAXE)) == MiningTuning.TOLERANCE_DIAMOND, "");
    }

    /**
     * §5 fix pass: Power=true, force=0 (a legitimate Power release — the oscillating meter can read
     * exactly 0) must still be treated as a real Power swing, not silently downgraded to an
     * ordinary hit merely because {@code force > 0} is false. Playtest-correction pass §11-12:
     * force=0 is now explicitly the WHITE zone ("released too weak to earn a bonus") — normal
     * Mining Damage, no STR bonus, no extra wear, but still a genuine Power impact (not a miss).
     */
    private static void powerForceZeroEdge(VerificationReporter r, ServerPlayer p, BlockState stoneState) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        equip(p, new ItemStack(Items.DIAMOND_PICKAXE));
        setScore(p, STR, 20);   // STR modifier = floor((20-10)/2) = +5
        var powerZero = PlayerMiningPower.compute(p, stoneState, true, 0f);
        var ordinaryZero = PlayerMiningPower.compute(p, stoneState, false, 0f);
        r.check("Power=true, force=0, STR+5, Diamond 75: a real WHITE-zone Power swing -> 75+0=75 damage, no bonus, 0 extra wear",
                powerZero.damage() == 75f && powerZero.profiled() && powerZero.band() == MiningTuning.BAND_WHITE
                        && MiningTuning.powerZoneExtraWear(powerZero.band(), powerZero.strModifier()) == 0,
                "" + powerZero);
        r.check("an ordinary (non-Power) hit at force=0 stays the plain authored 75 (no STR at all)",
                ordinaryZero.damage() == 75f, "" + ordinaryZero);
        setScore(p, STR, 10);
        equip(p, ItemStack.EMPTY);
    }

    /**
     * §1 fix pass: drives the REAL scheduling state machine tick-by-tick (via the
     * {@code PlayerMiningManager} debugTick/contactFrameObserver test seams) and measures actual
     * contact-to-contact tick spacing for a sustained hold. The pure formula checks above cannot
     * see an extra dead IDLE tick between swings — only actually running the state machine can.
     */
    private static void profiledCadenceStateMachine(VerificationReporter r, MinecraftServer server, ServerLevel level, ServerPlayer p, BlockPos pos) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        setScore(p, STR, 10);
        p.setXRot(90f);
        p.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        // wearAndSwap (the previous sub-test) leaves pos as AIR after its final one-shot-break
        // assertion — without a real target block every tryStartSwing() call returns early and no
        // contact ever fires, which would silently read as "0 contacts" rather than a real failure.
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());

        r.check("2.0/s sustained contact spacing (Stone Pickaxe, base speed): every 10 ticks",
                measureSustained(server, level, p, pos, new ItemStack(Items.STONE_PICKAXE), 6, 10), "");
        r.check("2.5/s sustained contact spacing (Netherite Pickaxe, base speed): every 8 ticks",
                measureSustained(server, level, p, pos, new ItemStack(Items.NETHERITE_PICKAXE), 6, 8), "");

        ItemStack capped = new ItemStack(Items.NETHERITE_PICKAXE);
        var enchants = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        capped.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HASTE, 4000, 1));
        r.check("10.0/s cap sustained contact spacing (Netherite + Efficiency V + Haste II): every 2 ticks",
                measureSustained(server, level, p, pos, capped, 8, 2), "");
        resetSpeedModifiers(p);

        List<Integer> gaps24 = measureGaps(server, level, p, pos, new ItemStack(Items.DIAMOND_PICKAXE), 30);
        double avg24 = gaps24.stream().mapToInt(Integer::intValue).average().orElse(-1);
        double ideal24 = 20.0 / 2.4;
        r.check(String.format("2.4/s (Diamond Pickaxe) long-run average contact interval = 20/2.4 = %.4f ticks", ideal24),
                gaps24.size() >= 29 && Math.abs(avg24 - ideal24) < 0.05 && gaps24.stream().allMatch(g -> g == 8 || g == 9),
                "avg=" + avg24 + " gaps=" + gaps24);

        List<Integer> gaps225 = measureGaps(server, level, p, pos, new ItemStack(Items.IRON_PICKAXE), 30);
        double avg225 = gaps225.stream().mapToInt(Integer::intValue).average().orElse(-1);
        double ideal225 = 20.0 / 2.25;
        r.check(String.format("2.25/s (Iron Pickaxe) long-run average contact interval = 20/2.25 = %.4f ticks", ideal225),
                gaps225.size() >= 29 && Math.abs(avg225 - ideal225) < 0.05 && gaps225.stream().allMatch(g -> g == 8 || g == 9),
                "avg=" + avg225 + " gaps=" + gaps225);

        staleTargetCadence(r, level, p, pos);

        powerForceZeroEdge(r, p, level.getBlockState(pos));

        PlayerMiningManager.forget(p);
        equip(p, ItemStack.EMPTY);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    /**
     * Block Breaking V2 Pass 1: the stale-target cadence correction through the REAL tick-driven state machine.
     * The swing is scheduled on one block; during its wind-up that block is replaced, so the contact-frame
     * re-raycast strikes another. The swing must keep its wind-up (contact moment) and take the recovery of the
     * block actually struck; a miss keeps the scheduled cycle. Expected values come from the same live speed
     * resolution the manager uses, so only the correction itself is under test.
     */
    private static void staleTargetCadence(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        equip(p, tool);
        BlockState stone = Blocks.STONE.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
        int baseline = MiningTuning.windUpTicks(tool.getSwingAnimation().duration(), false, 1f);
        float stoneIdeal = MiningCadence.idealTicks(PlayerMiningPower.effectiveMiningSpeed(p, tool, stone, ResolvedMiningSource.of(tool, stone)));
        float grassIdeal = MiningCadence.idealTicks(PlayerMiningPower.effectiveMiningSpeed(p, tool, grass, ResolvedMiningSource.of(tool, grass)));

        for (boolean slowToFast : new boolean[]{true, false}) {
            BlockState start = slowToFast ? grass : stone, actual = slowToFast ? stone : grass;
            float startIdeal = slowToFast ? grassIdeal : stoneIdeal, actualIdeal = slowToFast ? stoneIdeal : grassIdeal;
            MiningCadence.Cycle scheduled = MiningCadence.profiled(0f, startIdeal, baseline);
            MiningCadence.Cycle corrected = MiningCadence.profiled(0f, actualIdeal, baseline);
            int expected = MiningCadence.correctedRecovery(scheduled.windUpTicks(), corrected.cycleTicks())
                    + MiningCadence.profiled(corrected.carry(), actualIdeal, baseline).windUpTicks();
            int stale = scheduled.recoveryTicks() + MiningCadence.profiled(scheduled.carry(), actualIdeal, baseline).windUpTicks();
            int measured = measureRetarget(level, p, pos, tool, start, actual, actual);
            r.check((slowToFast ? "stale-target cadence: slow -> fast (scheduled on Grass, struck Stone)"
                            : "stale-target cadence: fast -> slow (scheduled on Stone, struck Grass)")
                            + ": recovery follows the ACTUAL target (contact gap " + expected + ", not the stale " + stale + ")",
                    measured == expected && expected != stale, "measured=" + measured + " expected=" + expected + " stale=" + stale);
        }

        MiningCadence.Cycle grassSwing = MiningCadence.profiled(0f, grassIdeal, baseline);
        int missExpected = grassSwing.recoveryTicks() + MiningCadence.profiled(grassSwing.carry(), stoneIdeal, baseline).windUpTicks();
        int missMeasured = measureRetarget(level, p, pos, tool, grass, Blocks.AIR.defaultBlockState(), stone);
        r.check("stale-target cadence: a miss at contact keeps the scheduled cycle", missMeasured == missExpected,
                "measured=" + missMeasured + " expected=" + missExpected);
        level.setBlockAndUpdate(pos, stone);
    }

    /**
     * Contact-to-contact gap of the first two swings of a hold that started on {@code start}, where the block is
     * replaced by {@code atContact} right after the first swing began, and set to {@code afterContact} at every
     * contact (so the second swing always has a target). -1 when two contacts were not observed.
     */
    private static int measureRetarget(ServerLevel level, ServerPlayer p, BlockPos pos, ItemStack tool,
                                       BlockState start, BlockState atContact, BlockState afterContact) {
        PlayerMiningManager.forget(p);
        equip(p, tool);
        level.setBlockAndUpdate(pos, start);
        List<Integer> contacts = new java.util.ArrayList<>();
        int[] tick = {0};
        PlayerMiningManager.contactFrameObserver = () -> {
            contacts.add(tick[0]);
            level.setBlockAndUpdate(pos, afterContact);
        };
        try {
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(
                    zcylas.totality.networking.mining.MiningIntentPayload.Action.HOLD_START));
            for (int i = 0; i < 100 && contacts.size() < 2; i++) {
                tick[0]++;
                PlayerMiningManager.debugTickPlayer(p);
                if (tick[0] == 1) level.setBlockAndUpdate(pos, atContact);   // the first swing has begun: retarget its wind-up
            }
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(
                    zcylas.totality.networking.mining.MiningIntentPayload.Action.HOLD_STOP));
        } finally {
            PlayerMiningManager.contactFrameObserver = null;
            PlayerMiningManager.forget(p);
        }
        return contacts.size() == 2 ? contacts.get(1) - contacts.get(0) : -1;
    }

    /**
     * Block Breaking V2 Pass 2 — conventional tool completion through the real pipeline: Gold's accepted profile,
     * Hoes as full conventional sources (effectiveness, Impact, Power, wear, tilling untouched), swords/shears as
     * specialized sources whose own blocks are no longer blocked by a Tier-0 probe, Force Tolerance preserved as a
     * field, and the four Power bands unchanged.
     */
    private static void conventionalToolCompletion(VerificationReporter r, MinecraftServer server, ServerLevel level, ServerPlayer p, BlockPos pos) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        var DEX = zcylas.totality.api.rpg.stats.AbilityScore.DEX;
        setScore(p, STR, 10);
        setScore(p, DEX, 10);
        resetSpeedModifiers(p);
        p.setXRot(90f);
        p.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockState hay = Blocks.HAY_BLOCK.defaultBlockState(), cobweb = Blocks.COBWEB.defaultBlockState();
        var enchants = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var impact = enchants.getOrThrow(zcylas.totality.init.ModEnchantments.IMPACT);

        // ---- Gold ----
        equip(p, new ItemStack(Items.GOLDEN_PICKAXE));
        var goldStone = PlayerMiningPower.compute(p, stone, false, 0f);
        setScore(p, STR, 16);
        var goldStoneStr16 = PlayerMiningPower.compute(p, stone, false, 0f);
        setScore(p, STR, 10);
        r.check("Gold Pickaxe vs Stone: profiled 25 Mining Damage, Tier 1, and NO automatic STR on ordinary strikes (STR 10 and 16 both 25)",
                goldStone.profiled() && goldStone.damage() == 25f && goldStone.tier() == 1 && goldStoneStr16.damage() == 25f,
                goldStone + " / " + goldStoneStr16);
        r.check("Gold Pickaxe vs Dirt: normal wrong-tool effectiveness (25 x 0.10 = 2.5); Gold Shovel vs Dirt: 25",
                PlayerMiningPower.compute(p, dirt, false, 0f).damage() == 2.5f
                        && withTool(p, Items.GOLDEN_SHOVEL, () -> PlayerMiningPower.compute(p, dirt, false, 0f).damage()) == 25f, "");
        ItemStack goldImpact = new ItemStack(Items.GOLDEN_PICKAXE);
        goldImpact.enchant(impact, 5);
        equip(p, goldImpact);
        r.check("Gold supports Impact under the standard rules (Impact-enchantable; 25 + Impact V 25 = 50)",
                impact.value().isSupportedItem(new ItemStack(Items.GOLDEN_PICKAXE)) && PlayerMiningPower.compute(p, stone, false, 0f).damage() == 50f, "");
        equip(p, new ItemStack(Items.GOLDEN_PICKAXE));
        var goldPower = PlayerMiningPower.compute(p, stone, true, 0.9f);
        r.check("Gold Power Mining uses the profiled RED zone (25 + 5 x STR mod 0 = 25), not the legacy multiplier",
                goldPower.profiled() && goldPower.band() == MiningTuning.BAND_RED && goldPower.damage() == 25f, "" + goldPower);
        r.check("Gold keeps its Force Tolerance field (preserved, not displayed)",
                ResolvedMiningSource.of(new ItemStack(Items.GOLDEN_PICKAXE), stone).forceTolerance() == MiningTuning.TOLERANCE_GOLD, "");
        level.setBlockAndUpdate(pos, stone);
        List<Integer> goldGaps = measureGaps(server, level, p, pos, new ItemStack(Items.GOLDEN_PICKAXE), 31);
        double goldAvg = goldGaps.stream().mapToInt(Integer::intValue).average().orElse(-1);
        r.check(String.format("Gold 3.0/s through the profiled cadence pipeline: long-run contact interval 20/3 = %.4f ticks", 20.0 / 3.0),
                goldGaps.size() >= 30 && Math.abs(goldAvg - 20.0 / 3.0) < 0.05 && goldGaps.stream().allMatch(g -> g == 6 || g == 7),
                "avg=" + goldAvg + " gaps=" + goldGaps);

        // ---- Hoes ----
        equip(p, new ItemStack(Items.IRON_HOE));
        var hoeHay = PlayerMiningPower.compute(p, hay, false, 0f);
        var hoeStone = PlayerMiningPower.compute(p, stone, false, 0f);
        r.check("Iron Hoe: profiled Iron baseline — 50 on Hay (mineable/hoe, effective), 5 on Stone (wrong tool), Tier 3",
                hoeHay.profiled() && hoeHay.damage() == 50f && hoeStone.damage() == 5f && hoeHay.tier() == 3
                        && TargetEffectiveness.resolve(new ItemStack(Items.IRON_HOE), hay) == TargetEffectiveness.EFFECTIVE, hoeHay + " / " + hoeStone);
        ItemStack effHoe = new ItemStack(Items.IRON_HOE);
        effHoe.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
        float hoeSpeed = PlayerMiningPower.effectiveMiningSpeed(p, new ItemStack(Items.IRON_HOE), hay, ResolvedMiningSource.of(new ItemStack(Items.IRON_HOE), hay));
        float effHoeSpeed = PlayerMiningPower.effectiveMiningSpeed(p, effHoe, hay, ResolvedMiningSource.of(effHoe, hay));
        r.check("Iron Hoe: Impact-enchantable and Efficiency V speeds its profiled cadence (2.25 -> 7.25/s)",
                impact.value().isSupportedItem(new ItemStack(Items.IRON_HOE)) && hoeSpeed == 2.25f && effHoeSpeed == 7.25f, hoeSpeed + " -> " + effHoeSpeed);
        var hoePower = PlayerMiningPower.compute(p, hay, true, 0.5f);
        r.check("Iron Hoe: Power Mining uses the profiled zones (GREEN band)", hoePower.profiled() && hoePower.band() == MiningTuning.BAND_GREEN, "" + hoePower);
        BlockState netherWart = Blocks.NETHER_WART_BLOCK.defaultBlockState();
        level.setBlockAndUpdate(pos, netherWart);
        ItemStack woodHoe = new ItemStack(Items.WOODEN_HOE);
        equip(p, woodHoe);
        var hoeStrike = PlayerMiningManager.strike(p, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), false, 0f);
        r.check("Wooden Hoe strike on Nether Wart Block (mineable/hoe): DAMAGED for 20 and exactly 1 wear",
                hoeStrike.outcome() == MiningResult.Outcome.DAMAGED && hoeStrike.applied() == 20f && p.getMainHandItem().getDamageValue() == 1,
                hoeStrike + " wear=" + p.getMainHandItem().getDamageValue());
        BlockDamageStorage.Entry wartEntry = BlockDamageStorage.get(level).get(pos, netherWart);
        if (wartEntry != null) BlockDamageStorage.get(level).remove(wartEntry);
        level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        equip(p, new ItemStack(Items.IRON_HOE));
        var tilled = p.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false)));
        r.check("Hoe tilling is untouched: using an Iron Hoe on Dirt still makes Farmland",
                level.getBlockState(pos).is(Blocks.FARMLAND), tilled + " -> " + level.getBlockState(pos));

        // ---- Swords and Shears: specialized sources ----
        int cobwebTier = BlockProfiles.resolveStatic(cobweb).requiredTier();
        equip(p, new ItemStack(Items.IRON_SWORD));
        var swordWeb = PlayerMiningPower.compute(p, cobweb, false, 0f);
        var swordStone = PlayerMiningPower.compute(p, stone, false, 0f);
        r.check("Iron Sword on Cobweb (its own correct block): Tier gate no longer blocks it; stays a legacy, non-profiled source",
                !swordWeb.profiled() && swordWeb.tier() >= cobwebTier && cobwebTier > 0 && swordWeb.damage() == 90f,
                swordWeb + " required=" + cobwebTier);
        r.check("Iron Sword on Stone: still Tier 0 below Stone's requirement (no conventional mining granted)",
                swordStone.tier() == 0 && swordStone.tier() < BlockProfiles.resolveStatic(stone).requiredTier(), "" + swordStone);
        r.check("Swords get no Impact and no profiled Power Mining",
                !impact.value().isSupportedItem(new ItemStack(Items.IRON_SWORD)) && !PlayerMiningPower.compute(p, cobweb, true, 0.9f).profiled(), "");
        level.setBlockAndUpdate(pos, cobweb);
        var swordStrike = PlayerMiningManager.strike(p, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), false, 0f);
        // Pass 3 (confirmed): Cobweb is SPECIAL and vanilla-owned, so Totality no longer strikes it; vanilla Sword/Shears
        // breaking speed and drops are authoritative (drops are exercised in a real world by BlockBreakingPass3ClientGameTest).
        r.check("Pass 3: Cobweb is SPECIAL/vanilla-owned — a Totality strike is INVALID and no Sword/Shears/hand owns it",
                swordStrike.outcome() == MiningResult.Outcome.INVALID
                        && !MiningOwnership.owns(net.minecraft.world.level.GameType.SURVIVAL, new ItemStack(Items.IRON_SWORD), level, pos, cobweb)
                        && !MiningOwnership.owns(net.minecraft.world.level.GameType.SURVIVAL, new ItemStack(Items.SHEARS), level, pos, cobweb), "" + swordStrike);
        BlockDamageStorage.Entry webEntry = BlockDamageStorage.get(level).get(pos, cobweb);
        if (webEntry != null) BlockDamageStorage.get(level).remove(webEntry);
        equip(p, new ItemStack(Items.SHEARS));
        var shearsWeb = PlayerMiningPower.compute(p, cobweb, false, 0f);
        r.check("Shears on Cobweb: Tier gate lifted by its own vanilla rule; vanilla still treats Shears/Sword as correct for Cobweb drops",
                !shearsWeb.profiled() && shearsWeb.tier() >= cobwebTier && p.hasCorrectToolForDrops(cobweb)
                        && withTool(p, Items.IRON_SWORD, () -> p.hasCorrectToolForDrops(cobweb) ? 1f : 0f) == 1f, "" + shearsWeb);

        // ---- four Power bands unchanged ----
        r.check("the four Power Mining bands are unchanged (0.2 WHITE, 0.5 GREEN, 0.7 ORANGE, 0.9 RED)",
                MiningTuning.presentationBand(0.2f, false) == MiningTuning.BAND_WHITE && MiningTuning.presentationBand(0.5f, false) == MiningTuning.BAND_GREEN
                        && MiningTuning.presentationBand(0.7f, false) == MiningTuning.BAND_ORANGE && MiningTuning.presentationBand(0.9f, false) == MiningTuning.BAND_RED, "");

        equip(p, ItemStack.EMPTY);
        PlayerMiningManager.forget(p);
        level.setBlockAndUpdate(pos, stone);
    }

    private static float withTool(ServerPlayer p, net.minecraft.world.item.Item item, java.util.function.Supplier<Float> body) {
        ItemStack before = p.getMainHandItem();
        equip(p, new ItemStack(item));
        try {
            return body.get();
        } finally {
            equip(p, before);
        }
    }

    private static boolean measureSustained(MinecraftServer server, ServerLevel level, ServerPlayer p, BlockPos pos,
                                            ItemStack tool, int targetContacts, int expectedGap) {
        List<Integer> gaps = measureGaps(server, level, p, pos, tool, targetContacts);
        return gaps.size() >= targetContacts - 1 && gaps.stream().allMatch(g -> g == expectedGap);
    }

    /**
     * Drives a sustained hold for {@code targetContacts} real contacts through the actual
     * {@code PlayerMiningManager} state machine (HOLD_START -> repeated {@code debugTick} calls ->
     * HOLD_STOP), resetting the target block back to fresh Stone after every contact so Block
     * Durability never gates how many contacts can be observed — the scheduling/cadence math never
     * depends on a target block's state for a profiled tool, so this has no effect on timing.
     * Returns the tick gaps between consecutive contacts.
     */
    private static List<Integer> measureGaps(MinecraftServer server, ServerLevel level, ServerPlayer p, BlockPos pos,
                                              ItemStack tool, int targetContacts) {
        PlayerMiningManager.forget(p);
        equip(p, tool);
        List<Integer> contactTicks = new java.util.ArrayList<>();
        int[] tickCounter = {0};
        PlayerMiningManager.contactFrameObserver = () -> {
            contactTicks.add(tickCounter[0]);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        };
        try {
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(
                    zcylas.totality.networking.mining.MiningIntentPayload.Action.HOLD_START));
            int maxTicks = 40 + targetContacts * 20;   // generous ceiling: a scheduling bug fails loudly, never hangs the suite
            for (int i = 0; i < maxTicks && contactTicks.size() < targetContacts; i++) {
                tickCounter[0]++;
                PlayerMiningManager.debugTickPlayer(p);
            }
            PlayerMiningManager.onIntent(p, new zcylas.totality.networking.mining.MiningIntentPayload(
                    zcylas.totality.networking.mining.MiningIntentPayload.Action.HOLD_STOP));
        } finally {
            PlayerMiningManager.contactFrameObserver = null;
        }
        List<Integer> gaps = new java.util.ArrayList<>();
        for (int i = 1; i < contactTicks.size(); i++) gaps.add(contactTicks.get(i) - contactTicks.get(i - 1));
        return gaps;
    }

    /**
     * Tooltip-eligibility + wrong-tool review-fix pass, §2-4/§12; wrong-tool Damage tightened to
     * x0.10 in the playtest-correction pass, §1-3: target-effectiveness math through the real
     * {@link PlayerMiningPower} pipeline. "Matching" uses {@code #minecraft:mineable/pickaxe}/
     * {@code axe}/{@code shovel}; "wrong tool but breakable" is x0.10 Damage / x0.50 Speed, applied
     * AFTER Impact/Power STR (damage) and AFTER the speed cap (speed). Also confirms wrong-tool can
     * never bypass Tier and that wear stays the ordinary outcome-based policy (no new wrong-tool
     * penalty).
     */
    private static void targetEffectivenessMath(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos, BlockDamageStorage storage) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        setScore(p, STR, 10);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        var enchants = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);

        // ---- Netherite Pickaxe: 100 Damage, matching (Stone) keeps it, wrong tool (Dirt/Grass) is x0.10 ----
        equip(p, new ItemStack(Items.NETHERITE_PICKAXE));
        var netherStone = PlayerMiningPower.compute(p, stone, false, 0f);
        var netherDirt = PlayerMiningPower.compute(p, dirt, false, 0f);
        var netherGrass = PlayerMiningPower.compute(p, grass, false, 0f);
        r.check("Netherite Pickaxe vs Stone: 100 x 1.00 = 100 (matching tool keeps the original authored value)",
                netherStone.damage() == 100f, "" + netherStone.damage());
        r.check("Netherite Pickaxe vs Dirt: 100 x 0.10 = 10 (wrong tool, still breakable, does not one-hit a 100-Durability block)",
                netherDirt.damage() == 10f, "" + netherDirt.damage());
        r.check("Netherite Pickaxe vs Grass Block: 100 x 0.10 = 10 (a highly enchanted Pickaxe is no longer a viable Shovel substitute)",
                netherGrass.damage() == 10f, "" + netherGrass.damage());

        // ---- Diamond + Impact V: Impact applied BEFORE effectiveness ----
        ItemStack diamondImpact = new ItemStack(Items.DIAMOND_PICKAXE);
        diamondImpact.enchant(enchants.getOrThrow(zcylas.totality.init.ModEnchantments.IMPACT), 5);
        equip(p, diamondImpact);
        var diamondStone = PlayerMiningPower.compute(p, stone, false, 0f);
        var diamondDirt = PlayerMiningPower.compute(p, dirt, false, 0f);
        r.check("Diamond + Impact V (75+25=100 pre-effectiveness) vs Stone: 100", diamondStone.damage() == 100f, "" + diamondStone.damage());
        r.check("Diamond + Impact V vs Dirt: 100 x 0.10 = 10 (Impact resolved before effectiveness, not after)",
                diamondDirt.damage() == 10f, "" + diamondDirt.damage());

        // ---- Netherite + Impact V vs Dirt: 125 x 0.10 = 12.5 (playtest-correction pass §10 example) ----
        ItemStack netheriteImpact = new ItemStack(Items.NETHERITE_PICKAXE);
        netheriteImpact.enchant(enchants.getOrThrow(zcylas.totality.init.ModEnchantments.IMPACT), 5);
        equip(p, netheriteImpact);
        var netheriteImpactDirt = PlayerMiningPower.compute(p, dirt, false, 0f);
        r.check("Netherite + Impact V (100+25=125 pre-effectiveness) vs Dirt: 125 x 0.10 = 12.5",
                netheriteImpactDirt.damage() == 12.5f, "" + netheriteImpactDirt.damage());

        // ---- Mining Tier vs Target Effectiveness fix pass: Pickaxe/Axe of the same material share
        // the SAME authored Mining Tier (material capability), never the old pickaxe-probe-only
        // MiningTier.ofTool value (which always gave an Axe Tier 0). Verified for all six materials. ----
        java.util.Map<String, net.minecraft.world.item.Item[]> materialPairs = new java.util.LinkedHashMap<>();
        materialPairs.put("Wood", new Item[]{Items.WOODEN_PICKAXE, Items.WOODEN_AXE});
        materialPairs.put("Stone", new Item[]{Items.STONE_PICKAXE, Items.STONE_AXE});
        materialPairs.put("Copper", new Item[]{Items.COPPER_PICKAXE, Items.COPPER_AXE});
        materialPairs.put("Iron", new Item[]{Items.IRON_PICKAXE, Items.IRON_AXE});
        materialPairs.put("Diamond", new Item[]{Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE});
        materialPairs.put("Netherite", new Item[]{Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE});
        boolean allMaterialsMatchTier = true;
        StringBuilder tierMismatch = new StringBuilder();
        for (var entry : materialPairs.entrySet()) {
            int pickaxeTier = ResolvedMiningSource.of(new ItemStack(entry.getValue()[0]), stone).tier();
            int axeTier = ResolvedMiningSource.of(new ItemStack(entry.getValue()[1]), stone).tier();
            if (pickaxeTier != axeTier) {
                allMaterialsMatchTier = false;
                tierMismatch.append(entry.getKey()).append(": pick=").append(pickaxeTier).append(" axe=").append(axeTier).append(" ");
            }
        }
        r.check("Wood/Stone/Copper/Iron/Diamond/Netherite: Pickaxe Mining Tier == Axe Mining Tier (authored material capability, not the old pickaxe-only probe)",
                allMaterialsMatchTier, tierMismatch.toString());

        // ---- Iron Pickaxe -> Stone: matching, full stats (Damage 50, Speed 2.25/s) ----
        equip(p, new ItemStack(Items.IRON_PICKAXE));
        var ironPickStone = PlayerMiningPower.compute(p, stone, false, 0f);
        ItemStack ironPick = new ItemStack(Items.IRON_PICKAXE);
        float ironPickSpeedStone = PlayerMiningPower.effectiveMiningSpeed(p, ironPick, stone, ResolvedMiningSource.of(ironPick, stone));
        r.check("Iron Pickaxe vs Stone: Damage 50 (matching)", ironPickStone.damage() == 50f, "" + ironPickStone.damage());
        r.check("Iron Pickaxe vs Stone: Speed 2.25/s (matching)", ironPickSpeedStone == 2.25f, "" + ironPickSpeedStone);

        // ---- Iron Axe -> Log: matching, full stats (Damage 50, Speed 2.25/s) ----
        equip(p, new ItemStack(Items.IRON_AXE));
        var axeLog = PlayerMiningPower.compute(p, log, false, 0f);
        ItemStack ironAxe = new ItemStack(Items.IRON_AXE);
        float ironAxeSpeedLog = PlayerMiningPower.effectiveMiningSpeed(p, ironAxe, log, ResolvedMiningSource.of(ironAxe, log));
        r.check("Iron Axe (50 base) vs Log: Damage 50 (matching)", axeLog.damage() == 50f, "" + axeLog.damage());
        r.check("Iron Axe vs Log: Speed 2.25/s (matching)", ironAxeSpeedLog == 2.25f, "" + ironAxeSpeedLog);

        var axeDirt = PlayerMiningPower.compute(p, dirt, false, 0f);
        r.check("Iron Axe vs Dirt: 50 x 0.10 = 5 (wrong tool, still breakable)", axeDirt.damage() == 5f, "" + axeDirt.damage());

        // ---- Shovel vertical-slice pass, §7: Shovel is matching on Dirt/Grass (x1.00), wrong tool
        // (x0.10/x0.50) on everything a Shovel doesn't mine, exactly like Pickaxe/Axe already are. ----
        equip(p, new ItemStack(Items.IRON_SHOVEL));
        var shovelDirt = PlayerMiningPower.compute(p, dirt, false, 0f);
        var shovelGrass = PlayerMiningPower.compute(p, grass, false, 0f);
        ItemStack ironShovel = new ItemStack(Items.IRON_SHOVEL);
        float ironShovelSpeedDirt = PlayerMiningPower.effectiveMiningSpeed(p, ironShovel, dirt, ResolvedMiningSource.of(ironShovel, dirt));
        r.check("Iron Shovel vs Dirt: Damage 50 x 1.00 = 50 (matching)", shovelDirt.damage() == 50f, "" + shovelDirt.damage());
        r.check("Iron Shovel vs Grass Block: Damage 50 x 1.00 = 50 (matching)", shovelGrass.damage() == 50f, "" + shovelGrass.damage());
        r.check("Iron Shovel vs Dirt: Speed 2.25 x 1.00 = 2.25/s (matching)", ironShovelSpeedDirt == 2.25f, "" + ironShovelSpeedDirt);
        var shovelStone = PlayerMiningPower.compute(p, stone, false, 0f);
        float ironShovelSpeedStone = PlayerMiningPower.effectiveMiningSpeed(p, ironShovel, stone, ResolvedMiningSource.of(ironShovel, stone));
        r.check("Iron Shovel vs Stone: Tier matches Iron Pickaxe's, wrong tool -> 50 x 0.10 = 5 (not INEFFECTIVE)",
                shovelStone.tier() == ironPickStone.tier() && shovelStone.damage() == 5f, "" + shovelStone);
        r.check("Iron Shovel vs Stone: Speed 2.25 x 0.50 = 1.125/s (wrong tool)", ironShovelSpeedStone == 1.125f, "" + ironShovelSpeedStone);
        equip(p, new ItemStack(Items.IRON_AXE));   // restore for the remainder of this method's Axe assertions below

        // ---- Iron Axe -> Stone: Tier fix pass §2 Expected Result — Tier now PASSES (same authored
        // material Tier as Iron Pickaxe), wrong tool, Damage 50 x 0.10 = 5, Speed 2.25 x 0.50 = 1.125/s.
        // Before the Tier fix, MiningTier.ofTool gave every Axe Tier 0 against Stone specifically,
        // making this pairing incorrectly INEFFECTIVE instead of a genuine wrong-tool DAMAGED hit. ----
        var axeStone = PlayerMiningPower.compute(p, stone, false, 0f);
        float ironAxeSpeedStone = PlayerMiningPower.effectiveMiningSpeed(p, ironAxe, stone, ResolvedMiningSource.of(ironAxe, stone));
        r.check("Iron Axe vs Stone: Mining Tier matches Iron Pickaxe's (material capability, no longer forced to 0)",
                axeStone.tier() == ironPickStone.tier(), "axeTier=" + axeStone.tier() + " pickTier=" + ironPickStone.tier());
        r.check("Iron Axe vs Stone: Tier passes -> wrong-tool DAMAGED, 50 x 0.10 = 5 (not INEFFECTIVE)",
                axeStone.damage() == 5f, "" + axeStone.damage());
        r.check("Iron Axe vs Stone: Speed 2.25 x 0.50 = 1.125/s", ironAxeSpeedStone == 1.125f, "" + ironAxeSpeedStone);

        // ---- Power STR resolved BEFORE effectiveness: STR 20 (+5 modifier), RED zone, Diamond base 75 ----
        setScore(p, STR, 20);
        equip(p, new ItemStack(Items.DIAMOND_PICKAXE));
        var redStone = PlayerMiningPower.compute(p, stone, true, 1f);
        var redDirt = PlayerMiningPower.compute(p, dirt, true, 1f);
        r.check("Power STR RED (75+5x5=100 pre-effectiveness) vs Stone: 100", redStone.damage() == 100f, "" + redStone.damage());
        r.check("Power STR RED vs Dirt: 100 x 0.10 = 10 (STR resolved before effectiveness, not after)",
                redDirt.damage() == 10f, "" + redDirt.damage());
        setScore(p, STR, 10);

        // ---- Mining Speed: effectiveness applied AFTER the speed cap ----
        ItemStack netherite = new ItemStack(Items.NETHERITE_PICKAXE);
        equip(p, netherite);
        float netherSpeedStone = PlayerMiningPower.effectiveMiningSpeed(p, netherite, stone, ResolvedMiningSource.of(netherite, stone));
        float netherSpeedDirt = PlayerMiningPower.effectiveMiningSpeed(p, netherite, dirt, ResolvedMiningSource.of(netherite, dirt));
        r.check("Netherite Pickaxe Mining Speed vs Stone: 2.5/s (matching)", netherSpeedStone == 2.5f, "" + netherSpeedStone);
        r.check("Netherite Pickaxe Mining Speed vs Dirt: 2.5 x 0.50 = 1.25/s (wrong tool)", netherSpeedDirt == 1.25f, "" + netherSpeedDirt);

        ItemStack diamondEff = new ItemStack(Items.DIAMOND_PICKAXE);
        diamondEff.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
        equip(p, diamondEff);
        float diaSpeedStone = PlayerMiningPower.effectiveMiningSpeed(p, diamondEff, stone, ResolvedMiningSource.of(diamondEff, stone));
        float diaSpeedDirt = PlayerMiningPower.effectiveMiningSpeed(p, diamondEff, dirt, ResolvedMiningSource.of(diamondEff, dirt));
        r.check("Diamond + Efficiency V Mining Speed vs Stone: 7.4/s (matching, unchanged from before this fix pass)",
                Math.abs(diaSpeedStone - 7.4f) < 1e-4f, "" + diaSpeedStone);
        r.check("Diamond + Efficiency V Mining Speed vs Dirt: 7.4 x 0.50 = 3.7/s (wrong tool)",
                Math.abs(diaSpeedDirt - 3.7f) < 1e-4f, "" + diaSpeedDirt);

        // ---- Efficiency/Haste still not double-counted for a profiled tool under target effectiveness ----
        r.check("Efficiency is not double-counted (7.4/s is exactly 2.4 base + 5.0 Efficiency V, no vanilla getDestroySpeed involved)",
                Math.abs(diaSpeedStone - (2.4f + 5.0f)) < 1e-4f, "" + diaSpeedStone);

        // ---- Wrong-tool reduction cannot bypass Required Mining Tier: Wooden Axe (tier 1, and axes
        // don't match #minecraft:mineable/axe for ores anyway) vs Iron Ore (requires tier 3) ----
        setScore(p, STR, 10);
        level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
        equip(p, new ItemStack(Items.WOODEN_AXE));
        var tierBlocked = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        r.check("wrong-tool + insufficient-Tier stays INEFFECTIVE (0 damage) — effectiveness can never rescue a Tier-blocked impact",
                tierBlocked.outcome() == MiningResult.Outcome.INEFFECTIVE && p.getMainHandItem().getDamageValue() == 0,
                "" + tierBlocked);
        var tierEntry = storage.get(pos, level.getBlockState(pos));
        if (tierEntry != null) storage.remove(tierEntry);

        // ---- wrong-tool successful hit still costs the ordinary +1 wear, nothing extra ----
        // (Iron Axe vs Stone: wrong tool, but Tier now passes after this fix pass — a genuine live
        // DAMAGED outcome, matching the brief's own worked example directly.)
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        equip(p, new ItemStack(Items.IRON_AXE));
        var wrongToolHit = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        r.check("wrong-tool successful hit (Iron Axe vs Stone, 5 damage) still costs exactly the ordinary +1 wear",
                wrongToolHit.outcome() == MiningResult.Outcome.DAMAGED && p.getMainHandItem().getDamageValue() == 1,
                "" + wrongToolHit + " wear=" + p.getMainHandItem().getDamageValue());
        var wrongToolEntry = storage.get(pos, level.getBlockState(pos));
        if (wrongToolEntry != null) storage.remove(wrongToolEntry);

        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        equip(p, ItemStack.EMPTY);
    }

    /**
     * Playtest-correction pass, §11/§24: the four REAL Power Mining bands, driven through the real
     * live strike path ({@code PlayerMiningManager.strike}) on a fresh 100-Durability Stone each
     * time, with Diamond Pickaxe (75 base) and STR 20 (+5 modifier) — the brief's own worked
     * example. RED (75+25=100) exactly breaks the 100-Durability block, so its total wear (26)
     * comes from the zone extra-wear (25) PLUS vanilla's own normalised terminal-break charge (1),
     * never from {@link MiningTuning#baseWear} (which only ever charges a DAMAGED outcome) — the
     * same "terminal break charged once by vanilla, normalised to 1" rule V1 already established.
     */
    private static void powerFourBands(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos, BlockDamageStorage storage) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        setScore(p, STR, 20);   // STR modifier = +5

        record Band(String name, float force, float expectedDamage, int expectedTotalWear,
                   MiningResult.Outcome expectedOutcome) {}
        Band[] bands = {
                new Band("WHITE", 0.00f, 75f, 1, MiningResult.Outcome.DAMAGED),
                new Band("GREEN", 0.50f, 80f, 1, MiningResult.Outcome.DAMAGED),
                new Band("ORANGE", 0.70f, 85f, 6, MiningResult.Outcome.DAMAGED),
                new Band("RED", 1.00f, 100f, 26, MiningResult.Outcome.BROKEN),
        };
        for (Band b : bands) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            var stale = storage.get(pos, level.getBlockState(pos));
            if (stale != null) storage.remove(stale);
            equip(p, new ItemStack(Items.DIAMOND_PICKAXE));
            var result = PlayerMiningManager.strike(p, topHit(pos), true, b.force());
            int wear = p.getMainHandItem().getDamageValue();
            r.check("Power " + b.name() + " (Diamond 75, STR+5): damage=" + b.expectedDamage() + ", outcome=" + b.expectedOutcome()
                            + ", total wear=" + b.expectedTotalWear(),
                    result.outcome() == b.expectedOutcome() && result.applied() == b.expectedDamage() && wear == b.expectedTotalWear(),
                    "outcome=" + result.outcome() + " applied=" + result.applied() + " wear=" + wear);
            var entry = storage.get(pos, level.getBlockState(pos));
            if (entry != null) storage.remove(entry);
        }
        setScore(p, STR, 10);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        equip(p, ItemStack.EMPTY);
    }

    /** §8-9 fix pass: the six vanilla blocks' COMMON Rarity + Lore, applied via {@code DefaultItemComponentEvents}. */
    private static void vanillaItemPresentation(VerificationReporter r) {
        var rarityType = zcylas.totality.api.core.rpgutils.rarity.ItemComponents.getRarity();
        var loreType = zcylas.totality.api.core.rpgutils.rarity.ItemComponents.getLore();
        Item[] items = {Items.OAK_LOG, Items.STONE, Items.DIORITE, Items.ANDESITE, Items.GRANITE, Items.COBBLESTONE};
        boolean allHaveCommonRarity = true;
        boolean allHaveNonEmptyLore = true;
        for (Item item : items) {
            ItemStack stack = new ItemStack(item);
            var rarity = rarityType != null ? stack.get(rarityType) : null;
            if (rarity == null || rarity.rarity() != zcylas.totality.api.core.rpgutils.rarity.ItemRarity.COMMON) allHaveCommonRarity = false;
            var lore = loreType != null ? stack.get(loreType) : null;
            if (lore == null || lore.text() == null || lore.text().isBlank()) allHaveNonEmptyLore = false;
        }
        r.check("Oak Log/Stone/Diorite/Andesite/Granite/Cobblestone all carry COMMON Rarity", allHaveCommonRarity, "");
        r.check("Oak Log/Stone/Diorite/Andesite/Granite/Cobblestone all carry non-empty Lore", allHaveNonEmptyLore, "");
    }

    private static void animation(VerificationReporter r, MinecraftServer server) {
        var Style = zcylas.totality.client.mining.MiningHandAnimation.Style.PICK;
        var zero = zcylas.totality.client.mining.MiningHandAnimation.Transform.ZERO;

        // schedule = the server's real cadence numbers (windUp/recovery) for the four documented cases
        int swing = 6;
        int[][] cadence = {
                {MiningTuning.windUpTicks(swing, false, 1f), MiningTuning.recoveryTicks(1f)},          // baseline (10)
                {MiningTuning.windUpTicks(swing, false, 5.33f), MiningTuning.recoveryTicks(5.33f)},    // Efficiency V (3)
                {MiningTuning.windUpTicks(swing, false, 1.4f), MiningTuning.recoveryTicks(1.4f)},      // Haste II (7)
                {MiningTuning.windUpTicks(swing, false, 0.3f), MiningTuning.recoveryTicks(0.3f)}};     // Mining Fatigue I (33)
        int[] cycle = new int[4];
        boolean durationsMatch = true, contactsOk = true, endsNeutral = true, finiteOk = true;
        for (int i = 0; i < 4; i++) {
            var a = new zcylas.totality.client.mining.MiningHandAnimation();
            a.startNormal(Style, cadence[i][0], cadence[i][1]);
            cycle[i] = a.durationTicks();
            durationsMatch &= cycle[i] == cadence[i][0] + cadence[i][1];
            contactsOk &= a.contactTick() == cadence[i][0];
            finiteOk &= allFinite(a, cycle[i]);
            endsNeutral &= a.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NONE && a.sample(0f).equals(zero);
        }
        r.check("normal animation duration equals the server's real swing cycle (baseline 10, Efficiency V 3, Haste II 7, Fatigue I 33)",
                durationsMatch && cycle[0] == 10 && cycle[1] == 3 && cycle[2] == 7 && cycle[3] == 33, java.util.Arrays.toString(cycle));
        r.check("visual contact is a deterministic point on the schedule (== the pre-contact ticks) for every cadence", contactsOk, "");
        r.check("animation cadence ordering: Efficiency < Haste < baseline < Fatigue (Efficiency/Haste faster, Fatigue slower)",
                cycle[1] < cycle[2] && cycle[2] < cycle[0] && cycle[0] < cycle[3], "");
        r.check("normal animation returns to neutral by itself at the end of the cycle and every sample is finite/bounded", endsNeutral && finiteOk, "");

        var a = new zcylas.totality.client.mining.MiningHandAnimation();
        a.startNormal(Style, 4, 6);
        for (int i = 0; i < 4; i++) a.tick();          // exactly at contact
        var atContact = a.sample(0f);
        var start = new zcylas.totality.client.mining.MiningHandAnimation();
        start.startNormal(Style, 4, 6);
        r.check("at contact the tool is at the stroke end (strongly forward-pitched, further than at the start)",
                atContact.pitch() < -50f && start.sample(0f).pitch() < 1f + 1e-3f, "pitch=" + atContact.pitch());

        // power timeline
        var pw = new zcylas.totality.client.mining.MiningHandAnimation();
        pw.startPowerCharge(Style);
        for (int i = 0; i < 30; i++) pw.tick();       // long hold
        var held = pw.sample(0f);
        boolean holdsPose = pw.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.POWER_CHARGE && held.pitch() > 60f;
        var pw2 = new zcylas.totality.client.mining.MiningHandAnimation();
        pw2.startNormal(Style, 3, 7);
        for (int i = 0; i < 2; i++) pw2.tick();
        r.check("power charge stays in a held, more committed posture than a normal draw-back", holdsPose && held.pitch() > pw2.sample(0f).pitch()
                && held.pitch() > 60f, "held pitch=" + held.pitch());
        pw.releasePower(6, 7);
        boolean strikeMode = pw.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.POWER_STRIKE && pw.contactTick() == 6;
        for (int i = 0; i < 6; i++) pw.tick();
        boolean recMode = pw.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.POWER_RECOVERY;
        float heavyPitch = pw.sample(0f).pitch();
        r.check("power release: charge -> heavy strike (contact at the power wind-up) -> heavy recovery; strike travels further than a normal stroke",
                strikeMode && recMode && heavyPitch < atContact.pitch(), "heavy=" + heavyPitch + " normal=" + atContact.pitch());
        finiteOk = allFinite(pw, 40);
        r.check("power sequence ends at neutral with finite, bounded samples", finiteOk && pw.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NONE, "" + pw.mode());

        var ig = new zcylas.totality.client.mining.MiningHandAnimation();
        ig.startNormal(Style, 3, 7);
        ig.releasePower(6, 7);
        r.check("a release that never had a charge is ignored", ig.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NORMAL, "");

        var c = new zcylas.totality.client.mining.MiningHandAnimation();
        c.startPowerCharge(Style);
        for (int i = 0; i < 12; i++) c.tick();
        float before = c.sample(0f).pitch();
        c.cancel();
        boolean returning = c.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.RETURN;
        float justAfter = c.sample(0f).pitch();
        for (int i = 0; i < 12; i++) c.tick();
        r.check("cancelling returns smoothly to neutral (no snap at the cancel instant, neutral afterwards)",
                returning && Math.abs(justAfter - before) < 1e-3f && c.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NONE
                        && c.sample(0f).equals(zero), "before=" + before + " after=" + justAfter);
        var n = new zcylas.totality.client.mining.MiningHandAnimation();
        n.startNormal(Style, 4, 6);
        boolean neutralNoop = true;
        n.reset();
        neutralNoop &= !n.isActive() && n.sample(0.5f).equals(zero);
        var cn = new zcylas.totality.client.mining.MiningHandAnimation();
        cn.cancel();
        neutralNoop &= !cn.isActive();
        r.check("idle/reset animation renders nothing (no leaked transform)", neutralNoop, "");

        var styles = zcylas.totality.client.mining.MiningHandAnimation.Style.values();
        boolean stylesOk = true;
        for (var st : styles) {
            var s = new zcylas.totality.client.mining.MiningHandAnimation();
            s.startNormal(st, 3, 7);
            stylesOk &= allFinite(s, 12);
        }
        r.check("all broad styles (pick, axe, shovel, bare hand) sample finite through a whole cycle", stylesOk, "");
        bareHandAnimation(r);

        var swingBuf = net.minecraft.network.RegistryFriendlyByteBuf.decorator(server.registryAccess()).apply(io.netty.buffer.Unpooled.buffer());
        zcylas.totality.networking.mining.MiningSwingPayload.CODEC.encode(swingBuf, new zcylas.totality.networking.mining.MiningSwingPayload(7, 33));
        var back = zcylas.totality.networking.mining.MiningSwingPayload.CODEC.decode(swingBuf);
        r.check("the swing-schedule payload round-trips and carries only timings (no authority)",
                back.windUpTicks() == 7 && back.recoveryTicks() == 33 && zcylas.totality.networking.mining.MiningSwingPayload.class.getRecordComponents().length == 2, "" + back);
    }

    private static void sourceIdentity(VerificationReporter r, ServerLevel level) {
        ItemStack a = new ItemStack(Items.NETHERITE_PICKAXE);
        ItemStack worn = a.copy();
        worn.setDamageValue(200);
        r.check("source identity: the same tool with different durability is the SAME source (not a swap)", MiningSourceIdentity.same(a, worn), "");
        ItemStack b = new ItemStack(Items.NETHERITE_PICKAXE);
        var enchants = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        b.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
        r.check("source identity: same Item type but different enchantments is a DIFFERENT source", !MiningSourceIdentity.same(a, b), "");
        ItemStack c = new ItemStack(Items.NETHERITE_PICKAXE);
        c.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Other"));
        r.check("source identity: same Item type but different components is a different source", !MiningSourceIdentity.same(a, c), "");
        r.check("source identity: pickaxe vs another item / empty hand is a different source",
                !MiningSourceIdentity.same(a, new ItemStack(Items.DIAMOND_PICKAXE)) && !MiningSourceIdentity.same(a, ItemStack.EMPTY)
                        && MiningSourceIdentity.same(ItemStack.EMPTY, ItemStack.EMPTY), "");
        r.check("server and client use the same rule (server sameSource delegates to MiningSourceIdentity)",
                PlayerMiningManager.sameSource(a, worn) && !PlayerMiningManager.sameSource(a, b), "");
    }

    /** Bare-hand Power self-damage: band-based and flat, only on a successful damaging contact, server side. */
    private static void bodyStrain(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos, BlockDamageStorage storage) {
        var STR = zcylas.totality.api.rpg.stats.AbilityScore.STR;
        var DEX = zcylas.totality.api.rpg.stats.AbilityScore.DEX;
        // pure classification: one authoritative band mapping, shared with the presentation.
        // Playtest-correction pass §11: force=0 now resolves to the real WHITE zone (this function
        // is only ever called for a genuine Power swing — see presentationBand's own Javadoc), not
        // BAND_DEFAULT (which a non-Power swing gets directly, without calling this function at all).
        r.check("force bands: WHITE below 0.35, GREEN below 0.65, ORANGE from 0.65, RED from 0.8 (also the presentation bands)",
                MiningTuning.presentationBand(0f, false) == MiningTuning.BAND_WHITE
                        && MiningTuning.presentationBand(0.34f, false) == MiningTuning.BAND_WHITE
                        && MiningTuning.presentationBand(0.35f, false) == MiningTuning.BAND_GREEN
                        && MiningTuning.presentationBand(0.64f, false) == MiningTuning.BAND_GREEN && MiningTuning.presentationBand(0.65f, false) == MiningTuning.BAND_ORANGE
                        && MiningTuning.presentationBand(0.79f, false) == MiningTuning.BAND_ORANGE && MiningTuning.presentationBand(0.8f, false) == MiningTuning.BAND_RED, "");
        var D = MiningResult.Outcome.DAMAGED;
        r.check("body strain policy: safe (WHITE/GREEN) -> 0, orange -> " + MiningTuning.BODY_STRAIN_ORANGE + ", red -> " + MiningTuning.BODY_STRAIN_RED
                        + "; MISS-equivalents (DENIED, INEFFECTIVE, INVALID) -> 0",
                MiningTuning.bodyStrain(D, MiningTuning.BAND_GREEN) == 0f && MiningTuning.bodyStrain(D, MiningTuning.BAND_WHITE) == 0f
                        && MiningTuning.bodyStrain(D, MiningTuning.BAND_ORANGE) == MiningTuning.BODY_STRAIN_ORANGE
                        && MiningTuning.bodyStrain(D, MiningTuning.BAND_RED) == MiningTuning.BODY_STRAIN_RED
                        && MiningTuning.bodyStrain(MiningResult.Outcome.BROKEN, MiningTuning.BAND_RED) == MiningTuning.BODY_STRAIN_RED
                        && MiningTuning.bodyStrain(MiningResult.Outcome.DENIED, MiningTuning.BAND_RED) == 0f
                        && MiningTuning.bodyStrain(MiningResult.Outcome.INEFFECTIVE, MiningTuning.BAND_RED) == 0f
                        && MiningTuning.bodyStrain(MiningResult.Outcome.INVALID, MiningTuning.BAND_RED) == 0f
                        && MiningTuning.BODY_STRAIN_RED > MiningTuning.BODY_STRAIN_ORANGE && MiningTuning.BODY_STRAIN_ORANGE > 0f, "");

        // through the real server strike path
        equip(p, ItemStack.EMPTY);
        setScore(p, DEX, 12);      // bare-hand Tier 1: may damage Stone
        setScore(p, STR, 10);
        float[] forces = {0f, 0.5f, 0.7f, 1.0f};
        float[] expected = {0f, 0f, MiningTuning.BODY_STRAIN_ORANGE, MiningTuning.BODY_STRAIN_RED};
        boolean ok = true; String detail = "";
        final float[] applied = {0f};
        final int[] calls = {0};
        PlayerMiningManager.bodyStrainObserver = (who, amount) -> { applied[0] += amount; calls[0]++; };
        try {
            for (int i = 0; i < forces.length; i++) {
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                var e = storage.get(pos, level.getBlockState(pos));
                if (e != null) storage.remove(e);
                applied[0] = 0f; calls[0] = 0;
                var res = PlayerMiningManager.strike(p, topHit(pos), forces[i] > 0f, forces[i]);
                ok &= res.outcome() == MiningResult.Outcome.DAMAGED && Math.abs(applied[0] - expected[i]) < 0.01f && calls[0] == (expected[i] > 0f ? 1 : 0);
                detail += "f=" + forces[i] + " strain=" + applied[0] + " ";
            }
            r.check("successful bare-hand impact: safe forces 0, orange -> " + MiningTuning.BODY_STRAIN_ORANGE + " flat, red -> " + MiningTuning.BODY_STRAIN_RED
                    + " flat, applied once, server side (seam observes the exact amount handed to the server damage path)", ok, detail);

            // misses / lost target / ineffective / tool never hurt
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            applied[0] = 0f; calls[0] = 0;
            p.setXRot(-90f);                                                        // looking at the sky: the swing misses
            var miss = PlayerMiningManager.contactNow(p, true, 1.0f, ItemStack.EMPTY);
            p.setXRot(90f);
            r.check("a bare-hand Power swing that misses / loses its target causes no self-damage", miss == null && calls[0] == 0, "calls=" + calls[0]);
            setScore(p, DEX, 10);                                                    // tier 0: Stone is out of reach of the hand
            var ineff = PlayerMiningManager.strike(p, topHit(pos), true, 1.0f);
            r.check("an INEFFECTIVE bare-hand Power hit causes no self-damage", ineff.outcome() == MiningResult.Outcome.INEFFECTIVE && calls[0] == 0, "calls=" + calls[0]);
            setScore(p, DEX, 12);
            var e2 = storage.get(pos, level.getBlockState(pos));
            if (e2 != null) storage.remove(e2);
            equip(p, new ItemStack(Items.IRON_PICKAXE));
            var toolPower = PlayerMiningManager.strike(p, topHit(pos), true, 1.0f);
            r.check("a Power hit with a TOOL never strains the body (the tool takes the stress instead)",
                    toolPower.outcome() == MiningResult.Outcome.DAMAGED && calls[0] == 0, "calls=" + calls[0]);
            var e3 = storage.get(pos, level.getBlockState(pos));
            if (e3 != null) storage.remove(e3);
        } finally {
            PlayerMiningManager.bodyStrainObserver = null;
        }
        equip(p, ItemStack.EMPTY);
        setScore(p, DEX, 10);
    }

    private static void bareHandAnimation(VerificationReporter r) {
        var Hand = zcylas.totality.client.mining.MiningHandAnimation.Style.HAND;
        // normal: one strike per swing, on the real cadence, contact bounded and unique
        int[][] cadence = {{3, 7}, {2, 1}, {2, 5}, {10, 23}};   // baseline, Efficiency V, Haste II, Fatigue I
        boolean modeOk = true, contactOk = true, durationOk = true, finiteOk = true, oneContact = true;
        for (int[] c : cadence) {
            var a = new zcylas.totality.client.mining.MiningHandAnimation();
            a.startNormal(Hand, c[0], c[1]);
            modeOk &= a.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NORMAL && a.style() == Hand;
            contactOk &= a.contactTick() == c[0] && a.contactTick() >= 1 && a.contactTick() <= a.durationTicks();
            durationOk &= a.durationTicks() == c[0] + c[1];
            int contacts = 0;
            for (int t = 0; t < a.durationTicks() + 2; t++) {
                if (a.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NORMAL && a.age() == a.contactTick()) contacts++;
                finiteOk &= a.sample(0f).isFinite() && a.sample(1f).isFinite();
                a.tick();
            }
            oneContact &= contacts == 1;
        }
        r.check("bare-hand normal animation enters NORMAL mode as the HAND style", modeOk, "");
        r.check("bare-hand animation duration follows the real cadence (cycle = pre-contact + recovery); contact is bounded",
                durationOk && contactOk, "");
        r.check("bare-hand animation has exactly ONE contact per swing (one strike = one impact) and stays finite", oneContact && finiteOk, "");
        var slow = new zcylas.totality.client.mining.MiningHandAnimation();
        slow.startNormal(Hand, 10, 23);
        var fast = new zcylas.totality.client.mining.MiningHandAnimation();
        fast.startNormal(Hand, 2, 1);
        r.check("slower cadence gives a longer bare-hand cycle, faster cadence a shorter one", slow.durationTicks() > fast.durationTicks(), "");

        // strike direction: chamber back/up, strike forward/down
        var n = new zcylas.totality.client.mining.MiningHandAnimation();
        n.startNormal(Hand, 6, 6);
        for (int i = 0; i < 3; i++) n.tick();
        float chamber = n.sample(0f).pitch();
        for (int i = 0; i < 3; i++) n.tick();
        float strike = n.sample(0f).pitch();
        r.check("bare-hand strike goes from a chamber (drawn back) to a forward/down strike", chamber > 0f && strike < chamber && strike < 0f, chamber + " -> " + strike);

        // the normal punch is a compact chambered FORWARD punch (not a downward hammer-fist), distinct from the Power punch
        var pn = new zcylas.totality.client.mining.MiningHandAnimation();
        pn.startNormal(Hand, 10, 10);
        for (int i = 0; i < 4; i++) pn.tick();                       // end of the chamber (0.4 x 10)
        var chamberT = pn.sample(0f);
        for (int i = 0; i < 6; i++) pn.tick();                       // contact
        var contactT = pn.sample(0f);
        r.check("normal bare-hand punch: small backward chamber, then the fist drives FORWARD (little downward tilt, unlike a hammer-fist)",
                chamberT.z() > 0.1f && chamberT.pitch() > 0f && chamberT.pitch() < 20f
                        && contactT.z() < -0.45f && contactT.pitch() > -30f && chamberT.z() - contactT.z() > 0.6f,
                "chamber z=" + chamberT.z() + " pitch=" + chamberT.pitch() + " | contact z=" + contactT.z() + " pitch=" + contactT.pitch());
        float minZ = 9f, maxZ = -9f; boolean fin = true;
        for (int i = 0; i < 12; i++) { for (float pt = 0f; pt <= 1f; pt += 0.25f) { var t = pn.sample(pt); fin &= t.isFinite(); minZ = Math.min(minZ, t.z()); maxZ = Math.max(maxZ, t.z()); } pn.tick(); }
        r.check("punch follow-through is small and bounded (carries slightly past contact, then returns to neutral), all samples finite",
                fin && minZ > -0.62f && maxZ < 0.3f && !pn.isActive() && pn.sample(0f).equals(zero()), "minZ=" + minZ + " maxZ=" + maxZ);
        var pc = new zcylas.totality.client.mining.MiningHandAnimation();
        pc.startPowerCharge(Hand);
        for (int i = 0; i < 30; i++) pc.tick();
        var chargedT = pc.sample(0f);
        pc.releasePower(6, 7);
        for (int i = 0; i < 6; i++) pc.tick();
        var heavyT = pc.sample(0f);
        r.check("bare-hand POWER poses are unchanged (charge pitch 72 / z 0.55; heavy strike pitch -78 / z -0.70 + the existing 0.02 contact kick = -0.68) and clearly distinct from the normal punch",
                Math.abs(chargedT.pitch() - 72f) < 1e-3f && Math.abs(chargedT.z() - 0.55f) < 1e-3f
                        && Math.abs(heavyT.pitch() + 78f) < 1e-3f && Math.abs(heavyT.z() + 0.68f) < 1e-3f
                        && chargedT.z() > chamberT.z() + 0.3f && heavyT.pitch() < contactT.pitch() - 40f,
                "charged " + chargedT.pitch() + "/" + chargedT.z() + " heavy " + heavyT.pitch() + "/" + heavyT.z());
        var tool = new zcylas.totality.client.mining.MiningHandAnimation();
        tool.startNormal(zcylas.totality.client.mining.MiningHandAnimation.Style.PICK, 10, 10);
        for (int i = 0; i < 10; i++) tool.tick();
        r.check("tool (pickaxe) stroke pose is unchanged by the punch change (pitch -72 at contact)", Math.abs(tool.sample(0f).pitch() + 72f) < 1e-3f, "" + tool.sample(0f).pitch());

        // left/right mirroring math stays finite (the mixin applies +/-1 to x, yaw and roll only)
        boolean mirrorOk = true;
        var m = new zcylas.totality.client.mining.MiningHandAnimation();
        m.startPowerCharge(Hand);
        for (int i = 0; i < 20; i++) { m.tick(); var t = m.sample(0.5f);
            for (int inv : new int[]{1, -1}) mirrorOk &= Float.isFinite(inv * t.x()) && Float.isFinite(inv * t.yaw()) && Float.isFinite(inv * t.roll()); }
        r.check("mirrored (left-handed) transform components stay finite", mirrorOk, "");

        // power: charge, hold, release, contact once, recovery, neutral; cancel recovers; no force anywhere in the animation
        var p = new zcylas.totality.client.mining.MiningHandAnimation();
        p.startPowerCharge(Hand);
        boolean charge = p.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.POWER_CHARGE;
        for (int i = 0; i < 30; i++) p.tick();
        float held = p.sample(0f).pitch();
        var normalRef = new zcylas.totality.client.mining.MiningHandAnimation();
        normalRef.startNormal(Hand, 6, 7);
        for (int i = 0; i < 2; i++) normalRef.tick();
        float normalChamber = normalRef.sample(1f).pitch();
        r.check("bare-hand Power charge enters the charge presentation and holds a much larger chamber than a normal punch",
                charge && held > 60f && held > normalChamber + 20f, "held=" + held + " normal=" + normalChamber);
        p.releasePower(6, 7);
        boolean strikeMode = p.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.POWER_STRIKE && p.contactTick() == 6;
        int contacts = 0, ticks = 0;
        var prev = p.mode();
        while (p.isActive() && ticks++ < 60) {
            if (!p.sample(0.3f).isFinite()) contacts += 100;
            p.tick();
            // contact is the instant the strike ends and recovery begins
            if (prev == zcylas.totality.client.mining.MiningHandAnimation.Mode.POWER_STRIKE
                    && p.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.POWER_RECOVERY) contacts++;
            prev = p.mode();
        }
        r.check("bare-hand Power release -> one heavy strike with exactly one contact -> recovery -> neutral",
                strikeMode && contacts == 1 && p.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NONE && p.sample(0f).equals(zero()), "contacts=" + contacts);
        var c = new zcylas.totality.client.mining.MiningHandAnimation();
        c.startPowerCharge(Hand);
        for (int i = 0; i < 10; i++) c.tick();
        float before = c.sample(0f).pitch();
        c.cancel();
        float justAfter = c.sample(0f).pitch();
        for (int i = 0; i < 10; i++) c.tick();
        r.check("cancelling a bare-hand Power charge eases back to neutral without a snap; a new swing can start afterwards",
                Math.abs(before - justAfter) < 1e-3f && !c.isActive() && startsAfter(c), "");
    }

    private static zcylas.totality.client.mining.MiningHandAnimation.Transform zero() {
        return zcylas.totality.client.mining.MiningHandAnimation.Transform.ZERO;
    }

    private static boolean startsAfter(zcylas.totality.client.mining.MiningHandAnimation a) {
        a.startNormal(zcylas.totality.client.mining.MiningHandAnimation.Style.HAND, 3, 7);
        return a.mode() == zcylas.totality.client.mining.MiningHandAnimation.Mode.NORMAL;
    }

    /**
     * Live checks of Block/Material Profiles and Integrity ownership/transitions on real blocks (Block Breaking V2,
     * Pass 1). Part of this suite: runs ONLY from {@link MiningVerification#run}, i.e. behind the same global live-world opt-in and inside
     * its snapshot/restore of the block-damage map; every block this touches is restored and every record it creates
     * is removed before returning.
     */
    static final class BlockProfileChecks {

        private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

        private BlockProfileChecks() {}

        static void run(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos origin) {
            BlockPos base = origin.offset(8, 0, 8);
            List<BlockPos> box = new ArrayList<>();
            for (int x = 0; x < 6; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 3; z++) box.add(base.offset(x, y, z));
            List<BlockState> original = box.stream().map(level::getBlockState).toList();
            try {
                resolution(r);
                door(r, level, storage, base);
                bed(r, level, storage, base.offset(2, 0, 1));
                piston(r, level, storage, base.offset(4, 0, 0));
                doubleChest(r, level, storage, base.offset(0, 0, 2));
                transitions(r, level, storage, base.offset(3, 0, 2));
                classification(r, level, storage, base.offset(5, 0, 2));
                removalHook(r, level, storage, base.offset(1, 0, 1), base.offset(1, 0, 0));
                dataset(r, level, storage, base.offset(3, 0, 1), base.offset(5, 0, 1));
                transformations(r, level, storage, base.offset(0, 0, 4));
                transformations4b(r, level, storage, origin.offset(0, 0, 8));
                chunkBoundaryOwnership(r, level, storage);
                finalCorrectionSpecial(r, level, storage, origin.offset(16, 0, 16));
            } catch (RuntimeException ex) {
                r.check("block profiles: live checks threw", false, ex.toString());
            } finally {
                BlockProfiles.removeBlockForVerification(Blocks.RESIN_BRICKS);
                BlockProfiles.removeTransformationForVerification(Blocks.STONE, Blocks.DEEPSLATE);
                Map<Long, BlockDamageStorage.Entry> entries = storage.entriesForVerification();
                for (BlockPos p : box) {
                    BlockDamageStorage.Entry e = entries.get(p.asLong());
                    if (e != null) storage.remove(e);
                }
                for (int i = 0; i < box.size(); i++) level.setBlock(box.get(i), original.get(i), FLAGS);
            }
        }

        private static void place(ServerLevel level, BlockPos pos, BlockState state) { level.setBlock(pos, state, FLAGS); }

        private static MiningResult strike(ServerLevel level, BlockPos pos, float damage) {
            return BlockBreaking.applyImpact(level, new MiningImpact(pos, Direction.UP, Vec3.atCenterOf(pos), damage, MiningTuning.TIER_MAX,
                    MiningSource.of(MiningSource.Kind.PLAYER_TOOL, null)));
        }

        private static BlockDamageStorage.Entry at(BlockDamageStorage storage, BlockPos pos) {
            return storage.entriesForVerification().get(pos.asLong());
        }

        private static boolean near(float a, float b) { return Math.abs(a - b) < 0.01f; }

        private static float maxOf(ServerLevel level, BlockPos pos, BlockState state) {
            return BlockProfiles.resolve(level, pos, state).maxDurability();
        }

        // ── resolution on the real registry ─────────────────────────────────────────────────────

        private static void resolution(VerificationReporter r) {
            var stone = BlockProfiles.resolveStatic(Blocks.STONE.defaultBlockState());
            r.check("profile: Stone = exact block profile 100, ORDINARY, Pickaxe",
                    stone.durabilityLayer() == Layer.BLOCK && stone.maxDurability() == 100f && stone.ordinary()
                            && stone.tools().tools().equals(java.util.Set.of(Tool.PICKAXE)), "" + stone);
            var log = BlockProfiles.resolveStatic(Blocks.OAK_LOG.defaultBlockState());
            var stripped = BlockProfiles.resolveStatic(Blocks.STRIPPED_OAK_WOOD.defaultBlockState());
            r.check("profile: overworld logs resolve through the authored material family (full-block form) at 100, Axe",
                    log.durabilityLayer() == Layer.MATERIAL_FORM && BlockDurabilityDefinitions.OVERWORLD_LOG.equals(log.material())
                            && BlockProfile.Form.FULL_BLOCK.equals(log.form()) && log.maxDurability() == 100f
                            && log.tools().effective(Tool.AXE) && stripped.maxDurability() == 100f, log + " / " + stripped);
            var crimson = BlockProfiles.resolveStatic(Blocks.CRIMSON_STEM.defaultBlockState());
            r.check("profile: Nether wood is its own family, never the Overworld Wood tag family (Crimson Stem = nether_wood 100)",
                    crimson.material() != null && crimson.material().id().equals("totality:nether_wood")
                            && !BlockDurabilityDefinitions.OVERWORLD_LOG.equals(crimson.material()) && crimson.maxDurability() == 100f, "" + crimson);
            var tin = BlockProfiles.resolveStatic(BuiltInRegistries.BLOCK.getValue(Identifier.parse("totality:tin_ore")).defaultBlockState());
            r.check("profile: a deferred Totality block (Tin Ore) keeps the compatibility fallback, no material",
                    tin.durabilityLayer() == Layer.FALLBACK && tin.material() == null, "" + tin);
            r.check("profile: Required Mining Tier keeps the vanilla-derived fallback (Diamond Ore, Iron Ore, Dirt)",
                    BlockProfiles.resolveStatic(Blocks.DIAMOND_ORE.defaultBlockState()).requiredTier() == MiningTier.fallbackRequiredTier(Blocks.DIAMOND_ORE.defaultBlockState())
                            && BlockProfiles.resolveStatic(Blocks.IRON_ORE.defaultBlockState()).requiredTier() == MiningTier.fallbackRequiredTier(Blocks.IRON_ORE.defaultBlockState())
                            && BlockProfiles.resolveStatic(Blocks.DIRT.defaultBlockState()).requiredTier() == 0, "");
            r.check("profile: classification — Bedrock UNBREAKABLE, Torch SPECIAL, Air/Water NOT_APPLICABLE, Stone ORDINARY",
                    BlockProfiles.resolveStatic(Blocks.BEDROCK.defaultBlockState()).classification() == Classification.UNBREAKABLE
                            && BlockProfiles.resolveStatic(Blocks.TORCH.defaultBlockState()).classification() == Classification.SPECIAL
                            && BlockProfiles.resolveStatic(Blocks.AIR.defaultBlockState()).classification() == Classification.NOT_APPLICABLE
                            && BlockProfiles.resolveStatic(Blocks.WATER.defaultBlockState()).classification() == Classification.NOT_APPLICABLE
                            && stone.classification() == Classification.ORDINARY, "");
            r.check("profile: BlockDurability view unchanged (Bedrock unbreakable, Stone 100)",
                    BlockDurability.resolveStatic(Blocks.BEDROCK.defaultBlockState()).unbreakable()
                            && BlockDurability.resolveStatic(Blocks.STONE.defaultBlockState()).max() == 100f, "");
            r.check("profile: ownership — doors lower half, beds head, pistons base (base and head), chests per position",
                    BlockProfiles.resolveStatic(Blocks.OAK_DOOR.defaultBlockState()).ownership() == Ownership.DOOR_LOWER_HALF
                            && BlockProfiles.resolveStatic(Blocks.IRON_DOOR.defaultBlockState()).ownership() == Ownership.DOOR_LOWER_HALF
                            && BlockProfiles.resolveStatic(Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()).ownership() == Ownership.BED_HEAD
                            && BlockProfiles.resolveStatic(Blocks.PISTON.defaultBlockState()).ownership() == Ownership.PISTON_BASE
                            && BlockProfiles.resolveStatic(Blocks.STICKY_PISTON.defaultBlockState()).ownership() == Ownership.PISTON_BASE
                            && BlockProfiles.resolveStatic(Blocks.PISTON_HEAD.defaultBlockState()).ownership() == Ownership.PISTON_BASE
                            && BlockProfiles.resolveStatic(Blocks.CHEST.defaultBlockState()).ownership() == Ownership.POSITION, "");
            ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE), axe = new ItemStack(Items.NETHERITE_AXE);
            r.check("profile: target effectiveness reads the profile and matches the previous tag rule",
                    TargetEffectiveness.resolve(pick, Blocks.STONE.defaultBlockState()) == TargetEffectiveness.EFFECTIVE
                            && TargetEffectiveness.resolve(pick, Blocks.DIRT.defaultBlockState()) == TargetEffectiveness.WRONG_TOOL
                            && TargetEffectiveness.resolve(axe, Blocks.OAK_LOG.defaultBlockState()) == TargetEffectiveness.EFFECTIVE
                            && TargetEffectiveness.resolve(axe, Blocks.STONE.defaultBlockState()) == TargetEffectiveness.WRONG_TOOL, "");
        }

        // ── shared and independent ownership ────────────────────────────────────────────────────

        private static void door(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos lower) {
            BlockPos upper = lower.above();
            BlockState lowerState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            place(level, lower, lowerState);
            place(level, upper, lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            float max = maxOf(level, lower, lowerState);
            var first = strike(level, upper, 30f);
            r.check("door: striking the upper half damages the ONE record owned by the lower half",
                    first.outcome() == MiningResult.Outcome.DAMAGED && at(storage, lower) != null && near(at(storage, lower).integrity, max - 30f)
                            && at(storage, upper) == null, first + " upper=" + at(storage, upper));
            var second = strike(level, lower, 20f);
            r.check("door: both halves share Integrity (upper 30 + lower 20)", near(second.remaining(), max - 50f), "" + second);

            place(level, lower, level.getBlockState(lower).setValue(DoorBlock.OPEN, true));
            place(level, upper, level.getBlockState(upper).setValue(DoorBlock.OPEN, true));
            var afterOpen = storage.get(lower, level.getBlockState(lower));
            r.check("door: opening it (ordinary non-structural state change) preserves the record",
                    afterOpen != null && near(afterOpen.integrity, max - 50f), "" + (afterOpen == null ? null : afterOpen.integrity));

            // Legacy per-position record left on the upper half by an older save folds into the owner.
            storage.remove(at(storage, lower));
            long now = level.getGameTime();
            storage.entriesForVerification().put(upper.asLong(), new BlockDamageStorage.Entry(upper, key(Blocks.OAK_DOOR), max, max - 70f, now));
            var folded = storage.get(lower, level.getBlockState(lower));
            r.check("door: an old save's record on the upper half is folded into the lower-half owner on read",
                    folded != null && near(folded.integrity, max - 70f) && at(storage, upper) == null, "" + (folded == null ? null : folded.integrity));

            level.destroyBlock(upper, false);
            r.check("door: breaking it deletes the owner record at the moment of removal (before any read or sweep)",
                    at(storage, lower) == null && at(storage, upper) == null, "");
            storage.sweep();
            r.check("door: destroying the door removes the owner record (no orphan on either half)",
                    level.getBlockState(lower).isAir() && at(storage, lower) == null && at(storage, upper) == null, "lower=" + level.getBlockState(lower));
        }

        private static void bed(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos foot) {
            BlockPos head = foot.north();
            BlockState footState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
            place(level, foot, footState);
            place(level, head, footState.setValue(BedBlock.PART, BedPart.HEAD));
            float max = maxOf(level, head, level.getBlockState(head));
            strike(level, foot, 10f);
            r.check("bed: striking the foot damages the head-owned record only",
                    at(storage, head) != null && near(at(storage, head).integrity, max - 10f) && at(storage, foot) == null, "");

            // Old save: the owner is more damaged than a legacy foot record -> the owner's damage is kept.
            at(storage, head).integrity = max - 60f;
            storage.entriesForVerification().put(foot.asLong(), new BlockDamageStorage.Entry(foot, key(Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED)), max, max - 40f, level.getGameTime()));
            storage.sweep();
            r.check("bed: sweep folds a legacy foot record into the head, keeping the more damaged record",
                    at(storage, foot) == null && at(storage, head) != null && near(at(storage, head).integrity, max - 60f), "");
            storage.remove(at(storage, head));
        }

        private static void piston(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos basePos) {
            BlockPos headPos = basePos.above();
            BlockState extended = Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP).setValue(PistonBaseBlock.EXTENDED, true);
            place(level, basePos, extended);
            place(level, headPos, Blocks.PISTON_HEAD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
            float max = maxOf(level, basePos, extended);
            strike(level, headPos, 10f);
            r.check("piston: striking the head of an extended piston damages the base-owned record",
                    at(storage, basePos) != null && near(at(storage, basePos).integrity, max - 10f) && at(storage, headPos) == null, "");
            r.check("piston: the owner's crack is also sent to the head position while extended",
                    headPos.equals(at(storage, basePos).sentPartner), "sentPartner=" + at(storage, basePos).sentPartner);
            place(level, headPos, Blocks.AIR.defaultBlockState());
            place(level, basePos, extended.setValue(PistonBaseBlock.EXTENDED, false));
            storage.sweep();                                             // the next regular crack refresh (<= 40 ticks)
            r.check("piston: after retraction the old head-position crack is cleared by the next crack refresh, not left to expire",
                    at(storage, basePos) != null && at(storage, basePos).sentPartner == null, "sentPartner=" + at(storage, basePos).sentPartner);
            var retracted = strike(level, basePos, 10f);
            r.check("piston: retracting (same block, non-structural state change) keeps the record",
                    near(retracted.remaining(), max - 20f), "" + retracted);
            storage.remove(at(storage, basePos));
        }

        private static void doubleChest(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos left) {
            BlockPos right = left.east();   // facing north: a LEFT half connects clockwise, i.e. east
            BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
            place(level, left, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
            place(level, right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
            float max = maxOf(level, left, level.getBlockState(left));
            strike(level, left, 10f);
            strike(level, right, 30f);
            r.check("double chest: each half keeps its own independent record",
                    at(storage, left) != null && at(storage, right) != null
                            && near(at(storage, left).integrity, max - 10f) && near(at(storage, right).integrity, max - 30f), "");
            storage.remove(at(storage, left));
            storage.remove(at(storage, right));
        }

        // ── transitions and old-save migration ──────────────────────────────────────────────────

        private static void transitions(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
            BlockState stone = Blocks.STONE.defaultBlockState(), deepslate = Blocks.DEEPSLATE.defaultBlockState();
            float deepMax = maxOf(level, pos, deepslate);

            place(level, pos, stone);
            strike(level, pos, 40f);                                     // 60 / 100
            place(level, pos, deepslate);
            r.check("transition: an unrelated replacement (no authored transformation) never inherits the record",
                    storage.get(pos, deepslate) == null && at(storage, pos) == null, "");

            BlockProfiles.transformation(Blocks.STONE, Blocks.DEEPSLATE);   // verification-only, withdrawn below
            try {
                place(level, pos, stone);
                strike(level, pos, 40f);
                storage.transformBlock(pos, deepslate, FLAGS);
                var migrated = at(storage, pos);
                float expected = Math.round(deepMax * 60f / 100f);
                r.check("transition: an authored physical transformation of the live block migrates the record by percentage (60% of 100 -> 60% of " + deepMax + ")",
                        migrated != null && level.getBlockState(pos).is(Blocks.DEEPSLATE) && migrated.block.equals(key(Blocks.DEEPSLATE))
                                && migrated.max == deepMax && migrated.integrity == expected
                                && storage.get(pos, deepslate) == migrated,
                        migrated == null ? "null" : migrated.block + " " + migrated.integrity + "/" + migrated.max);
                if (migrated != null) storage.remove(migrated);

                // Follow-up regression: removal, then a later replacement whose ids form the authored pair. No read or
                // sweep runs in between, so the damaged Stone record is still stored when Deepslate appears.
                place(level, pos, stone);
                strike(level, pos, 40f);
                place(level, pos, Blocks.AIR.defaultBlockState());      // Stone removed by something outside mining
                place(level, pos, deepslate);                             // an unrelated Deepslate placed later
                r.check("transition: removal then replacement with an authored-pair block does NOT inherit the removed block's damage",
                        storage.get(pos, deepslate) == null && at(storage, pos) == null, "");

                place(level, pos, stone);
                strike(level, pos, 40f);
                place(level, pos, deepslate);                             // same pair, changed directly (no transformation call)
                r.check("transition: a direct block change between an authored pair (no transformation call) is a replacement",
                        storage.get(pos, deepslate) == null && at(storage, pos) == null, "");

                place(level, pos, stone);
                strike(level, pos, 40f);
                place(level, pos, Blocks.AIR.defaultBlockState());
                boolean changed = storage.transformBlock(pos, deepslate, FLAGS);   // a transformation call after the removal
                r.check("transition: a transformation call on a position whose block was already removed migrates nothing",
                        changed && level.getBlockState(pos).is(Blocks.DEEPSLATE) && at(storage, pos) == null
                                && storage.get(pos, deepslate) == null, "");
            } finally {
                BlockProfiles.removeTransformationForVerification(Blocks.STONE, Blocks.DEEPSLATE);
            }

            place(level, pos, stone);
            long now = level.getGameTime();
            storage.entriesForVerification().put(pos.asLong(), new BlockDamageStorage.Entry(pos, key(Blocks.STONE), 50f, 20f, now));
            var rescaled = storage.get(pos, stone);
            r.check("old save: a record saved under an older maximum (20/50) is rescaled by percentage to the current profile (40/100)",
                    rescaled != null && rescaled.max == 100f && rescaled.integrity == 40f && rescaled.lastImpactTick == now,
                    rescaled == null ? "null" : rescaled.integrity + "/" + rescaled.max);
            if (rescaled != null) storage.remove(rescaled);

            storage.entriesForVerification().put(pos.asLong(), new BlockDamageStorage.Entry(pos, key(Blocks.STONE), 0f, 0f, now));
            r.check("old save: a record with an invalid (zero) maximum is discarded safely, the block reads as full",
                    storage.get(pos, stone) == null && at(storage, pos) == null, "");
        }

        // ── classification boundary ─────────────────────────────────────────────────────────────

        private static void classification(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
            BlockState calcite = Blocks.RESIN_BRICKS.defaultBlockState();
            var before = BlockProfiles.resolveStatic(calcite);
            boolean exact = before.durabilityLayer() == Layer.BLOCK || before.durabilityLayer() == Layer.STATE_OVERRIDE
                    || before.classificationLayer() == Layer.BLOCK || before.classificationLayer() == Layer.STATE_OVERRIDE;
            if (exact) {   // the temporary exact profiles below must not replace (and then remove) a real one
                r.check("classification: Resin Bricks must have no exact block profile for this check", false, "" + before);
                return;
            }
            place(level, pos, calcite);
            float max = maxOf(level, pos, calcite);
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            strike(level, pos, 10f);

            BlockProfiles.block(Blocks.RESIN_BRICKS, BlockProfile.EMPTY.withClassification(Classification.SPECIAL));
            try {
                r.check("classification: ORDINARY -> SPECIAL deletes the finite record; vanilla owns the block; Totality never strikes it",
                        storage.get(pos, calcite) == null && at(storage, pos) == null
                                && !MiningOwnership.owns(GameType.SURVIVAL, pick, level, pos, calcite)
                                && strike(level, pos, 10f).outcome() == MiningResult.Outcome.INVALID && at(storage, pos) == null, "");
            } finally {
                BlockProfiles.removeBlockForVerification(Blocks.RESIN_BRICKS);
            }
            var fresh = strike(level, pos, 5f);
            r.check("classification: SPECIAL -> ORDINARY starts at full Integrity (no dormant damage)",
                    near(fresh.remaining(), max - 5f), "" + fresh);

            BlockProfiles.block(Blocks.RESIN_BRICKS, BlockProfile.EMPTY.withClassification(Classification.UNBREAKABLE));
            try {
                r.check("classification: UNBREAKABLE is owned (no vanilla progress) but never damaged, and deletes the record",
                        MiningOwnership.owns(GameType.SURVIVAL, pick, level, pos, calcite)
                                && strike(level, pos, 10f).outcome() == MiningResult.Outcome.INVALID
                                && storage.get(pos, calcite) == null && BlockDurability.resolve(level, pos, calcite).unbreakable(), "");
            } finally {
                BlockProfiles.removeBlockForVerification(Blocks.RESIN_BRICKS);
            }

            // Dormant record in a chunk nobody reads: the start-up pass removes it by block id alone.
            strike(level, pos, 10f);
            long now = level.getGameTime();
            BlockPos ghost = pos.above();
            storage.entriesForVerification().put(ghost.asLong(),
                    new BlockDamageStorage.Entry(ghost, Identifier.fromNamespaceAndPath("totality", "verification_missing_block"), 100f, 50f, now));
            int removed;
            BlockProfiles.block(Blocks.RESIN_BRICKS, BlockProfile.EMPTY.withClassification(Classification.SPECIAL));
            try {
                removed = storage.reconcileRecords();
            } finally {
                BlockProfiles.removeBlockForVerification(Blocks.RESIN_BRICKS);
            }
            r.check("old save: start-up reconciliation drops records of now-non-ORDINARY and no-longer-registered blocks",
                    removed == 2 && at(storage, pos) == null && at(storage, ghost) == null, "removed=" + removed);
        }

        // ── Pass 2: removal notification (same-id replacement safeguard) ────────────────────────

        private static void removalHook(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos, BlockPos doorLower) {
            BlockState stone = Blocks.STONE.defaultBlockState();

            place(level, pos, stone);
            strike(level, pos, 40f);                                     // 60 / 100
            boolean recorded = at(storage, pos) != null;
            place(level, pos, Blocks.AIR.defaultBlockState());           // removed outside the strike pipeline...
            boolean deletedAtRemoval = at(storage, pos) == null;
            place(level, pos, stone);                                     // ...and fresh Stone placed, no read/sweep between
            var fresh = strike(level, pos, 10f);
            r.check("same-id replacement: damaged Stone removed, fresh Stone placed with no read/sweep between -> the new Stone starts intact",
                    recorded && deletedAtRemoval && near(fresh.remaining(), 90f), "recorded=" + recorded + " deleted=" + deletedAtRemoval + " " + fresh);
            storage.remove(at(storage, pos));

            place(level, pos, stone);
            strike(level, pos, 40f);
            place(level, pos, Blocks.DIRT.defaultBlockState());
            r.check("different-id replacement: the record is deleted at the removal itself (no later id comparison needed)",
                    at(storage, pos) == null, "");

            BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
            place(level, pos, log);
            strike(level, pos, 30f);
            place(level, pos, log.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            r.check("ordinary state change (same block, new state) is not a removal: the record survives unchanged",
                    at(storage, pos) != null && near(at(storage, pos).integrity, 70f), "");
            storage.remove(at(storage, pos));

            BlockProfiles.transformation(Blocks.STONE, Blocks.DEEPSLATE);   // verification-only, withdrawn below
            try {
                place(level, pos, stone);
                strike(level, pos, 40f);
                storage.transformBlock(pos, Blocks.DEEPSLATE.defaultBlockState(), FLAGS);
                var migrated = at(storage, pos);
                r.check("authorized transformation: the removal hook does not clear the record inside transformBlock (migrated by percentage)",
                        migrated != null && migrated.block.equals(key(Blocks.DEEPSLATE)), migrated == null ? "null" : migrated.block.toString());
                if (migrated != null) storage.remove(migrated);
            } finally {
                BlockProfiles.removeTransformationForVerification(Blocks.STONE, Blocks.DEEPSLATE);
            }

            BlockPos upper = doorLower.above();
            BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            place(level, doorLower, door);
            place(level, upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            strike(level, upper, 30f);
            place(level, doorLower, level.getBlockState(doorLower).setValue(DoorBlock.OPEN, true));
            place(level, upper, level.getBlockState(upper).setValue(DoorBlock.OPEN, true));
            boolean keptOnOpen = at(storage, doorLower) != null;
            place(level, upper, Blocks.AIR.defaultBlockState());           // partner half removed, owner half still standing
            boolean keptOnPartnerRemoval = at(storage, doorLower) != null;
            place(level, doorLower, Blocks.AIR.defaultBlockState());       // owner removed
            r.check("shared assembly: the owner record survives state changes and the partner's removal, and is deleted with the owner",
                    keptOnOpen && keptOnPartnerRemoval && at(storage, doorLower) == null && at(storage, upper) == null,
                    "open=" + keptOnOpen + " partner=" + keptOnPartnerRemoval);
        }

        // ── Pass 3: the authored vanilla dataset ────────────────────────────────────────────────

        private static BlockProfile.Resolved res(Block block) { return BlockProfiles.resolveStatic(block.defaultBlockState()); }

        private static boolean hp(Block block, float expected) { return res(block).ordinary() && res(block).maxDurability() == expected; }

        private static void dataset(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos slabPos, BlockPos snowPos) {
            r.check("dataset: every ledger id exists in the live 26.2 registry", VanillaBlockProfiles.missingIds().isEmpty(),
                    "" + VanillaBlockProfiles.missingIds());
            r.check("dataset anchors: Stone/Wood 100, Deepslate 150, Sand/Gravel 75, Netherrack 50, Blackstone 120, End Stone 200",
                    hp(Blocks.STONE, 100) && hp(Blocks.OAK_PLANKS, 100) && hp(Blocks.OAK_LOG, 100) && hp(Blocks.DEEPSLATE, 150)
                            && hp(Blocks.SAND, 75) && hp(Blocks.GRAVEL, 75) && hp(Blocks.NETHERRACK, 50) && hp(Blocks.BLACKSTONE, 120)
                            && hp(Blocks.END_STONE, 200), "");
            r.check("dataset anchors: ores host+20 (120/170/70), Ancient Debris 300, Obsidian/Crying 600, Reinforced Deepslate 1000",
                    hp(Blocks.COAL_ORE, 120) && hp(Blocks.DIAMOND_ORE, 120) && hp(Blocks.DEEPSLATE_IRON_ORE, 170)
                            && hp(Blocks.NETHER_GOLD_ORE, 70) && hp(Blocks.NETHER_QUARTZ_ORE, 70) && hp(Blocks.ANCIENT_DEBRIS, 300)
                            && hp(Blocks.OBSIDIAN, 600) && hp(Blocks.CRYING_OBSIDIAN, 600) && hp(Blocks.REINFORCED_DEEPSLATE, 1000), "");
            BlockState doubleSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
            r.check("forms: Stone slab 50 (material+form) / double 100 (state override -> double-slab form); stairs 75; wall 75",
                    hp(Blocks.STONE_SLAB, 50) && res(Blocks.STONE_SLAB).durabilityLayer() == Layer.MATERIAL_FORM
                            && BlockProfiles.resolveStatic(doubleSlab).maxDurability() == 100f && hp(Blocks.STONE_STAIRS, 75)
                            && hp(Blocks.COBBLESTONE_WALL, 75), BlockProfiles.resolveStatic(doubleSlab).toString());
            r.check("precedence: exact Cracked Stone Bricks 75 beats its Ordinary Stone family (material kept)",
                    hp(Blocks.CRACKED_STONE_BRICKS, 75) && res(Blocks.CRACKED_STONE_BRICKS).durabilityLayer() == Layer.BLOCK
                            && "totality:ordinary_stone".equals(res(Blocks.CRACKED_STONE_BRICKS).material().id()), "" + res(Blocks.CRACKED_STONE_BRICKS));
            r.check("forms: Wood fence 50, gate 75, door 100, trapdoor 50; glass pane 25; Blackstone slab 60; Nether Brick fence 75",
                    hp(Blocks.OAK_FENCE, 50) && hp(Blocks.OAK_FENCE_GATE, 75) && hp(Blocks.OAK_DOOR, 100) && hp(Blocks.OAK_TRAPDOOR, 50)
                            && hp(Blocks.GLASS_PANE, 25) && hp(Blocks.BLACKSTONE_SLAB, 60) && hp(Blocks.NETHER_BRICK_FENCE, 75), "");
            boolean layersOk = true;
            for (int n = 1; n <= 8; n++) {
                var p = BlockProfiles.resolveStatic(Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, n));
                layersOk &= p.maxDurability() == 5f * n && p.durabilityLayer() == Layer.STATE_OVERRIDE;
            }
            r.check("state-dependent: Snow layers 5 HP per layer (5..40) via exact state overrides", layersOk, "");
            r.check("tools: Glass/Wool neutral, Leaves Hoe, Crafting Table Axe, Stonecutter Pickaxe",
                    res(Blocks.GLASS).tools().neutral() && res(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)).tools().neutral()
                            && res(Blocks.OAK_LEAVES).tools().effective(Tool.HOE) && !res(Blocks.OAK_LEAVES).tools().neutral()
                            && res(Blocks.CRAFTING_TABLE).tools().effective(Tool.AXE) && res(Blocks.STONECUTTER).tools().effective(Tool.PICKAXE), "");
            java.util.List<String> tierDrift = new java.util.ArrayList<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                BlockState st = b.defaultBlockState();
                var p = BlockProfiles.resolveStatic(st);
                int vanilla = b.defaultDestroyTime() < 0f ? 0 : MiningTier.fallbackRequiredTier(st);
                if (p.ordinary() && p.requiredTier() != vanilla) tierDrift.add(key(b) + "=" + p.requiredTier() + "/" + vanilla);
            }
            r.check("Required Mining Tier: every ordinary block keeps its vanilla-derived tier (no tier authored)", tierDrift.isEmpty(), "" + tierDrift);
            r.check("classification: Cobweb/TNT/Dragon Egg/Decorated Pot/Turtle Egg/Frogspawn SPECIAL; Bedrock/Barrier/Nether Portal UNBREAKABLE",
                    res(Blocks.COBWEB).classification() == Classification.SPECIAL && res(Blocks.TNT).classification() == Classification.SPECIAL
                            && res(Blocks.DRAGON_EGG).classification() == Classification.SPECIAL && res(Blocks.DECORATED_POT).classification() == Classification.SPECIAL
                            && res(Blocks.TURTLE_EGG).classification() == Classification.SPECIAL && res(Blocks.FROGSPAWN).classification() == Classification.SPECIAL
                            && res(Blocks.BEDROCK).classification() == Classification.UNBREAKABLE && res(Blocks.BARRIER).classification() == Classification.UNBREAKABLE
                            && res(Blocks.NETHER_PORTAL).classification() == Classification.UNBREAKABLE, "");
            r.check("Totality Core deferral: Tin Ore stays on the compatibility fallback; Ruby ores use the provisional 120 / 170",
                    res(block("totality:tin_ore")).durabilityLayer() == Layer.FALLBACK && hp(block("totality:ruby_ore"), 120)
                            && hp(block("totality:deepslate_ruby_ore"), 170), "");

            // Slab merge: a same-block state change whose maximum doubles keeps the remaining percentage.
            BlockState single = Blocks.STONE_SLAB.defaultBlockState();
            place(level, slabPos, single);
            strike(level, slabPos, 25f);                                    // 25 / 50 (50%)
            place(level, slabPos, doubleSlab);                              // second slab merged: same id, new state
            var merged = storage.get(slabPos, doubleSlab);
            r.check("slab merge keeps the damaged percentage: 25/50 single -> 50/100 double (record kept, rescaled)",
                    merged != null && merged.max == 100f && merged.integrity == 50f, merged == null ? "null" : merged.integrity + "/" + merged.max);
            if (merged != null) storage.remove(merged);

            BlockState two = Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 2);
            place(level, snowPos, two);
            strike(level, snowPos, 4f);                                     // 6 / 10 (60%)
            BlockState four = two.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 4);
            place(level, snowPos, four);
            var grown = storage.get(snowPos, four);
            r.check("snow layer growth keeps the damaged percentage: 6/10 at 2 layers -> 12/20 at 4 layers",
                    grown != null && grown.max == 20f && grown.integrity == 12f, grown == null ? "null" : grown.integrity + "/" + grown.max);
            if (grown != null) storage.remove(grown);

            // A record saved before Pass 3 under the old compatibility maximum migrates by percentage on read.
            place(level, slabPos, Blocks.NETHERRACK.defaultBlockState());
            float oldMax = Blocks.NETHERRACK.defaultDestroyTime() * MiningTuning.DURABILITY_PER_HARDNESS;
            storage.entriesForVerification().put(slabPos.asLong(),
                    new BlockDamageStorage.Entry(slabPos, key(Blocks.NETHERRACK), oldMax, oldMax / 2f, level.getGameTime()));
            var migrated = storage.get(slabPos, Blocks.NETHERRACK.defaultBlockState());
            r.check("pre-Pass-3 save: Netherrack record at 50% of its old fallback max (" + oldMax + ") migrates to 25/50",
                    migrated != null && migrated.max == 50f && migrated.integrity == 25f, migrated == null ? "null" : migrated.integrity + "/" + migrated.max);
            if (migrated != null) storage.remove(migrated);

            var rows = BlockCoverageReport.rows();
            long vanillaInCompat = rows.stream().filter(row -> row.id().getNamespace().equals("minecraft")
                    && row.category() == BlockCoverageReport.Category.COMPAT_FALLBACK).count();
            java.util.List<String> unresolved = rows.stream().filter(row -> row.category() == BlockCoverageReport.Category.UNRESOLVED_DESIGN)
                    .map(row -> row.id().toString()).toList();
            r.check("coverage: one row per registry block; no vanilla block counted as accepted fallback; no vanilla UNRESOLVED_DESIGN left",
                    rows.size() == BuiltInRegistries.BLOCK.size() && vanillaInCompat == 0 && unresolved.isEmpty(),
                    "rows=" + rows.size() + " vanillaInCompat=" + vanillaInCompat + " unresolved=" + unresolved);
            reconciliationCsv(r, level);
        }

        /**
         * Every row of the canonical Pass 3 reconciliation CSV against the LIVE engine profile: classification (and that it is
         * authored, not fallback), default-state HP, the explicit double-slab HP, tool ("VANILLA" = no authored tool),
         * ownership. Reads the checked-in reference file from the project directory (the run directory is build/verification-run).
         */
        private static void reconciliationCsv(VerificationReporter r, ServerLevel level) {
            java.nio.file.Path csv = level.getServer().getServerDirectory().toAbsolutePath().getParent().getParent()
                    .resolve("Context/References/TOTALITY_BLOCK_BREAKING_V2_PASS3_RECONCILIATION_DELTA_176.csv");
            java.util.List<String> lines;
            try {
                lines = java.nio.file.Files.readAllLines(csv);
            } catch (java.io.IOException e) {
                r.check("reconciliation CSV readable", false, csv + ": " + e);
                return;
            }
            java.util.List<String> header = java.util.Arrays.asList(lines.getFirst().split(","));
            int iId = header.indexOf("registry_id"), iClass = header.indexOf("new_classification"), iHp = header.indexOf("new_default_hp"),
                    iTool = header.indexOf("effective_tool"), iOwner = header.indexOf("ownership"), iNotes = header.indexOf("notes");
            java.util.List<String> bad = new java.util.ArrayList<>();
            int rowsChecked = 0, ordinary = 0, special = 0;
            java.util.regex.Pattern doubleSlab = java.util.regex.Pattern.compile("Double slab (\\d+)");
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) continue;
                java.util.List<String> f = splitCsv(line);
                rowsChecked++;
                var id = Identifier.parse(f.get(iId));
                var block = BuiltInRegistries.BLOCK.getOptional(id);
                if (block.isEmpty()) { bad.add(id + " missing from registry"); continue; }
                var p = BlockProfiles.resolveStatic(block.get().defaultBlockState());
                if (f.get(iClass).equals("SPECIAL")) {
                    special++;
                    if (p.classification() != Classification.SPECIAL || p.classificationLayer() == Layer.FALLBACK) bad.add(id + " not authored SPECIAL: " + p);
                    continue;
                }
                ordinary++;
                float hp = Float.parseFloat(f.get(iHp));
                if (!p.ordinary() || p.maxDurability() != hp || p.durabilityLayer() == Layer.FALLBACK) bad.add(id + " hp " + p.maxDurability() + " != " + hp + " " + p.durabilityLayer());
                String tool = f.get(iTool);
                boolean toolOk = switch (tool) {
                    case "VANILLA" -> p.toolsLayer() == Layer.FALLBACK;
                    case "NEUTRAL" -> p.tools().neutral();
                    default -> !p.tools().neutral() && p.tools().tools().equals(java.util.Set.of(Tool.valueOf(tool)));
                };
                if (!toolOk) bad.add(id + " tool " + p.tools() + " != " + tool);
                if (!p.ownership().name().equals(f.get(iOwner))) bad.add(id + " ownership " + p.ownership() + " != " + f.get(iOwner));
                var m = doubleSlab.matcher(f.get(iNotes));
                if (m.find()) {
                    BlockState dbl = block.get().defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                            net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
                    float expected = Float.parseFloat(m.group(1));
                    if (BlockProfiles.resolveStatic(dbl).maxDurability() != expected) bad.add(id + " double " + BlockProfiles.resolveStatic(dbl).maxDurability() + " != " + expected);
                }
            }
            r.check("reconciliation: all CSV rows resolve exactly as specified (176 rows: 147 ORDINARY + 29 SPECIAL)",
                    bad.isEmpty() && rowsChecked == 176 && ordinary == 147 && special == 29,
                    "rows=" + rowsChecked + " ordinary=" + ordinary + " special=" + special + " problems=" + bad);
        }

        /** Minimal CSV field splitter (double-quoted fields may contain commas). */
        private static java.util.List<String> splitCsv(String line) {
            java.util.List<String> out = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else quoted = !quoted;
                } else if (c == ',' && !quoted) {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else cur.append(c);
            }
            out.add(cur.toString());
            return out;
        }

        // ── Pass 4A: production in-place transformations through the REAL vanilla entry points ──────

        private static InteractionResultHolder use(ServerLevel level, net.minecraft.server.level.ServerPlayer p, BlockPos pos, ItemStack stack) {
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            var result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            return new InteractionResultHolder(result, p.getMainHandItem());
        }

        private record InteractionResultHolder(net.minecraft.world.InteractionResult result, ItemStack after) {}

        /** Random ticks through the real block path until the block id changes (weathering); false if it never did. */
        private static boolean weather(ServerLevel level, BlockPos pos) {
            Block before = level.getBlockState(pos).getBlock();
            for (int i = 0; i < 20_000 && level.getBlockState(pos).is(before); i++) level.getBlockState(pos).randomTick(level, pos, level.getRandom());
            return !level.getBlockState(pos).is(before);
        }

        private static String rec(BlockDamageStorage storage, BlockPos pos) {
            var e = at(storage, pos);
            return e == null ? "none" : e.block + " " + e.integrity + "/" + e.max;
        }

        private static boolean recIs(BlockDamageStorage storage, BlockPos pos, Block block, float integrity, float max) {
            var e = at(storage, pos);
            return e != null && e.block.equals(key(block)) && e.integrity == integrity && e.max == max;
        }

        private static void transformations(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos base) {
            List<BlockPos> area = new ArrayList<>();
            for (int x = 0; x < 8; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 4; z++) area.add(base.offset(x, y, z));
            List<BlockState> original = area.stream().map(level::getBlockState).toList();
            for (BlockPos a : area) place(level, a, Blocks.AIR.defaultBlockState());
            var p = zcylas.totality.server.TotalityFakePlayer.create(level, "[MiningVerification-transform]");
            p.setPos(base.getX() + 0.5, base.getY() + 3, base.getZ() + 0.5);
            BlockPos a = base, b = base.east(), far = base.offset(7, 0, 3);
            try {
                r.check("Pass 4A: accepted transformation pairs registered from the vanilla tables", VanillaTransformations.registeredPairs() > 100
                        && BlockProfiles.transformsTo(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG) && BlockProfiles.transformsTo(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM)
                        && BlockProfiles.transformsTo(block("minecraft:copper_block"), block("minecraft:exposed_copper")) && BlockProfiles.transformsTo(block("minecraft:exposed_copper"), block("minecraft:copper_block"))
                        && BlockProfiles.transformsTo(block("minecraft:copper_block"), block("minecraft:waxed_copper_block")) && BlockProfiles.transformsTo(block("minecraft:waxed_copper_block"), block("minecraft:copper_block"))
                        && BlockProfiles.transformsTo(Blocks.DIRT, Blocks.FARMLAND) && BlockProfiles.transformsTo(Blocks.GRASS_BLOCK, Blocks.DIRT_PATH)
                        && BlockProfiles.transformsTo(Blocks.FARMLAND, Blocks.DIRT) && !BlockProfiles.transformsTo(Blocks.STRIPPED_OAK_LOG, Blocks.OAK_LOG),
                        "pairs=" + VanillaTransformations.registeredPairs());

                // ---- wood stripping (Overworld + Nether): percentage, axis, one tool wear ----
                place(level, a, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                strike(level, a, 40f);
                var strip = use(level, p, a, new ItemStack(Items.IRON_AXE));
                BlockState stripped = level.getBlockState(a);
                r.check("Pass 4A strip: Oak Log 60/100 -> Stripped Oak Log 60/100, axis X kept, Axe worn by 1",
                        stripped.is(Blocks.STRIPPED_OAK_LOG) && stripped.getValue(BlockStateProperties.AXIS) == Direction.Axis.X
                                && recIs(storage, a, Blocks.STRIPPED_OAK_LOG, 60f, 100f) && strip.after().getDamageValue() == 1,
                        rec(storage, a) + " " + stripped + " wear=" + strip.after().getDamageValue());
                clear(level, storage, a);
                place(level, a, Blocks.CRIMSON_STEM.defaultBlockState());
                strike(level, a, 25f);
                use(level, p, a, new ItemStack(Items.IRON_AXE));
                r.check("Pass 4A strip: Crimson Stem 75/100 -> Stripped Crimson Stem 75/100",
                        level.getBlockState(a).is(Blocks.STRIPPED_CRIMSON_STEM) && recIs(storage, a, Blocks.STRIPPED_CRIMSON_STEM, 75f, 100f), rec(storage, a));
                clear(level, storage, a);

                // ---- fresh replacement with an authored pair starts intact ----
                place(level, a, Blocks.OAK_LOG.defaultBlockState());
                strike(level, a, 40f);
                place(level, a, Blocks.AIR.defaultBlockState());
                place(level, a, Blocks.STRIPPED_OAK_LOG.defaultBlockState());
                r.check("Pass 4A: a removed log and a newly placed Stripped Log (an authored pair) start intact",
                        at(storage, a) == null && storage.get(a, level.getBlockState(a)) == null, rec(storage, a));

                // ---- copper: scrape, wax (item consumed), wax off; save/reload after the transformation ----
                place(level, a, block("minecraft:exposed_copper").defaultBlockState());
                strike(level, a, 50f);                                                          // 100 / 150
                var scrape = use(level, p, a, new ItemStack(Items.IRON_AXE));
                boolean scraped = level.getBlockState(a).is(block("minecraft:copper_block")) && recIs(storage, a, block("minecraft:copper_block"), 100f, 150f);
                var wax = use(level, p, a, new ItemStack(Items.HONEYCOMB, 3));
                boolean waxed = level.getBlockState(a).is(block("minecraft:waxed_copper_block")) && recIs(storage, a, block("minecraft:waxed_copper_block"), 100f, 150f)
                        && wax.after().getCount() == 2;
                var rewax = use(level, p, a, new ItemStack(Items.HONEYCOMB, 3));
                boolean rejected = rewax.result() == net.minecraft.world.InteractionResult.PASS && rewax.after().getCount() == 3
                        && recIs(storage, a, block("minecraft:waxed_copper_block"), 100f, 150f);
                persistAfterTransformation(r, level, storage, a);
                var off = use(level, p, a, new ItemStack(Items.IRON_AXE));
                boolean unwaxed = level.getBlockState(a).is(block("minecraft:copper_block")) && recIs(storage, a, block("minecraft:copper_block"), 100f, 150f);
                r.check("Pass 4A copper: Axe scrape, Honeycomb wax (1 consumed), Axe wax-off keep 100/150; the Axe wears once per use",
                        scraped && waxed && unwaxed && scrape.after().getDamageValue() == 1 && off.after().getDamageValue() == 1,
                        "scraped=" + scraped + " waxed=" + waxed + " unwaxed=" + unwaxed + " " + rec(storage, a));
                r.check("Pass 4A copper: waxing an already waxed block is rejected (PASS): no item used, record unchanged", rejected, rec(storage, a));

                // ---- random-tick oxidation ----
                boolean weathered = weather(level, a);
                r.check("Pass 4A oxidation: Copper Block 100/150 random-ticks into Exposed Copper 100/150",
                        weathered && level.getBlockState(a).is(block("minecraft:exposed_copper")) && recIs(storage, a, block("minecraft:exposed_copper"), 100f, 150f), rec(storage, a));
                clear(level, storage, a);

                // ---- single Copper Chest: inventory and block entity survive waxing ----
                Block copperChest = block("minecraft:copper_chest"), waxedChest = block("minecraft:waxed_copper_chest");
                BlockState chestState = copperChest.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
                place(level, a, chestState);
                var chestBe = level.getBlockEntity(a);
                if (chestBe instanceof net.minecraft.world.Container c) c.setItem(0, new ItemStack(Items.DIAMOND, 5));
                strike(level, a, 50f);
                use(level, p, a, new ItemStack(Items.HONEYCOMB));
                boolean kept = level.getBlockEntity(a) instanceof net.minecraft.world.Container c && c.getItem(0).is(Items.DIAMOND)
                        && c.getItem(0).getCount() == 5 && countItems(c) == 5;
                r.check("Pass 4A Copper Chest: waxing keeps 100/150 and the inventory (5 diamonds, same slot, nothing duplicated or lost)",
                        level.getBlockState(a).is(waxedChest) && recIs(storage, a, waxedChest, 100f, 150f) && kept, rec(storage, a) + " kept=" + kept);
                clear(level, storage, a);

                // ---- double Copper Chest: both independent records follow the vanilla partner conversion ----
                place(level, a, chestState.setValue(ChestBlock.TYPE, ChestType.LEFT));
                place(level, b, chestState.setValue(ChestBlock.TYPE, ChestType.RIGHT));
                if (level.getBlockEntity(a) instanceof net.minecraft.world.Container c) c.setItem(0, new ItemStack(Items.EMERALD, 2));
                if (level.getBlockEntity(b) instanceof net.minecraft.world.Container c) c.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
                strike(level, a, 30f);                                                          // 120 / 150
                strike(level, b, 60f);                                                          // 90 / 150
                use(level, p, a, new ItemStack(Items.HONEYCOMB));
                boolean both = level.getBlockState(a).is(waxedChest) && level.getBlockState(b).is(waxedChest)
                        && recIs(storage, a, waxedChest, 120f, 150f) && recIs(storage, b, waxedChest, 90f, 150f);
                boolean items = level.getBlockEntity(a) instanceof net.minecraft.world.Container ca && ca.getItem(0).is(Items.EMERALD) && ca.getItem(0).getCount() == 2
                        && level.getBlockEntity(b) instanceof net.minecraft.world.Container cb && cb.getItem(0).is(Items.GOLD_INGOT) && cb.getItem(0).getCount() == 3;
                r.check("Pass 4A double Copper Chest: waxing one half converts both; each half keeps its own record (120, 90 of 150) and inventory",
                        both && items, rec(storage, a) + " / " + rec(storage, b) + " items=" + items);
                clear(level, storage, a);
                clear(level, storage, b);

                // ---- Golem Statue: oxidation keeps pose, facing and the block entity ----
                Block statue = block("minecraft:copper_golem_statue"), exposedStatue = block("minecraft:exposed_copper_golem_statue");
                var pose = net.minecraft.world.level.block.CopperGolemStatueBlock.Pose.STANDING.getNextPose();
                place(level, far, statue.defaultBlockState().setValue(net.minecraft.world.level.block.CopperGolemStatueBlock.POSE, pose)
                        .setValue(net.minecraft.world.level.block.CopperGolemStatueBlock.FACING, Direction.EAST));
                boolean hadBe = level.getBlockEntity(far) != null;
                strike(level, far, 50f);
                boolean statueWeathered = weather(level, far);
                BlockState st = level.getBlockState(far);
                r.check("Pass 4A Golem Statue: oxidation keeps 100/150, pose and facing (and its block entity)",
                        statueWeathered && st.is(exposedStatue) && st.getValue(net.minecraft.world.level.block.CopperGolemStatueBlock.POSE) == pose
                                && st.getValue(net.minecraft.world.level.block.CopperGolemStatueBlock.FACING) == Direction.EAST
                                && recIs(storage, far, exposedStatue, 100f, 150f) && (!hadBe || level.getBlockEntity(far) != null),
                        rec(storage, far) + " " + st);
                clear(level, storage, far);

                // ---- copper door: weathering from the lower-half owner carries the shared record ----
                BlockState door = block("minecraft:copper_door").defaultBlockState();
                place(level, far, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                place(level, far.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                strike(level, far.above(), 30f);                                                // owner (lower) 70 / 100
                boolean doorWeathered = weather(level, far);
                r.check("Pass 4A copper door: the lower-half owner record migrates 70/100 when the door oxidizes",
                        doorWeathered && level.getBlockState(far).is(block("minecraft:exposed_copper_door")) && level.getBlockState(far.above()).is(block("minecraft:exposed_copper_door"))
                                && recIs(storage, far, block("minecraft:exposed_copper_door"), 70f, 100f) && at(storage, far.above()) == null, rec(storage, far));
                clear(level, storage, far);
                place(level, far.above(), Blocks.AIR.defaultBlockState());

                // ---- Dispenser waxing ----
                BlockPos dispenser = base.offset(2, 0, 2), target = dispenser.east();   // both inside the restored area
                place(level, dispenser, Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING, Direction.EAST));
                if (level.getBlockEntity(dispenser) instanceof net.minecraft.world.Container c) c.setItem(0, new ItemStack(Items.HONEYCOMB, 2));
                place(level, target, block("minecraft:copper_block").defaultBlockState());
                strike(level, target, 50f);
                level.getBlockState(dispenser).tick(level, dispenser, level.getRandom());
                boolean dispensed = level.getBlockEntity(dispenser) instanceof net.minecraft.world.Container c && c.getItem(0).getCount() == 1;
                r.check("Pass 4A Dispenser: Honeycomb waxing keeps 100/150 and consumes one Honeycomb",
                        level.getBlockState(target).is(block("minecraft:waxed_copper_block")) && recIs(storage, target, block("minecraft:waxed_copper_block"), 100f, 150f) && dispensed,
                        rec(storage, target) + " consumed=" + dispensed);
                clear(level, storage, target);
                place(level, dispenser, Blocks.AIR.defaultBlockState());

                // ---- soil: tilling, flattening, reversion, moisture; rejected tilling ----
                place(level, a, Blocks.DIRT.defaultBlockState());
                strike(level, a, 40f);
                var till = use(level, p, a, new ItemStack(Items.IRON_HOE));
                boolean tilled = level.getBlockState(a).is(Blocks.FARMLAND) && recIs(storage, a, Blocks.FARMLAND, 60f, 100f) && till.after().getDamageValue() == 1;
                place(level, a, level.getBlockState(a).setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
                boolean moist = recIs(storage, a, Blocks.FARMLAND, 60f, 100f);
                net.minecraft.world.level.block.FarmlandBlock.turnToDirt(null, level.getBlockState(a), level, a);
                boolean reverted = level.getBlockState(a).is(Blocks.DIRT) && recIs(storage, a, Blocks.DIRT, 60f, 100f);
                r.check("Pass 4A soil: Dirt 60/100 tilled to Farmland (Hoe worn 1), moisture change kept, reverted to Dirt 60/100",
                        tilled && moist && reverted, "tilled=" + tilled + " moist=" + moist + " reverted=" + reverted + " " + rec(storage, a));
                place(level, a.above(), Blocks.STONE.defaultBlockState());
                var blocked = use(level, p, a, new ItemStack(Items.IRON_HOE));
                r.check("Pass 4A soil: tilling rejected (block above) changes nothing: Dirt and its record untouched, no wear",
                        blocked.result() == net.minecraft.world.InteractionResult.PASS && level.getBlockState(a).is(Blocks.DIRT)
                                && recIs(storage, a, Blocks.DIRT, 60f, 100f) && blocked.after().getDamageValue() == 0, rec(storage, a));
                place(level, a.above(), Blocks.AIR.defaultBlockState());
                clear(level, storage, a);
                place(level, a, Blocks.GRASS_BLOCK.defaultBlockState());
                strike(level, a, 20f);
                use(level, p, a, new ItemStack(Items.IRON_SHOVEL));
                boolean path = level.getBlockState(a).is(Blocks.DIRT_PATH) && recIs(storage, a, Blocks.DIRT_PATH, 80f, 100f);
                place(level, a.above(), Blocks.STONE.defaultBlockState());
                level.getBlockState(a).tick(level, a, level.getRandom());                       // covered Path -> Dirt
                boolean pathBack = level.getBlockState(a).is(Blocks.DIRT) && recIs(storage, a, Blocks.DIRT, 80f, 100f);
                place(level, a.above(), Blocks.AIR.defaultBlockState());
                clear(level, storage, a);
                place(level, a, Blocks.COARSE_DIRT.defaultBlockState());
                strike(level, a, 10f);
                use(level, p, a, new ItemStack(Items.IRON_HOE));
                boolean coarse = level.getBlockState(a).is(Blocks.DIRT) && recIs(storage, a, Blocks.DIRT, 90f, 100f);
                clear(level, storage, a);
                place(level, a, Blocks.ROOTED_DIRT.defaultBlockState());
                strike(level, a, 30f);
                use(level, p, a, new ItemStack(Items.IRON_HOE));
                boolean rooted = level.getBlockState(a).is(Blocks.DIRT) && recIs(storage, a, Blocks.DIRT, 70f, 100f);
                clear(level, storage, a);
                r.check("Pass 4A soil: Grass -> Path 80/100 (Shovel), covered Path -> Dirt 80/100, Coarse -> Dirt 90/100, Rooted -> Dirt 70/100",
                        path && pathBack && coarse && rooted, "path=" + path + " pathBack=" + pathBack + " coarse=" + coarse + " rooted=" + rooted);

                // ---- differing maximum through the same transaction; failed transformation ----
                BlockProfiles.transformation(Blocks.STONE, Blocks.DEEPSLATE);
                try {
                    place(level, a, Blocks.STONE.defaultBlockState());
                    strike(level, a, 40f);                                                      // 60 / 100
                    BlockDamageStorage.transaction(level, a, () -> level.setBlock(a, Blocks.DEEPSLATE.defaultBlockState(), 3));
                    r.check("Pass 4A: a transaction across a differing maximum rescales by percentage (60/100 -> 90/150)",
                            recIs(storage, a, Blocks.DEEPSLATE, 90f, 150f), rec(storage, a));
                    boolean refused = storage.transformBlock(a, level.getBlockState(a), 3);     // identical state: vanilla refuses
                    r.check("Pass 4A: a failed transformation (block change refused) leaves the block and record unchanged",
                            !refused && level.getBlockState(a).is(Blocks.DEEPSLATE) && recIs(storage, a, Blocks.DEEPSLATE, 90f, 150f), rec(storage, a));
                } finally {
                    BlockProfiles.removeTransformationForVerification(Blocks.STONE, Blocks.DEEPSLATE);
                }
                clear(level, storage, a);
            } catch (RuntimeException ex) {
                r.check("Pass 4A: transformation checks threw", false, ex.toString());
            } finally {
                for (BlockPos x : area) {
                    var e = storage.entriesForVerification().get(x.asLong());
                    if (e != null) storage.remove(e);
                }
                for (int i = 0; i < area.size(); i++) place(level, area.get(i), original.get(i));
            }
        }

        /** Pass 4B: remaining physical transformations through their REAL vanilla entry points. */
        /**
         * Final correction pass: Repeater, Comparator, End Rod and Scaffolding are explicitly SPECIAL / vanilla-owned with no
         * authored HP left behind; a Totality strike never accumulates damage on them; and nothing else in the registry moved —
         * the live coverage is compared field for field with the accepted Pass 3 reconciliation coverage CSV.
         */
        private static void finalCorrectionSpecial(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
            List<Block> blocks = List.of(Blocks.REPEATER, Blocks.COMPARATOR, Blocks.END_ROD, Blocks.SCAFFOLDING);
            List<String> bad = new ArrayList<>();
            for (Block b : blocks) {
                for (BlockState st : b.getStateDefinition().getPossibleStates()) {
                    var p = BlockProfiles.resolveStatic(st);
                    if (p.classification() != Classification.SPECIAL || p.classificationLayer() != Layer.BLOCK
                            || p.durabilityLayer() != Layer.FALLBACK || p.maxDurability() != 0f || p.material() != null) {
                        bad.add(key(b) + " " + p);
                        break;
                    }
                }
            }
            r.check("final correction: Repeater/Comparator/End Rod/Scaffolding are exact-id SPECIAL in every state, no authored "
                    + "(inert) HP, no material", bad.isEmpty(), "" + bad);

            BlockState original = level.getBlockState(pos);
            List<String> owned = new ArrayList<>();
            try {
                for (Block b : blocks) {
                    BlockState st = b.defaultBlockState();
                    place(level, pos, st);
                    MiningResult hit = strike(level, pos, 1000f);
                    boolean ownedByAny = false;
                    for (Item tool : List.of(Items.AIR, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE)) {
                        ownedByAny |= MiningOwnership.owns(net.minecraft.world.level.GameType.SURVIVAL, new ItemStack(tool), level, pos, st);
                    }
                    BlockDamageStorage.Entry e = at(storage, pos);
                    if (hit.outcome() != MiningResult.Outcome.INVALID || ownedByAny || e != null || !level.getBlockState(pos).is(b)) {
                        owned.add(key(b) + " outcome=" + hit.outcome() + " owned=" + ownedByAny + " record=" + (e == null ? "none" : e.integrity + "/" + e.max));
                    }
                    if (e != null) storage.remove(e);
                }
            } finally {
                place(level, pos, original);
            }
            r.check("final correction: the four are vanilla-owned — a 1000-damage Totality strike is INVALID, no tool or hand owns "
                    + "them, no damage record accumulates, the block stays", owned.isEmpty(), "" + owned);

            java.nio.file.Path csv = level.getServer().getServerDirectory().toAbsolutePath().getParent().getParent()
                    .resolve("Context/Audit/TOTALITY_BLOCK_BREAKING_V2_PASS3R_BLOCK_PROFILE_COVERAGE.csv");
            Map<String, String> accepted = new java.util.HashMap<>();
            try {
                for (String line : java.nio.file.Files.readAllLines(csv)) accepted.put(line.substring(0, line.indexOf(',')), line);
            } catch (java.io.IOException ex) {
                r.check("final correction: accepted Pass 3R coverage CSV readable", false, csv + " " + ex);
                return;
            }
            java.util.Set<String> expectedChanged = new java.util.TreeSet<>(List.of("minecraft:repeater", "minecraft:comparator",
                    "minecraft:end_rod", "minecraft:scaffolding"));
            java.util.Set<String> changed = new java.util.TreeSet<>();
            java.util.Map<BlockCoverageReport.Category, Integer> counts = new java.util.EnumMap<>(BlockCoverageReport.Category.class);
            boolean fourSpecial = true;
            var rows = BlockCoverageReport.rows();
            for (var row : rows) {
                counts.merge(row.category(), 1, Integer::sum);
                String id = row.id().toString();
                if (!row.csv().equals(accepted.get(id))) changed.add(id);
                if (expectedChanged.contains(id)) fourSpecial &= row.category() == BlockCoverageReport.Category.SPECIAL;
            }
            r.check("final correction: every other registry row (incl. the 30 Totality Core deferrals) is field-for-field identical to the "
                    + "accepted Pass 3R coverage; only the four changed, now SPECIAL",
                    changed.equals(expectedChanged) && fourSpecial && rows.size() == accepted.size() - 1 && rows.size() == BuiltInRegistries.BLOCK.size(),
                    "changed=" + changed + " rows=" + rows.size() + " csv=" + (accepted.size() - 1));
            r.check("final correction: coverage counts 840 ACCEPTED_AUTHORED / 338 SPECIAL / 15 UNBREAKABLE / 6 NA / 30 COMPAT_FALLBACK / 0 UNRESOLVED",
                    counts.getOrDefault(BlockCoverageReport.Category.ACCEPTED_AUTHORED, 0) == 840
                            && counts.getOrDefault(BlockCoverageReport.Category.SPECIAL, 0) == 338
                            && counts.getOrDefault(BlockCoverageReport.Category.UNBREAKABLE, 0) == 15
                            && counts.getOrDefault(BlockCoverageReport.Category.NA, 0) == 6
                            && counts.getOrDefault(BlockCoverageReport.Category.COMPAT_FALLBACK, 0) == 30
                            && counts.getOrDefault(BlockCoverageReport.Category.UNRESOLVED_DESIGN, 0) == 0, "" + counts);
        }

        private static void transformations4b(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos base) {
            level.getChunkAt(base);
            List<BlockPos> area = new ArrayList<>();
            for (int x = 0; x < 8; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 8; z++) area.add(base.offset(x, y, z));
            List<BlockState> original = area.stream().map(level::getBlockState).toList();
            for (BlockPos a : area) place(level, a, Blocks.AIR.defaultBlockState());
            var p = zcylas.totality.server.TotalityFakePlayer.create(level, "[MiningVerification-transform4b]");
            p.setPos(base.getX() + 0.5, base.getY() + 4, base.getZ() + 0.5);
            BlockPos a = base.offset(1, 0, 1), side = a.east();
            try {
                // ---- Concrete Powder -> Concrete: in place, differing maximum (45/75 -> 90/150) ----
                Block powder = Blocks.CONCRETE_POWDER.pick(net.minecraft.world.item.DyeColor.LIME), concrete = Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIME);
                place(level, a, powder.defaultBlockState());
                place(level, a.below(), Blocks.STONE.defaultBlockState());
                strike(level, a, 30f);                                                           // 45 / 75
                level.setBlockAndUpdate(side, Blocks.WATER.defaultBlockState());                 // water arrives: vanilla shape update
                r.check("Pass 4B concrete: existing Lime Powder 45/75 solidifies in place (water contact) to Lime Concrete 90/150",
                        level.getBlockState(a).is(concrete) && recIs(storage, a, concrete, 90f, 150f), rec(storage, a) + " " + level.getBlockState(a));
                persistAfterTransformation(r, level, storage, a, powder, "Lime Concrete 90/150");
                clear(level, storage, a);
                place(level, side, Blocks.AIR.defaultBlockState());
                place(level, a, powder.defaultBlockState());
                strike(level, a, 30f);
                place(level, a.below(), Blocks.AIR.defaultBlockState());
                level.getBlockState(a).tick(level, a, level.getRandom());                        // unsupported: vanilla starts a falling block
                r.check("Pass 4B concrete: a damaged powder that starts FALLING leaves its position (record deleted); nothing follows the entity",
                        !level.getBlockState(a).is(powder) && at(storage, a) == null, rec(storage, a) + " " + level.getBlockState(a));
                clear(level, storage, a);

                // ---- Anvil: menu use degradation 400 -> 300 -> 200 -> removed ----
                place(level, a.below(), Blocks.STONE.defaultBlockState());
                place(level, a, Blocks.ANVIL.defaultBlockState());
                strike(level, a, 100f);                                                          // 300 / 400 (75%)
                var menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, a));
                boolean chipped = degrade(menu, p, level, a, Blocks.CHIPPED_ANVIL) && recIs(storage, a, Blocks.CHIPPED_ANVIL, 225f, 300f);
                boolean damaged = degrade(menu, p, level, a, Blocks.DAMAGED_ANVIL) && recIs(storage, a, Blocks.DAMAGED_ANVIL, 150f, 200f);
                boolean broke = degrade(menu, p, level, a, Blocks.AIR) && at(storage, a) == null;
                r.check("Pass 4B anvil: use degradation keeps the percentage (300/400 -> 225/300 -> 150/200); the terminal break deletes the record",
                        chipped && damaged && broke, "chipped=" + chipped + " damaged=" + damaged + " broke=" + broke + " " + rec(storage, a));
                clear(level, storage, a);

                // ---- Sponge -> Wet Sponge (existing sponge, water arrives), equal maximum ----
                place(level, a, Blocks.SPONGE.defaultBlockState());
                strike(level, a, 20f);                                                           // 30 / 50
                level.setBlockAndUpdate(side, Blocks.WATER.defaultBlockState());
                r.check("Pass 4B sponge: an existing Sponge 30/50 absorbing water becomes Wet Sponge 30/50 (and the water is gone)",
                        level.getBlockState(a).is(Blocks.WET_SPONGE) && recIs(storage, a, Blocks.WET_SPONGE, 30f, 50f)
                                && !level.getBlockState(side).is(Blocks.WATER), rec(storage, a) + " side=" + level.getBlockState(side));
                clear(level, storage, a);
                place(level, side, Blocks.AIR.defaultBlockState());
                place(level, a, Blocks.WET_SPONGE.defaultBlockState());
                r.check("Pass 4B sponge: a freshly placed Wet Sponge has no record", at(storage, a) == null, rec(storage, a));
                clear(level, storage, a);

                // ---- Coral Block death (and survival with water) ----
                place(level, a, Blocks.TUBE_CORAL_BLOCK.defaultBlockState());
                strike(level, a, 10f);                                                           // 40 / 50
                place(level, side, Blocks.WATER.defaultBlockState());
                level.getBlockState(a).tick(level, a, level.getRandom());
                boolean survived = level.getBlockState(a).is(Blocks.TUBE_CORAL_BLOCK) && recIs(storage, a, Blocks.TUBE_CORAL_BLOCK, 40f, 50f);
                place(level, side, Blocks.AIR.defaultBlockState());
                level.getBlockState(a).tick(level, a, level.getRandom());
                r.check("Pass 4B coral: with water the tick changes nothing; without water Tube Coral Block 40/50 dies in place to Dead Tube Coral Block 40/50",
                        survived && level.getBlockState(a).is(Blocks.DEAD_TUBE_CORAL_BLOCK) && recIs(storage, a, Blocks.DEAD_TUBE_CORAL_BLOCK, 40f, 50f),
                        "survived=" + survived + " " + rec(storage, a));
                clear(level, storage, a);

                // ---- Cauldron contents: buckets, bottles (nested lowerFillLevel), lava, precipitation, dripstone ----
                place(level, a, Blocks.CAULDRON.defaultBlockState());
                strike(level, a, 30f);                                                           // 120 / 150
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(a).add(0, 0.5, 0), Direction.UP, a, false);
                ItemStack water = new ItemStack(Items.WATER_BUCKET);
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, water);
                level.getBlockState(a).useItemOn(water, level, p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                boolean filled = level.getBlockState(a).is(Blocks.WATER_CAULDRON) && recIs(storage, a, Blocks.WATER_CAULDRON, 120f, 150f)
                        && p.getMainHandItem().is(Items.BUCKET);
                int bottles = 0;
                for (int i = 0; i < 3; i++) {
                    ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                    p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, bottle);
                    level.getBlockState(a).useItemOn(bottle, level, p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    if (p.getMainHandItem().is(Items.POTION) || p.getInventory().contains(new ItemStack(Items.POTION))) bottles++;
                    if (i < 2 && !recIs(storage, a, Blocks.WATER_CAULDRON, 120f, 150f)) bottles = -100;   // same-id level changes keep it
                }
                boolean emptied = level.getBlockState(a).is(Blocks.CAULDRON) && recIs(storage, a, Blocks.CAULDRON, 120f, 150f);
                r.check("Pass 4B cauldron: empty 120/150 + Water Bucket -> Water Cauldron 120/150 (bucket returned); 3 bottles lower it to empty, still 120/150",
                        filled && emptied && bottles == 3, "filled=" + filled + " emptied=" + emptied + " bottles=" + bottles + " " + rec(storage, a));
                ItemStack lava = new ItemStack(Items.LAVA_BUCKET);
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, lava);
                level.getBlockState(a).useItemOn(lava, level, p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                boolean lavaIn = level.getBlockState(a).is(Blocks.LAVA_CAULDRON) && recIs(storage, a, Blocks.LAVA_CAULDRON, 120f, 150f);
                ItemStack empty = new ItemStack(Items.BUCKET);
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, empty);
                level.getBlockState(a).useItemOn(empty, level, p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                boolean lavaOut = level.getBlockState(a).is(Blocks.CAULDRON) && recIs(storage, a, Blocks.CAULDRON, 120f, 150f)
                        && (p.getMainHandItem().is(Items.LAVA_BUCKET) || p.getInventory().contains(new ItemStack(Items.LAVA_BUCKET)));
                r.check("Pass 4B cauldron: Lava Bucket in -> Lava Cauldron 120/150; Bucket out -> empty 120/150 with a Lava Bucket returned",
                        lavaIn && lavaOut, "in=" + lavaIn + " out=" + lavaOut + " " + rec(storage, a));
                boolean rained = false;
                for (int i = 0; i < 2000 && !rained; i++) {
                    ((net.minecraft.world.level.block.CauldronBlock) Blocks.CAULDRON).handlePrecipitation(level.getBlockState(a), level, a,
                            net.minecraft.world.level.biome.Biome.Precipitation.RAIN);
                    rained = level.getBlockState(a).is(Blocks.WATER_CAULDRON);
                }
                boolean rainKept = rained && recIs(storage, a, Blocks.WATER_CAULDRON, 120f, 150f);
                net.minecraft.world.level.block.LayeredCauldronBlock.lowerFillLevel(level.getBlockState(a), level, a);   // level 1 -> empty
                boolean dried = level.getBlockState(a).is(Blocks.CAULDRON) && recIs(storage, a, Blocks.CAULDRON, 120f, 150f);
                boolean dripped = false;
                try {
                    var drip = net.minecraft.world.level.block.CauldronBlock.class.getDeclaredMethod("receiveStalactiteDrip", BlockState.class,
                            net.minecraft.world.level.Level.class, BlockPos.class, net.minecraft.world.level.material.Fluid.class);
                    drip.setAccessible(true);
                    drip.invoke(Blocks.CAULDRON, level.getBlockState(a), level, a, net.minecraft.world.level.material.Fluids.LAVA);
                    dripped = level.getBlockState(a).is(Blocks.LAVA_CAULDRON) && recIs(storage, a, Blocks.LAVA_CAULDRON, 120f, 150f);
                } catch (ReflectiveOperationException ex) {
                    r.check("Pass 4B cauldron: dripstone entry point reachable", false, ex.toString());
                }
                r.check("Pass 4B cauldron: rain fills the empty Cauldron (120/150 kept), lowerFillLevel empties it (kept), a lava drip fills it (kept)",
                        rainKept && dried && dripped, "rain=" + rainKept + " dried=" + dried + " drip=" + dripped + " " + rec(storage, a));
                clear(level, storage, a);

                // ---- Lightning deoxidation of the struck copper (its own record) ----
                Block oxidized = block("minecraft:oxidized_copper"), fresh = block("minecraft:copper_block");
                place(level, a, oxidized.defaultBlockState());
                strike(level, a, 60f);                                                           // 90 / 150
                try {
                    var clearCopper = net.minecraft.world.entity.LightningBolt.class.getDeclaredMethod("clearCopperOnLightningStrike",
                            net.minecraft.world.level.Level.class, BlockPos.class);
                    clearCopper.setAccessible(true);
                    clearCopper.invoke(null, level, a);
                    r.check("Pass 4B lightning: struck Oxidized Copper 90/150 resets to unaffected Copper 90/150 in place",
                            level.getBlockState(a).is(fresh) && recIs(storage, a, fresh, 90f, 150f), rec(storage, a));
                } catch (ReflectiveOperationException ex) {
                    r.check("Pass 4B lightning: entry point reachable", false, ex.toString());
                }
                clear(level, storage, a);

                // ---- Grass / Mycelium: decay in place; spread onto a SEPARATE Dirt keeps the Dirt's own record ----
                place(level, a, Blocks.GRASS_BLOCK.defaultBlockState());
                strike(level, a, 20f);                                                           // 80 / 100
                place(level, a.above(), Blocks.STONE.defaultBlockState());
                level.getBlockState(a).randomTick(level, a, level.getRandom());
                boolean grassDecayed = level.getBlockState(a).is(Blocks.DIRT) && recIs(storage, a, Blocks.DIRT, 80f, 100f);
                clear(level, storage, a);
                place(level, a.above(), Blocks.AIR.defaultBlockState());
                place(level, a, Blocks.MYCELIUM.defaultBlockState());
                strike(level, a, 50f);
                place(level, a.above(), Blocks.STONE.defaultBlockState());
                level.getBlockState(a).randomTick(level, a, level.getRandom());
                boolean myceliumDecayed = level.getBlockState(a).is(Blocks.DIRT) && recIs(storage, a, Blocks.DIRT, 50f, 100f);
                clear(level, storage, a);
                place(level, a.above(), Blocks.AIR.defaultBlockState());
                r.check("Pass 4B spread: covered Grass 80/100 and Mycelium 50/100 decay in place to Dirt, keeping their percentage",
                        grassDecayed && myceliumDecayed, "grass=" + grassDecayed + " mycelium=" + myceliumDecayed);
                place(level, a, Blocks.GRASS_BLOCK.defaultBlockState());
                place(level, side, Blocks.DIRT.defaultBlockState());
                strike(level, a, 10f);                                                           // source 90 / 100
                strike(level, side, 30f);                                                        // target 70 / 100
                for (int i = 0; i < 3000 && level.getBlockState(side).is(Blocks.DIRT); i++) level.getBlockState(a).randomTick(level, a, level.getRandom());
                boolean spread = level.getBlockState(side).is(Blocks.GRASS_BLOCK);
                r.check("Pass 4B spread: Grass spreading onto a damaged Dirt converts the TARGET with its own 70/100; the source keeps its own 90/100",
                        spread && recIs(storage, side, Blocks.GRASS_BLOCK, 70f, 100f) && recIs(storage, a, Blocks.GRASS_BLOCK, 90f, 100f),
                        "spread=" + spread + " light=" + level.getMaxLocalRawBrightness(a.above()) + " target=" + rec(storage, side) + " source=" + rec(storage, a));
                clear(level, storage, a);
                clear(level, storage, side);
            } catch (RuntimeException ex) {
                r.check("Pass 4B: transformation checks threw", false, ex.toString());
            } finally {
                for (BlockPos x : area) {
                    var e = storage.entriesForVerification().get(x.asLong());
                    if (e != null) storage.remove(e);
                }
                for (int i = 0; i < area.size(); i++) place(level, area.get(i), original.get(i));
            }
        }

        /** Uses the Anvil through its real menu (result-slot take -> AnvilMenu.onTake) until the block at {@code pos} becomes {@code next}. */
        private static boolean degrade(net.minecraft.world.inventory.AnvilMenu menu, net.minecraft.server.level.ServerPlayer p, ServerLevel level,
                                       BlockPos pos, Block next) {
            for (int i = 0; i < 2000; i++) {
                menu.getSlot(2).onTake(p, ItemStack.EMPTY);
                if (level.getBlockState(pos).is(next)) return true;
            }
            return false;
        }

        /**
         * Pass 4B hardening: an assembly at the very edge of a loaded chunk whose UNRELATED neighbouring chunk is not loaded
         * must still carry its loaded owner / partner through a transaction. Uses a far chunk loaded on its own (its
         * neighbours stay unloaded); the precondition is asserted, not assumed.
         */
        private static void chunkBoundaryOwnership(VerificationReporter r, ServerLevel level, BlockDamageStorage storage) {
            // Each scenario uses its OWN far chunk, loaded alone, so its west neighbour is genuinely unloaded (vanilla
            // neighbour updates from one scenario may legitimately load a neighbour, which would void the next one).
            // The precondition is asserted immediately before every check.
            var p = zcylas.totality.server.TotalityFakePlayer.create(level, "[MiningVerification-boundary]");
            int y = level.getMaxY() - 6;
            // Door: waxed from the UPPER half; the LOWER half owns the record.
            edgeScenario(r, level, storage, 125, 125, y, (lower, used) -> {
                BlockPos upper = lower.above();
                used.add(lower);
                used.add(upper);
                BlockState door = block("minecraft:copper_door").defaultBlockState();
                place(level, lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                place(level, upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                boolean tied = BlockDamageStorage.tiedPositionsForVerification(level, upper).contains(lower);
                strike(level, upper, 40f);                                                       // owner (lower) 60 / 100
                use(level, p, upper, new ItemStack(Items.HONEYCOMB));
                Block waxedDoor = block("minecraft:waxed_copper_door");
                r.check("boundary: chunk-edge Copper Door — the loaded lower-half owner is tied to the upper half, and waxing keeps 60/100",
                        tied && level.getBlockState(lower).is(waxedDoor) && recIs(storage, lower, waxedDoor, 60f, 100f), "tied=" + tied + " " + rec(storage, lower));
            });
            // Double Copper Chest: LEFT at the chunk edge, its RIGHT half one block east (same chunk).
            edgeScenario(r, level, storage, 127, 125, y, (left, used) -> {
                BlockPos right = left.east();
                used.add(left);
                used.add(right);
                Block chest = block("minecraft:copper_chest"), waxedChest = block("minecraft:waxed_copper_chest");
                BlockState cs = chest.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
                place(level, left, cs.setValue(ChestBlock.TYPE, ChestType.LEFT));
                place(level, right, cs.setValue(ChestBlock.TYPE, ChestType.RIGHT));
                boolean tied = BlockDamageStorage.tiedPositionsForVerification(level, left).contains(right);
                strike(level, left, 50f);
                strike(level, right, 20f);
                use(level, p, left, new ItemStack(Items.HONEYCOMB));
                r.check("boundary: chunk-edge double Copper Chest — the loaded partner half is tied, and waxing keeps both records (100, 130 of 150)",
                        tied && recIs(storage, left, waxedChest, 100f, 150f) && recIs(storage, right, waxedChest, 130f, 150f),
                        "tied=" + tied + " " + rec(storage, left) + " / " + rec(storage, right));
            });
            // Bed: FOOT at the chunk edge facing east, HEAD (the owner) one block east.
            edgeScenario(r, level, storage, 129, 125, y, (foot, used) -> {
                BlockPos head = foot.east();
                used.add(foot);
                used.add(head);
                BlockState bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
                place(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
                place(level, head, bed.setValue(BedBlock.PART, BedPart.HEAD));
                var tied = BlockDamageStorage.tiedPositionsForVerification(level, foot);
                r.check("boundary: chunk-edge Bed — the loaded head owner is tied to the foot despite the unloaded neighbour chunk",
                        tied.contains(head), "tied=" + tied);
            });
        }

        private interface EdgeBody { void run(BlockPos edge, List<BlockPos> used); }

        private static void edgeScenario(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, int cx, int cz, int y, EdgeBody body) {
            level.getChunk(cx, cz);                                                              // this chunk only
            BlockPos edge = new BlockPos(cx * 16, y, cz * 16 + 4);                              // local x = 0 (west edge)
            List<BlockPos> used = new ArrayList<>();
            List<BlockState> original = new ArrayList<>();
            for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) original.add(level.getBlockState(edge.offset(dx, dy, 0)));
            try {
                r.check("boundary precondition (chunk " + cx + "," + cz + "): the west neighbour chunk is NOT loaded, the edge chunk is",
                        !level.hasChunkAt(edge.west()) && level.hasChunkAt(edge), "westLoaded=" + level.hasChunkAt(edge.west()));
                body.run(edge, used);
            } catch (RuntimeException ex) {
                r.check("boundary (chunk " + cx + "," + cz + "): threw", false, ex.toString());
            } finally {
                for (BlockPos x : used) {
                    var e = storage.entriesForVerification().get(x.asLong());
                    if (e != null) storage.remove(e);
                }
                int i = 0;
                for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) place(level, edge.offset(dx, dy, 0), original.get(i++));
            }
        }

        private static void clear(ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
            var e = at(storage, pos);
            if (e != null) storage.remove(e);
            place(level, pos, Blocks.AIR.defaultBlockState());
        }

        private static int countItems(net.minecraft.world.Container c) {
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
            return n;
        }

        /** The migrated record survives a real save/reload and re-attaches to the NEW block only. */
        private static void persistAfterTransformation(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos) {
            persistAfterTransformation(r, level, storage, pos, block("minecraft:copper_block"), "Waxed Copper 100/150");
        }

        private static void persistAfterTransformation(VerificationReporter r, ServerLevel level, BlockDamageStorage storage, BlockPos pos,
                                                       Block oldBlock, String label) {
            var live = at(storage, pos);
            java.nio.file.Path dir = null;
            try {
                dir = java.nio.file.Files.createTempDirectory("totality_transform_persist");
                var server = level.getServer();
                var first = new net.minecraft.world.level.storage.SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess());
                var written = BlockDamageStorage.open(first);
                written.get().entries.put(pos.asLong(), new BlockDamageStorage.Entry(pos, live.block, live.max, live.integrity, live.lastImpactTick));
                written.set(written.get());
                first.close();
                var second = new net.minecraft.world.level.storage.SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess());
                var loaded = BlockDamageStorage.open(second);
                var got = loaded.get().entries.get(pos.asLong());
                BlockDamageStorage reattached = BlockDamageStorage.over(level, loaded);
                Block newBlock = level.getBlockState(pos).getBlock();
                boolean attaches = reattached.get(pos, newBlock.defaultBlockState()) != null;
                boolean oldRejected = BlockDamageStorage.over(level, loaded).get(pos, oldBlock.defaultBlockState()) == null;
                r.check("Pass 4: a transformed record (" + label + ") survives save/reload and re-attaches only to the new block",
                        got != null && got.block.equals(key(newBlock)) && got.integrity == live.integrity && got.max == live.max && attaches && oldRejected,
                        got == null ? "null" : got.block + " " + got.integrity + "/" + got.max + " attaches=" + attaches + " oldRejected=" + oldRejected);
                second.close();
            } catch (Exception ex) {
                r.check("Pass 4A: save/reload after transformation threw", false, ex.toString());
            } finally {
                if (dir != null) try (var walk = java.nio.file.Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
                } catch (java.io.IOException ignored) { }
            }
        }

        private static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)); }

        private static Identifier key(Block block) { return BuiltInRegistries.BLOCK.getKey(block); }
    }
}
