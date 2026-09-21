package zcylas.totality.api.mining;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;

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
        if (VerificationReporter.isDevEnvironment()) registerHarvestProbe();
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
        try {
            level.getChunkAt(pos);
            pure(r);
            persistence(r, server, level, pos);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            durability(r, level, pos);
            integrity(r, level, storage, pos);
            sharedAndIdentity(r, level, storage, pos);
            tiers(r, level, storage, pos);
            forceTolerance(r);
            corrected(r, server, level);
        } finally {
            BlockState now = level.getBlockState(pos);
            BlockDamageStorage.Entry e = storage.get(pos, now);
            if (e != null) storage.remove(e);
            level.setBlockAndUpdate(pos, original);
        }
        r.summarize();
        reportPersistedState(server);
    }

    /** Dev diagnostic: how much persisted block damage each level loaded, before and after a maintenance sweep. */
    private static void reportPersistedState(MinecraftServer server) {
        for (ServerLevel l : server.getAllLevels()) {
            BlockDamageStorage s = BlockDamageStorage.get(l);
            int before = s.size();
            if (before == 0) continue;
            s.sweep();
            Totality.LOGGER.info("[MiningVerification] {}: {} persisted damaged block(s) loaded, {} after sweep (game time {})",
                    l.dimension().identifier(), before, s.size(), l.getGameTime());
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
            r.check("axe on wood: PLAYER_TOOL, intrinsic axe speed (6 x 6 = 36 damage)", axeOnLog.kind() == MiningSource.Kind.PLAYER_TOOL
                    && axeOnLog.damage() == 36f && axeOnLog.tier() == 0, "" + axeOnLog);
            equip(p, new ItemStack(Items.WOODEN_SHOVEL));
            var shovelOnDirt = PlayerMiningPower.compute(p, Blocks.DIRT.defaultBlockState(), 0f);
            r.check("shovel on dirt: PLAYER_TOOL", shovelOnDirt.kind() == MiningSource.Kind.PLAYER_TOOL && shovelOnDirt.damage() > 6f, "" + shovelOnDirt);
            equip(p, new ItemStack(Items.IRON_PICKAXE));
            var pickOnDirt = PlayerMiningPower.compute(p, Blocks.DIRT.defaultBlockState(), 0f);
            r.check("pickaxe on dirt stays a tool source (intrinsic default speed 1 -> 6 damage)",
                    pickOnDirt.kind() == MiningSource.Kind.PLAYER_TOOL && pickOnDirt.damage() == 6f, "" + pickOnDirt);
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

            // ---- tool STR = modifier only ----
            equip(p, new ItemStack(Items.NETHERITE_PICKAXE));
            setScore(p, STR, 14);
            float d14 = PlayerMiningPower.compute(p, stoneState, 0f).damage();
            setScore(p, STR, 16);
            float d16 = PlayerMiningPower.compute(p, stoneState, 0f).damage();
            Totality.LOGGER.info("[MiningVerification] netherite pickaxe on stone: STR 14 -> {} damage, STR 16 -> {} damage", d14, d16);
            r.check("same netherite pickaxe/block: STR 14 (+2) vs STR 16 (+3) differ by exactly 1 (56 vs 57)",
                    d14 == 56f && d16 == 57f && d16 - d14 == 1f, d14 + " / " + d16);

            // ---- Efficiency / Haste / Fatigue: cadence only ----
            setScore(p, STR, 10);
            equip(p, new ItemStack(Items.IRON_PICKAXE));
            resetSpeedModifiers(p);
            int swing = p.getMainHandItem().getSwingAnimation().duration();
            float baseDmg = PlayerMiningPower.compute(p, stoneState, 0f).damage();
            float basePow = PlayerMiningPower.compute(p, stoneState, 1f).damage();
            float baseLoad = PlayerMiningPower.compute(p, stoneState, 1f).forceLoad();
            float ratioBase = PlayerMiningPower.cadenceRatio(p, stoneState);
            var eff = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY);
            eff.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(EFFICIENCY_ID, 26.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            float dmgE = PlayerMiningPower.compute(p, stoneState, 0f).damage(), powE = PlayerMiningPower.compute(p, stoneState, 1f).damage();
            float loadE = PlayerMiningPower.compute(p, stoneState, 1f).forceLoad(), ratioE = PlayerMiningPower.cadenceRatio(p, stoneState);
            eff.removeModifier(EFFICIENCY_ID);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HASTE, 4000, 1));
            float dmgH = PlayerMiningPower.compute(p, stoneState, 0f).damage(), powH = PlayerMiningPower.compute(p, stoneState, 1f).damage();
            float loadH = PlayerMiningPower.compute(p, stoneState, 1f).forceLoad(), ratioH = PlayerMiningPower.cadenceRatio(p, stoneState);
            eff.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(EFFICIENCY_ID, 26.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            float dmgEH = PlayerMiningPower.compute(p, stoneState, 0f).damage(), powEH = PlayerMiningPower.compute(p, stoneState, 1f).damage();
            float ratioEH = PlayerMiningPower.cadenceRatio(p, stoneState);
            resetSpeedModifiers(p);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, 4000, 0));
            float dmgF = PlayerMiningPower.compute(p, stoneState, 0f).damage(), powF = PlayerMiningPower.compute(p, stoneState, 1f).damage();
            float loadF = PlayerMiningPower.compute(p, stoneState, 1f).forceLoad(), ratioF = PlayerMiningPower.cadenceRatio(p, stoneState);
            resetSpeedModifiers(p);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, 4000, 3));
            float ratioExtremeFatigue = PlayerMiningPower.cadenceRatio(p, stoneState);
            resetSpeedModifiers(p);
            eff.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(EFFICIENCY_ID, 100000.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            float ratioExtremeEff = PlayerMiningPower.cadenceRatio(p, stoneState);
            resetSpeedModifiers(p);

            r.check("damage per hit is IDENTICAL with Efficiency V / Haste II / Efficiency+Haste / Mining Fatigue I",
                    dmgE == baseDmg && dmgH == baseDmg && dmgEH == baseDmg && dmgF == baseDmg && baseDmg == 36f,
                    baseDmg + " " + dmgE + " " + dmgH + " " + dmgEH + " " + dmgF);
            r.check("Power Mining damage is identical under Efficiency / Haste / Fatigue (72 = 36 x 2)",
                    powE == basePow && powH == basePow && powEH == basePow && powF == basePow && basePow == 72f, "");
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
        }
    }

    private static void durability(VerificationReporter r, ServerLevel level, ServerPlayer p, BlockPos pos, BlockDamageStorage storage) {
        // (a) several partial impacts + final break: 36 + 36 + final(28 left) = 3 impacts -> exactly 3 durability
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        equip(p, iron);
        var o1 = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        int w1 = p.getMainHandItem().getDamageValue();
        var o2 = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        int w2 = p.getMainHandItem().getDamageValue();
        var o3 = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        int w3 = p.getMainHandItem().getDamageValue();
        r.check("partial impacts each cost exactly 1 durability; the final break is charged by vanilla once (1,2,3 total)",
                o1 == MiningResult.Outcome.DAMAGED && o2 == MiningResult.Outcome.DAMAGED && o3 == MiningResult.Outcome.BROKEN
                        && w1 == 1 && w2 == 2 && w3 == 3, o1 + "/" + o2 + "/" + o3 + " wear " + w1 + "," + w2 + "," + w3);
        clearItems(level, pos);

        // (b) one-shot final break: 1 durability, not 2
        setScore(p, zcylas.totality.api.rpg.stats.AbilityScore.STR, 100);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        equip(p, new ItemStack(Items.IRON_PICKAXE));
        var one = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        r.check("one-shot final break costs exactly 1 durability (no double charge)",
                one == MiningResult.Outcome.BROKEN && p.getMainHandItem().getDamageValue() == 1, one + " wear=" + p.getMainHandItem().getDamageValue());
        setScore(p, zcylas.totality.api.rpg.stats.AbilityScore.STR, 10);

        // (c) power partial: 1 base + stress
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack pw = new ItemStack(Items.IRON_PICKAXE);
        equip(p, pw);
        int stress = MiningTuning.toolStress(pw, MiningTuning.forceLoad(1f, 10));
        var pp = PlayerMiningManager.strike(p, topHit(pos), true, 1f).outcome();
        int wp = p.getMainHandItem().getDamageValue();
        r.check("power partial hit = 1 base + force stress", pp == MiningResult.Outcome.DAMAGED && wp == 1 + stress, "wear=" + wp + " expected=" + (1 + stress));
        // (d) power final: vanilla 1 + stress (72 then 72 >= 28 remaining)
        var pf = PlayerMiningManager.strike(p, topHit(pos), true, 1f).outcome();
        int wf = p.getMainHandItem().getDamageValue();
        r.check("power final hit = vanilla 1 + force stress (no extra base charge)",
                pf == MiningResult.Outcome.BROKEN && wf == 2 * (1 + stress), "wear=" + wf + " expected=" + (2 * (1 + stress)));
        clearItems(level, pos);

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

        // (f) ineffective = 0 base wear
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var e0 = storage.get(pos, level.getBlockState(pos));
        if (e0 != null) storage.remove(e0);
        equip(p, new ItemStack(Items.IRON_AXE));
        var ineff = PlayerMiningManager.strike(p, topHit(pos), false, 0f).outcome();
        var ineffPower = PlayerMiningManager.strike(p, topHit(pos), true, 1f).outcome();
        r.check("ineffective hits (axe vs Stone), normal or power, cost no durability",
                ineff == MiningResult.Outcome.INEFFECTIVE && ineffPower == MiningResult.Outcome.INEFFECTIVE
                        && p.getMainHandItem().getDamageValue() == 0, ineff + "/" + ineffPower);
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
        r.check("force bands: 0->default, low, mid, high, red zone / overload -> danger",
                MiningTuning.presentationBand(0f, false) == 0 && MiningTuning.presentationBand(0.2f, false) == 1
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
        // ---- terminal hit costs exactly 1 for damagePerBlock 0, 1 and 2 (0 would otherwise be free), and is restored ----
        for (int dpb : new int[]{0, 1, 2}) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            ItemStack t = pickaxeWithDamagePerBlock(dpb);
            equip(p, t);
            PlayerMiningManager.strike(p, topHit(pos), false, 0f);
            PlayerMiningManager.strike(p, topHit(pos), false, 0f);
            int partial = p.getMainHandItem().getDamageValue();
            var last = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
            int total = p.getMainHandItem().getDamageValue();
            var comp = p.getMainHandItem().get(net.minecraft.core.component.DataComponents.TOOL);
            r.check("damagePerBlock=" + dpb + ": partial impacts cost 1 each and the terminal hit exactly 1 (total 3); component restored, no leak",
                    last.outcome() == MiningResult.Outcome.BROKEN && partial == 2 && total == 3 && comp != null && comp.damagePerBlock() == dpb,
                    "partial=" + partial + " total=" + total + " comp=" + (comp == null ? null : comp.damagePerBlock()));
            clearItems(level, pos);
        }
        // ---- exactly ONE base durability per successful impact, whatever Tool.damagePerBlock says ----
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack heavy = pickaxeWithDamagePerBlock(2);
        equip(p, heavy);
        PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        int w1 = p.getMainHandItem().getDamageValue();
        PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        int w2 = p.getMainHandItem().getDamageValue();
        var fin = PlayerMiningManager.strike(p, topHit(pos), false, 0f);
        int w3 = p.getMainHandItem().getDamageValue();
        var restored = p.getMainHandItem().get(net.minecraft.core.component.DataComponents.TOOL);
        r.check("damagePerBlock=2 tool: partial impacts cost exactly 1 each, terminal hit brings the total to 3 (not 4), no double charge",
                fin.outcome() == MiningResult.Outcome.BROKEN && w1 == 1 && w2 == 2 && w3 == 3, "wear " + w1 + "," + w2 + "," + w3);
        r.check("the tool component is restored after the terminal break (damagePerBlock still 2)",
                restored != null && restored.damagePerBlock() == 2, "" + restored);
        clearItems(level, pos);
        // power: still additional stress on top of the base 1
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack heavy2 = pickaxeWithDamagePerBlock(2);
        equip(p, heavy2);
        int stress = MiningTuning.toolStress(heavy2, MiningTuning.forceLoad(1f, 10));
        PlayerMiningManager.strike(p, topHit(pos), true, 1f);
        r.check("damagePerBlock=2 tool, power partial hit: 1 base + stress (stress stays additional)",
                p.getMainHandItem().getDamageValue() == 1 + stress, "wear=" + p.getMainHandItem().getDamageValue() + " expected=" + (1 + stress));
        var e0 = storage.get(pos, level.getBlockState(pos));
        if (e0 != null) storage.remove(e0);

        sourceIdentity(r, level);
        bodyStrain(r, level, p, pos, storage);

        // ---- mid-swing tool swap: the source is snapshotted at swing start, the TARGET is not ----
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack toolA = new ItemStack(Items.IRON_PICKAXE);
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

        // next swing with the new tool works with the NEW tool's properties (fresh block, so 54 fits)
        if (ent2 != null) storage.remove(ent2);
        var next = PlayerMiningManager.contactNow(p, false, 0f, toolB.copy());
        r.check("next swing with the newly held tool applies normally (netherite pickaxe: 9x6 = 54 damage)",
                next != null && next.outcome() == MiningResult.Outcome.DAMAGED && Math.abs(next.applied() - 54f) < 0.01f, "" + next);

        // target changes with the same tool are still allowed
        BlockPos second = pos.west(1);
        level.setBlockAndUpdate(second, Blocks.STONE.defaultBlockState());
        p.setPos(second.getX() + 0.5, second.getY() + 1, second.getZ() + 0.5);
        var retarget = PlayerMiningManager.contactNow(p, false, 0f, toolB.copy());
        r.check("same tool, target changed before contact: the impact lands on the NEW target",
                retarget != null && retarget.outcome() == MiningResult.Outcome.DAMAGED && storage.get(second, level.getBlockState(second)) != null, "" + retarget);
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
        // pure classification: one authoritative band mapping, shared with the presentation
        r.check("force bands: safe below 0.65, ORANGE from 0.65, RED from 0.8 (also the presentation bands)",
                MiningTuning.presentationBand(0.64f, false) == MiningTuning.BAND_MID && MiningTuning.presentationBand(0.65f, false) == MiningTuning.BAND_ORANGE
                        && MiningTuning.presentationBand(0.79f, false) == MiningTuning.BAND_ORANGE && MiningTuning.presentationBand(0.8f, false) == MiningTuning.BAND_RED
                        && MiningTuning.presentationBand(0f, false) == MiningTuning.BAND_DEFAULT, "");
        var D = MiningResult.Outcome.DAMAGED;
        r.check("body strain policy: safe -> 0, orange -> " + MiningTuning.BODY_STRAIN_ORANGE + ", red -> " + MiningTuning.BODY_STRAIN_RED
                        + "; MISS-equivalents (DENIED, INEFFECTIVE, INVALID) -> 0",
                MiningTuning.bodyStrain(D, MiningTuning.BAND_MID) == 0f && MiningTuning.bodyStrain(D, MiningTuning.BAND_LOW) == 0f
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
}
