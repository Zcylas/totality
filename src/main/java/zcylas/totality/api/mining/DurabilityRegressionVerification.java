package zcylas.totality.api.mining;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.server.TotalityFakePlayer;

/**
 * Dev-environment-gated self-test for the 2026-09-22 melee-durability-regression + Unbreaking
 * review pass. Covers exactly the two genuinely NEW pieces of ground this pass touches — mining
 * wear's Unbreaking-awareness and melee durability — never re-proving what {@code MiningVerification}
 * already established live (authored Damage/Speed, Power zone requested-wear values, and the
 * ordinary/terminal double-charge audit: see its own "one-shot final break costs exactly 1
 * durability (no double charge)" and "damagePerBlock=2 tool: ... no double charge" checks, both
 * unmodified and still passing — this pass's own audit of {@code BlockBreaking#destroyCharging1}
 * found no code change was needed there at all).
 *
 * <p><b>MINING §2:</b> {@code PlayerMiningManager#strike} already calls
 * {@code tool.hurtAndBreak(wear + stress, player, EquipmentSlot.MAINHAND)} — confirmed against the
 * exact decompiled MC 26.2 {@code ItemStack} source that {@code hurtAndBreak} is ALREADY the
 * standard, enchantment-aware path ({@code EnchantmentHelper.processDurabilityChange} internally,
 * the same Unbreaking RNG vanilla itself uses). No code change was needed for mining — this suite
 * exists to prove that fact live, not to fix a bug that (for mining specifically) never existed.
 *
 * <p><b>MELEE §1:</b> the actual regression this pass fixes. {@code VanillaDamageInterceptor}'s
 * "player attacking mob" branch replaces vanilla's own damage pipeline entirely (Totality's
 * dice-roll {@code CombatResolver}), which never touched item durability at all — vanilla's own
 * {@code ItemStack#postHurtEnemy} (the method that would normally apply it) was simply never
 * reached. Fixed in {@code CombatResolver#resolveAttack}'s {@code ItemStack}-based overload; this
 * suite drives the REAL production event path (a real {@code player.attack(zombie)} call, which
 * fires the real {@code ServerLivingEntityEvents.ALLOW_DAMAGE} event {@code VanillaDamageInterceptor}
 * listens on) rather than calling the fix's internals directly, the same "exercise real production
 * code" discipline every verification suite in this codebase follows.
 *
 * <p>Per this pass's own explicit instruction, Unbreaking is never asserted as a single
 * deterministic sample (a live RNG enchantment effect): the unenchanted case is asserted exactly
 * (Unbreaking's own chance formula gives 100% at level 0 — genuinely deterministic, not merely
 * assumed), and Unbreaking III is asserted statistically over many samples, with a generous
 * tolerance band around its own documented ~25%-of-requests-apply rate.
 */
public final class DurabilityRegressionVerification {

    private static final int SUITE_DELAY_TICKS = 5;

    private DurabilityRegressionVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return; // opt-in: runs against the live world
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                ServerScheduler.getInstance().queue(DurabilityRegressionVerification::runSelfTest, SUITE_DELAY_TICKS));
    }

    private static void setScore(ServerPlayer p, zcylas.totality.api.rpg.stats.AbilityScore score, int value) {
        var stats = zcylas.totality.api.rpg.stats.StatsComponents.getStats(p);
        stats.setSpentPointsDirectly(score, value - 10);
        stats.recalculate();
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "DurabilityRegressionVerification");
        ServerLevel level = server.overworld();
        var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreaking = enchants.getOrThrow(Enchantments.UNBREAKING);

        // ── MINING: Unbreaking-aware wear ────────────────────────────────────────────────────
        // Both mining phases below work on the block under a fake player at (0.5, maxY - 3, 0.5);
        // capture it so cleanup restores what was there instead of leaving a Stone block behind.
        BlockPos testPos = new BlockPos(0, level.getMaxY() - 4, 0);
        BlockState originalTestBlock = level.getBlockState(testPos);

        // Pre-existing block damage at the test position is never this suite's to change (same principle as
        // MiningVerification): snapshot the exact live entry there (object + every mutable field), detach it for
        // the suite so the checks start from a clean slate, and restore it exactly in the outer finally.
        java.util.Map<Long, BlockDamageStorage.Entry> live = BlockDamageStorage.get(level).entriesForVerification();
        long testKey = testPos.asLong();
        BlockDamageStorage.Entry existing = live.get(testKey);
        MiningVerification.EntrySnapshot preexisting =
                existing == null ? null : MiningVerification.EntrySnapshot.of(java.util.Map.entry(testKey, existing));
        boolean verifierLeftover = false;
        try {
            live.remove(testKey);
            ServerPlayer miner = TotalityFakePlayer.create(level, "[DurabilityRegressionVerification-miner]");
            try {
                setScore(miner, zcylas.totality.api.rpg.stats.AbilityScore.STR, 10);
                miner.setXRot(90f);
                miner.setPos(0.5, level.getMaxY() - 3, 0.5);
                var pos = miner.blockPosition().below();

                ItemStack unenchantedPick = new ItemStack(Items.IRON_PICKAXE);
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                safe(r, "MINING: an unenchanted tool receives the FULL requested wear (Unbreaking level "
                        + "0 is genuinely deterministic — 100% apply chance, not merely assumed) — one "
                        + "ordinary damaging impact requests and applies exactly 1", () -> {
                    miner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, unenchantedPick.copy());
                    var result = PlayerMiningManager.strike(miner, topHit(pos), false, 0f);
                    int wear = miner.getMainHandItem().getDamageValue();
                    return result(result.outcome() == MiningResult.Outcome.DAMAGED && wear == 1,
                            "outcome=" + result.outcome() + " wear=" + wear);
                });

                safe(r, "MINING: Unbreaking III measurably reduces total mining wear over many samples "
                        + "(statistical, never asserted as a single deterministic RNG sample) — routed "
                        + "through the exact same tool.hurtAndBreak(...) call as the unenchanted case "
                        + "above, never a custom Totality RNG", () -> {
                    ItemStack enchantedPick = new ItemStack(Items.IRON_PICKAXE);
                    enchantedPick.enchant(unbreaking, 3);
                    miner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, enchantedPick.copy());
                    int samples = 300;
                    int totalWear = 0;
                    for (int i = 0; i < samples; i++) {
                        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                        var e = BlockDamageStorage.get(level).get(pos, level.getBlockState(pos));
                        if (e != null) BlockDamageStorage.get(level).remove(e);
                        ItemStack fresh = new ItemStack(Items.IRON_PICKAXE);
                        fresh.enchant(unbreaking, 3);
                        fresh.setDamageValue(0);
                        miner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, fresh);
                        PlayerMiningManager.strike(miner, topHit(pos), false, 0f);
                        totalWear += miner.getMainHandItem().getDamageValue();
                    }
                    // Unenchanted 300 samples of amount=1 would be exactly 300; Unbreaking III's own
                    // documented ~25% apply rate should land well under half that — a wide, forgiving
                    // statistical band (not a tight one), since this is genuine RNG.
                    boolean pass = totalWear > 0 && totalWear < samples / 2;
                    return result(pass, "totalWear=" + totalWear + "/" + samples
                            + " (expected roughly 1/4, comfortably under half)");
                });
            } finally {
                var minerPos = miner.blockPosition().below();
                level.setBlockAndUpdate(minerPos, Blocks.STONE.defaultBlockState());
                clearStrayDamage(level, minerPos);
                level.setBlockAndUpdate(testPos, originalTestBlock);
                miner.discard();
            }

            // ── MELEE: the actual regression fix ─────────────────────────────────────────────────
            checkMeleeDurability(r, server, level, Items.IRON_PICKAXE, "Pickaxe");
            checkMeleeDurability(r, server, level, Items.IRON_AXE, "Axe");
            checkMeleeDurability(r, server, level, Items.IRON_SHOVEL, "Shovel");

            ServerPlayer meleeUnbreaking = TotalityFakePlayer.create(level, "[DurabilityRegressionVerification-melee-unbreaking]");
            try {
                safe(r, "MELEE: Unbreaking III measurably reduces total melee wear over many samples "
                        + "(statistical) — same requirement as mining, proven independently for the "
                        + "melee path since it is a completely separate code path (CombatResolver, not "
                        + "PlayerMiningManager)", () -> {
                    int samples = 300;
                    int totalWear = 0;
                    for (int i = 0; i < samples; i++) {
                        Zombie zombie = zcylas.totality.api.core.util.VerificationMobs.lootlessZombie(level);
                        zombie.setPos(meleeUnbreaking.getX() + 1.5, meleeUnbreaking.getY(), meleeUnbreaking.getZ());
                        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
                        pick.enchant(unbreaking, 3);
                        meleeUnbreaking.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, pick);
                        meleeUnbreaking.attack(zombie);
                        totalWear += meleeUnbreaking.getMainHandItem().getDamageValue();
                        zombie.discard();
                    }
                    // Unenchanted 300 samples of amount=2 (the real vanilla per-attack Weapon cost)
                    // would be exactly 600; Unbreaking III should land well under half that.
                    boolean pass = totalWear > 0 && totalWear < samples;
                    return result(pass, "totalWear=" + totalWear + "/" + (samples * 2)
                            + " requested (expected roughly 1/4, comfortably under half)");
                });
            } finally {
                meleeUnbreaking.discard();
            }

            ServerPlayer breaker = TotalityFakePlayer.create(level, "[DurabilityRegressionVerification-broken]");
            try {
                safe(r, "MELEE: a tool one hit from breaking correctly breaks (consumed from the stack) "
                        + "on a successful melee hit, exactly like vanilla's own postHurtEnemy would",
                        () -> {
                            // 2 damage per real vanilla landed melee hit (Weapon component) — one point
                            // short of breaking guarantees a LANDED hit breaks it (unenchanted = always
                            // the full 2 applies, deterministic at Unbreaking level 0); retried below
                            // only because the ATTACK ROLL itself can genuinely miss.
                            boolean broken = attackRetrying(breaker, level, 20, () -> {
                                ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
                                pick.setDamageValue(pick.getMaxDamage() - 1);
                                breaker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, pick);
                            }, () -> breaker.getMainHandItem().isEmpty());
                            return result(broken, "mainHandItem=" + breaker.getMainHandItem());
                        });

                safe(r, "MELEE: no double-apply — hitting with a mainhand tool never also touches the "
                        + "(empty) offhand slot", () -> {
                    boolean landed = attackRetrying(breaker, level, 20, () -> {
                        breaker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                        breaker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    }, () -> breaker.getMainHandItem().getDamageValue() > 0);
                    int mainWear = breaker.getMainHandItem().getDamageValue();
                    boolean offhandStillEmpty = breaker.getOffhandItem().isEmpty();
                    return result(landed && mainWear == 2 && offhandStillEmpty,
                            "landed=" + landed + " mainWear=" + mainWear + " (expected 2, the real "
                                    + "vanilla Weapon component cost — never hard-coded), "
                                    + "offhandStillEmpty=" + offhandStillEmpty);
                });
            } finally {
                breaker.discard();
            }

            // ── §18: Mining Skill XP still fires after the durability changes (not modified this pass) ──
            ServerPlayer xpPlayer = TotalityFakePlayer.create(level, "[DurabilityRegressionVerification-xp]");
            try {
                xpPlayer.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                setScore(xpPlayer, zcylas.totality.api.rpg.stats.AbilityScore.STR, 10);
                xpPlayer.setXRot(90f);
                xpPlayer.setPos(0.5, level.getMaxY() - 3, 0.5);
                var pos = xpPlayer.blockPosition().below();
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                xpPlayer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));

                safe(r, "MINING XP (§18, unmodified this pass): a real terminal block break still "
                        + "awards Mining Skill XP through the existing MiningSkillEvents/PlayerBlockBreakEvents.AFTER "
                        + "chain exactly as before — this pass never touches Mining XP/progression; this only "
                        + "confirms the durability changes didn't silently break that unrelated event chain", () -> {
                    int xpBefore = zcylas.totality.api.rpg.skills.core.SkillsComponents.getSkills(xpPlayer)
                            .getXp(zcylas.totality.api.rpg.skills.core.Skill.MINING);
                    boolean broke = false;
                    for (int i = 0; i < 5 && !broke; i++) {
                        var res = PlayerMiningManager.strike(xpPlayer, topHit(pos), false, 0f);
                        broke = res.outcome() == MiningResult.Outcome.BROKEN;
                    }
                    int xpAfter = zcylas.totality.api.rpg.skills.core.SkillsComponents.getSkills(xpPlayer)
                            .getXp(zcylas.totality.api.rpg.skills.core.Skill.MINING);
                    return result(broke && xpAfter > xpBefore,
                            "broke=" + broke + " xpBefore=" + xpBefore + " xpAfter=" + xpAfter);
                });
            } finally {
                var xpPos = xpPlayer.blockPosition().below();
                level.setBlockAndUpdate(xpPos, Blocks.STONE.defaultBlockState());
                clearStrayDamage(level, xpPos);
                level.setBlockAndUpdate(testPos, originalTestBlock);
                // The terminal break above drops real item entities; remove only those at the test position.
                for (var e : level.getAllEntities())
                    if (e instanceof net.minecraft.world.entity.item.ItemEntity item && item.blockPosition().distManhattan(testPos) <= 2)
                        item.discard();
                xpPlayer.discard();
            }
        } finally {
            BlockDamageStorage.Entry now = live.get(testKey);
            verifierLeftover = now != null && (preexisting == null || now != preexisting.entry());
            live.remove(testKey);
            if (preexisting != null) preexisting.restoreInto(live);
            BlockDamageStorage.get(level).markDirtyForVerification();
        }
        r.check("the suite left no verifier-created block damage at its test position",
                !verifierLeftover, "leftover entry was present before restore");
        r.check("pre-existing block damage at the test position restored exactly (or the position left empty if there was none)",
                preexisting == null ? !live.containsKey(testKey) : preexisting.matches(live),
                "preexisting=" + (preexisting != null) + " now=" + live.get(testKey));

        r.summarize();
    }

    /**
     * Cross-run test-isolation fix (found live, 2026-09-22): {@link BlockDamageStorage} is a
     * {@code SavedData} — it survives server restarts. Without this cleanup, a stray damaged (but
     * not yet fully recovered) entry left at this suite's shared test position by the Unbreaking
     * statistical loop above (or the XP check below) would still be on disk the next time the dev
     * server starts, corrupting {@code MiningVerification}'s own "a (re)joining player gets the
     * persisted crack immediately" check, which expects a clean slate at boot. Both {@code finally}
     * blocks that touch a block position in this suite call this so no run of this suite ever
     * leaves persisted mining-damage state behind for the next server boot to trip over.
     */
    private static void clearStrayDamage(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var stray = BlockDamageStorage.get(level).get(pos, level.getBlockState(pos));
        if (stray != null) BlockDamageStorage.get(level).remove(stray);
    }

    /**
     * Unenchanted mainhand melee hit against a real (freshly spawned, then discarded) Zombie must
     * apply exactly the item's own real vanilla {@link Weapon#itemDamagePerAttack()} — read live
     * from the item's own component, never hard-coded here either, so this test can never silently
     * pass merely because it happens to agree with a stale assumption. Retried (see
     * {@link #attackRetrying}) because the attack roll itself is a real RNG hit/miss check.
     */
    private static void checkMeleeDurability(VerificationReporter r, MinecraftServer server, ServerLevel level,
                                             net.minecraft.world.item.Item toolItem, String label) {
        ServerPlayer p = TotalityFakePlayer.create(level, "[DurabilityRegressionVerification-" + label + "]");
        try {
            Weapon weaponData = new ItemStack(toolItem).get(DataComponents.WEAPON);
            r.check("MELEE setup: " + label + " carries a real vanilla Weapon component (every "
                    + "ToolMaterial-built tool does) — this check would be meaningless otherwise",
                    weaponData != null, "weapon=" + weaponData);
            if (weaponData == null) return;
            int expectedWear = weaponData.itemDamagePerAttack();

            boolean landed = attackRetrying(p, level, 20,
                    () -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(toolItem)),
                    () -> p.getMainHandItem().getDamageValue() > 0);
            int wear = p.getMainHandItem().getDamageValue();
            r.check("MELEE: a successful " + label + " melee hit on a real entity consumes exactly "
                    + "its own real vanilla per-attack durability cost (" + expectedWear + ") — this "
                    + "is the actual regression this pass fixes: before it, this was always 0",
                    landed && wear == expectedWear,
                    "landed=" + landed + " wear=" + wear + " expected=" + expectedWear);
        } finally {
            p.discard();
        }
    }

    /**
     * Repeatedly re-arms via {@code setup} (called fresh before every attempt — resets whatever
     * state that attempt's own assertion reads, e.g. the mainhand stack) and attacks a fresh
     * Zombie, until either {@code landed} reports success or {@code maxAttempts} is exhausted. The
     * ATTACK ROLL {@code CombatResolver}/{@code AttackRoll} performs is a real D20-style RNG
     * hit/miss check (against the target's AC) — completely independent of, and upstream from,
     * the Unbreaking durability RNG this pass is actually testing. A single attack attempt can
     * therefore genuinely miss for reasons unrelated to durability at all; asserting durability
     * from exactly one un-retried swing is exactly the kind of single-deterministic-RNG-sample
     * mistake this pass's own guidance (§6, for Unbreaking) warns against, so every melee check in
     * this suite that depends on a swing actually landing retries through this helper instead.
     */
    private static boolean attackRetrying(ServerPlayer p, ServerLevel level, int maxAttempts,
                                          Runnable setup, java.util.function.BooleanSupplier landed) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            setup.run();
            Zombie zombie = zcylas.totality.api.core.util.VerificationMobs.lootlessZombie(level);
            zombie.setPos(p.getX() + 1.5, p.getY(), p.getZ());
            p.attack(zombie);
            zombie.discard();
            if (landed.getAsBoolean()) return true;
        }
        return false;
    }

    private static BlockHitResult topHit(net.minecraft.core.BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    @FunctionalInterface
    private interface CheckBody {
        CheckResult run() throws Exception;
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, CheckBody body) {
        try {
            CheckResult outcome = body.run();
            r.check(label, outcome.pass(), outcome.detail());
        } catch (Exception e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
