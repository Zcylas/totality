package zcylas.totality.api.rpg.resources.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.resources.ResourceTarget;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;
import zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources;
import zcylas.totality.init.items.FoodItems;
import zcylas.totality.networking.resource.BaselineResourceLifecycleEvents;
import zcylas.totality.server.TotalityFakePlayer;

import java.lang.reflect.Method;

/**
 * Dev-environment-gated self-test for the Food 0-100 authority — original pass 2026-09-17, corrected
 * 2026-09-17 (stale-mirror fix, variable-maximum support, sprint-gate removal, starvation-damage
 * suppression, Cake/Saturation-effect authority holes closed). Exercises the REAL production
 * join-time migration, baseline grant reconciliation, {@code TotalityFoodItem}'s real
 * {@code finishUsingItem}, and every vanilla mutation path this pass intercepts, against a real
 * {@code ServerPlayer} via {@link TotalityFakePlayer} — mirrors
 * {@code HealthRecoveryDiceResourceVerification}'s established pattern. See
 * {@code TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md}'s
 * 2026-09-17 correction section.
 *
 * <p>{@link TotalityFakePlayer} never fires {@code ServerPlayConnectionEvents.JOIN}, so this calls
 * {@code migrateLegacyIfAbsent}/{@code PlayerBaselineResources.reconcile} directly. Pure clamping/
 * formatter/definition-shape/mirror-projection math is covered by {@code FoodResourceDefinitionTest}
 * (plain JUnit); this class covers only what requires a real {@code ServerPlayer}/{@code ItemStack}/
 * vanilla-method lifecycle.
 *
 * <p>Registration is gated on {@link VerificationReporter#isDevEnvironment()} — a complete no-op in
 * a production build.
 */
public final class FoodSystemVerification {

    private static final int SUITE_DELAY_TICKS = 5;

    private FoodSystemVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return; // opt-in: runs against the live world
        ServerLifecycleEvents.SERVER_STARTED.register(FoodSystemVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(FoodSystemVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static long queryFood(ServerPlayer player) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.FOOD);
        if (!(result instanceof ResourceQueryResult.Success success)) {
            throw new AssertionError("totality:food query failed: " + result);
        }
        return success.snapshot().currentUnits();
    }

    private static long queryFoodMax(ServerPlayer player) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.FOOD);
        if (!(result instanceof ResourceQueryResult.Success success)) {
            throw new AssertionError("totality:food query failed: " + result);
        }
        return success.snapshot().maximumUnits();
    }

    private static void adminSet(ServerPlayer player, long value) {
        PlayerResourceService.INSTANCE.set(player, ResourceTarget.scalar(PlayerResourceIds.FOOD, value),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ADMIN_COMMAND)));
    }

    /**
     * Reads vanilla {@code FoodData}'s private {@code exhaustionLevel} field — there is no public
     * getter (only {@link net.minecraft.world.food.FoodData#addExhaustion}, which writes). Used only
     * by the REAL ACTIVITY checks below to observe that real production activity (not a direct
     * {@code addExhaustion} injection) actually moved this field, exactly as {@code
     * hasEnoughFoodToDoExhaustiveManoeuvres()} elsewhere in this file is already read via reflection.
     */
    private static float readExhaustion(ServerPlayer player) throws ReflectiveOperationException {
        java.lang.reflect.Field field = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
        field.setAccessible(true);
        return field.getFloat(player.getFoodData());
    }

    /** How far {@link #findSafeAirScratchPosition} searches horizontally around its preferred position. */
    private static final int SCRATCH_SEARCH_RADIUS = 4;

    /**
     * {@code pos} itself and its 6 face-adjacent neighbors must all be air — never merely "whatever
     * happens to be there gets overwritten and restored." This check runs against the REAL,
     * persistent overworld (the Cake check needs a real {@code CakeBlock#eat} call to fire
     * {@code CakeBlockEatAuthorityMixin}'s redirect, and no isolated/nonpersistent test level exists
     * in this codebase — see the Cake check's own Javadoc), so it must never overwrite real content,
     * including a block entity or anything a neighbor-aware effect might read.
     */
    private static boolean isSafeAirScratch(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return false;
        for (Direction dir : Direction.values()) {
            if (!level.getBlockState(pos.relative(dir)).isAir()) return false;
        }
        return true;
    }

    /**
     * {@code preferred} if it (and its immediate neighbors) are already air, else the first air
     * position found in a small bounded horizontal search around it, else {@code null} — meaning no
     * safe position was found nearby and the caller must skip its world-touching check entirely
     * rather than overwrite real content. Never searches vertically or expands beyond
     * {@link #SCRATCH_SEARCH_RADIUS}: a small, bounded, predictable search, not an unbounded scan.
     */
    private static BlockPos findSafeAirScratchPosition(ServerLevel level, BlockPos preferred) {
        if (isSafeAirScratch(level, preferred)) return preferred;
        for (int dx = -SCRATCH_SEARCH_RADIUS; dx <= SCRATCH_SEARCH_RADIUS; dx++) {
            for (int dz = -SCRATCH_SEARCH_RADIUS; dz <= SCRATCH_SEARCH_RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = preferred.offset(dx, 0, dz);
                if (isSafeAirScratch(level, candidate)) return candidate;
            }
        }
        return null;
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "FoodSystemVerification");
        ServerLevel level = server.overworld();

        // Diagnostic-only (2026-09-22 Cake/world-mutation bugfix, corrected 2026-09-22 review
        // pass): an OLDER, pre-fix run of the Cake check below used to invoke CakeBlock#eat at a
        // TotalityFakePlayer's default, never-explicitly-set position — vanilla's own default
        // entity position, exactly BlockPos.ZERO (0,0,0), the WORLD ORIGIN, not necessarily the
        // configured world spawn point — which really did write a permanent Cake block into the
        // world there. The Cake check itself is now fixed to use a dedicated, validated-air
        // scratch position instead (see below), so this cannot happen again.
        //
        // This deliberately does NOT automatically delete/replace anything at BlockPos.ZERO: there
        // is no persistent migration marker distinguishing "a pre-fix run's leftover Cake" from "a
        // real Cake a player has since legitimately placed at that exact position" — an automatic
        // deletion here would be unsafe and would run on every single dev-server start forever
        // (never truly "one-time"), silently destroying legitimate future content. The one dev
        // world found contaminated during the original bugfix's validation was already cleaned up
        // by hand at that time; no ongoing automatic remediation is needed or performed. This is a
        // read-only diagnostic only — it logs, and touches nothing.
        if (level.getBlockState(BlockPos.ZERO).is(Blocks.CAKE)) {
            Totality.LOGGER.warn("[FoodSystemVerification] A Cake block is present at BlockPos.ZERO "
                    + "(0,0,0), the world origin. This is a harmless diagnostic, not an error: it may "
                    + "be real, legitimate player content, or — if this world was used before the "
                    + "2026-09-22 Cake/world-mutation bugfix — a leftover from that now-fixed bug. "
                    + "Nothing was changed; if it needs cleaning up, do so manually.");
        }

        // ── MIGRATION ────────────────────────────────────────────────────────────────────────
        ServerPlayer migration = TotalityFakePlayer.create(level, "[FoodSystemVerification-migration]");
        try {
            migration.getFoodData().setFoodLevel(14);

            safe(r, "MIGRATION: join-time migration: vanilla foodLevel 14/20 imports as exactly "
                    + "70/100 (x5, never reinterpreted as 14/100)", () -> {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(migration);
                long food = queryFood(migration);
                return result(food == 70, "food=" + food);
            });

            safe(r, "MIGRATION: idempotent — re-running it after vanilla's own field is externally "
                    + "changed does not re-import or rescale the already-migrated true value", () -> {
                migration.getFoodData().setFoodLevel(3); // simulate vanilla having since drifted
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(migration);
                long food = queryFood(migration);
                return result(food == 70, "food=" + food + " (must remain 70, not re-import 3*5=15)");
            });

            safe(r, "MIGRATION: reconnect/dimension-change-style reconciliation does not rescale an "
                    + "already-granted Food value", () -> {
                PlayerBaselineResources.reconcile(migration);
                long food = queryFood(migration);
                return result(food == 70, "food=" + food);
            });
        } finally {
            migration.discard();
        }

        ServerPlayer fresh = TotalityFakePlayer.create(level, "[FoodSystemVerification-fresh]");
        try {
            safe(r, "MIGRATION: a brand-new player (vanilla default foodLevel=20) migrates to exactly "
                    + "100/100 — new players begin in the intended current Food domain", () -> {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(fresh);
                PlayerBaselineResources.reconcile(fresh);
                long food = queryFood(fresh);
                return result(food == 100, "food=" + food);
            });
        } finally {
            fresh.discard();
        }

        // ── STALE-MIRROR BUG AND ITS FIX ─────────────────────────────────────────────────────
        ServerPlayer mirror = TotalityFakePlayer.create(level, "[FoodSystemVerification-mirror]");
        try {
            BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(mirror);
            PlayerBaselineResources.reconcile(mirror);

            safe(r, "MIRROR BUG: a direct authoritative mutation with no vanilla call site (admin "
                    + "set) does NOT by itself refresh vanilla's mirror — proves the bug this pass "
                    + "fixes exists at the mutation layer (fixed by the tick-based reconciler below, "
                    + "not by this mutation call itself)", () -> {
                int before = mirror.getFoodData().getFoodLevel(); // 20, from the 100/100 grant above
                adminSet(mirror, 40);
                long food = queryFood(mirror);
                int mirrorAfterMutationAlone = mirror.getFoodData().getFoodLevel();
                boolean pass = food == 40 && mirrorAfterMutationAlone == before;
                return result(pass, "food=" + food + ", mirror stayed at " + mirrorAfterMutationAlone
                        + " (expected still-stale " + before + ")");
            });

            safe(r, "MIRROR FIX: FoodVanillaCompatibilityBridge.resyncMirrorIfStale (the function "
                    + "FoodMirrorServerTick calls once per player every real server tick) corrects "
                    + "the stale mirror to the proportional projection of the true value (40/100 -> "
                    + "8/20)", () -> {
                FoodVanillaCompatibilityBridge.resyncMirrorIfStale(mirror);
                int mirrorValue = mirror.getFoodData().getFoodLevel();
                return result(mirrorValue == 8, "mirror=" + mirrorValue);
            });

            safe(r, "MIRROR: resyncMirrorIfStale is a no-op (never touches the authoritative value) "
                    + "when the mirror is already correct", () -> {
                long before = queryFood(mirror);
                FoodVanillaCompatibilityBridge.resyncMirrorIfStale(mirror);
                long after = queryFood(mirror);
                return result(before == after, "before=" + before + ", after=" + after);
            });
        } finally {
            mirror.discard();
        }

        // ── EATING (TotalityFoodItem) ────────────────────────────────────────────────────────
        ServerPlayer eater = TotalityFakePlayer.create(level, "[FoodSystemVerification-eater]");
        try {
            BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(eater);
            PlayerBaselineResources.reconcile(eater);
            adminSet(eater, 40);
            FoodVanillaCompatibilityBridge.resyncMirrorIfStale(eater);

            safe(r, "EATING: Pizza Margherita Slice restores exactly 6 Food via the real "
                    + "TotalityFoodItem.finishUsingItem — 2026-09-17 Pizza/Saturation correction: "
                    + "finishUsingItem now explicitly resyncs the mirror itself (needed so the "
                    + "temporary Saturation clamp below is evaluated against the fresh, post-eating "
                    + "ceiling, not a stale one), so the mirror is already correct immediately, before "
                    + "FoodMirrorServerTick's own per-tick reconciliation would even run", () -> {
                ItemStack stack = new ItemStack(FoodItems.PIZZA_MARGHERITA_SLICE);
                FoodItems.PIZZA_MARGHERITA_SLICE.finishUsingItem(stack, eater.level(), eater);
                long food = queryFood(eater);
                int vanillaMirrorAfter = eater.getFoodData().getFoodLevel();
                boolean pass = food == 46 && vanillaMirrorAfter == 9;
                return result(pass, "food=" + food + " (expected 46), vanillaMirror=" + vanillaMirrorAfter
                        + " (expected 9, round-half-up of 46/100*20=9.2 — already correct, not stale)");
            });

            safe(r, "EATING: a redundant resyncMirrorIfStale call after eating is a true no-op — the "
                    + "mirror finishUsingItem already wrote is already correct, so nothing changes",
                    () -> {
                int before = eater.getFoodData().getFoodLevel();
                FoodVanillaCompatibilityBridge.resyncMirrorIfStale(eater);
                int after = eater.getFoodData().getFoodLevel();
                return result(before == 9 && after == 9, "mirror " + before + " -> " + after + " (expected 9 -> 9)");
            });

            safe(r, "EATING: Pizza Margherita restores exactly 48 Food, clamped at the resolved "
                    + "maximum rather than overflowing (46 + 48 = 94, well under the 100 baseline "
                    + "here)", () -> {
                ItemStack stack = new ItemStack(FoodItems.PIZZA_MARGHERITA);
                FoodItems.PIZZA_MARGHERITA.finishUsingItem(stack, eater.level(), eater);
                long food = queryFood(eater);
                return result(food == 94, "food=" + food);
            });

            safe(r, "EATING: eating a Pizza Slice while already at exactly the resolved maximum "
                    + "keeps Food at that maximum (no overflow) while the item is still consumed by "
                    + "finishUsingItem", () -> {
                adminSet(eater, queryFoodMax(eater));
                ItemStack stack = new ItemStack(FoodItems.PIZZA_MARGHERITA_SLICE);
                ItemStack afterUse = FoodItems.PIZZA_MARGHERITA_SLICE.finishUsingItem(stack, eater.level(), eater);
                long food = queryFood(eater);
                long max = queryFoodMax(eater);
                boolean consumed = afterUse.getCount() == 0 || afterUse.isEmpty();
                return result(food == max && consumed, "food=" + food + "/" + max + ", afterUseCount=" + afterUse.getCount());
            });

            safe(r, "EATING: authored consume durations: Pizza Margherita = 320 ticks (16.0s), Slice "
                    + "= 40 ticks (2.0s) — unchanged by this correction pass", () -> {
                int pizzaTicks = FoodItems.PIZZA_MARGHERITA.getUseDuration(new ItemStack(FoodItems.PIZZA_MARGHERITA), eater);
                int sliceTicks = FoodItems.PIZZA_MARGHERITA_SLICE.getUseDuration(new ItemStack(FoodItems.PIZZA_MARGHERITA_SLICE), eater);
                return result(pizzaTicks == 320 && sliceTicks == 40,
                        "pizzaTicks=" + pizzaTicks + ", sliceTicks=" + sliceTicks);
            });

            safe(r, "EATING: eating is always allowed regardless of current Food (canAlwaysEat)", () -> {
                boolean canEat = eater.canEat(true);
                return result(canEat, "canEat(true)=" + canEat);
            });

            safe(r, "EATING: existing nonzero Saturation is not accidentally destroyed by consuming a "
                    + "Totality food (2026-09-17 Pizza/Saturation correction, the actual regression this "
                    + "pass fixes): Food low (mirror 1), Saturation 8.0, eat a Slice — Saturation must "
                    + "follow the authored-addition/clamp rule (min(8.0+1.5, freshCeiling)), never reset "
                    + "to 0 the way vanilla's own zero-nutrition eat() used to", () -> {
                        adminSet(eater, 2);
                        eater.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(2, 100)); // 1
                        eater.getFoodData().setSaturation(8.0F);
                        ItemStack stack = new ItemStack(FoodItems.PIZZA_MARGHERITA_SLICE);
                        FoodItems.PIZZA_MARGHERITA_SLICE.finishUsingItem(stack, eater.level(), eater);
                        long food = queryFood(eater);
                        int mirrorAfter = eater.getFoodData().getFoodLevel();
                        float saturation = eater.getFoodData().getSaturationLevel();
                        boolean pass = food == 8 && mirrorAfter == 2 && saturation == 2.0F;
                        return result(pass, "food=" + food + " (expected 8), mirror=" + mirrorAfter + " (expected 2), "
                                + "saturation=" + saturation + " (expected 2.0 = min(8.0+1.5, 2) — NOT 0)");
                    });

            safe(r, "EATING: whole Pizza Margherita from empty Food/Saturation (2026-09-17 "
                    + "Pizza/Saturation correction, the task's own worked example): true Food 0/100, "
                    + "mirror 0/20, Saturation 0 -> eat Pizza -> true Food 48/100 (no double "
                    + "restoration), mirror proportional (10/20), temporary Saturation clamped to the "
                    + "FRESH post-eating ceiling (10.0), never 0 from a stale pre-eating mirror", () -> {
                        adminSet(eater, 0);
                        eater.getFoodData().setFoodLevel(0);
                        eater.getFoodData().setSaturation(0.0F);
                        ItemStack stack = new ItemStack(FoodItems.PIZZA_MARGHERITA);
                        FoodItems.PIZZA_MARGHERITA.finishUsingItem(stack, eater.level(), eater);
                        long food = queryFood(eater);
                        int mirrorAfter = eater.getFoodData().getFoodLevel();
                        float saturation = eater.getFoodData().getSaturationLevel();
                        boolean pass = food == 48 && mirrorAfter == 10 && saturation == 10.0F;
                        return result(pass, "food=" + food + " (expected 48), mirror=" + mirrorAfter
                                + " (expected 10), saturation=" + saturation
                                + " (expected 10.0 = min(0+12.0, 10) — NOT 0)");
                    });

            safe(r, "EATING: Slice from empty Food/Saturation: true Food 6/100, mirror proportional "
                    + "(1/20), temporary Saturation clamped to the fresh post-eating ceiling (1.0, not "
                    + "the full 1.5, and never 0)", () -> {
                        adminSet(eater, 0);
                        eater.getFoodData().setFoodLevel(0);
                        eater.getFoodData().setSaturation(0.0F);
                        ItemStack stack = new ItemStack(FoodItems.PIZZA_MARGHERITA_SLICE);
                        FoodItems.PIZZA_MARGHERITA_SLICE.finishUsingItem(stack, eater.level(), eater);
                        long food = queryFood(eater);
                        int mirrorAfter = eater.getFoodData().getFoodLevel();
                        float saturation = eater.getFoodData().getSaturationLevel();
                        boolean pass = food == 6 && mirrorAfter == 1 && saturation == 1.0F;
                        return result(pass, "food=" + food + " (expected 6), mirror=" + mirrorAfter
                                + " (expected 1), saturation=" + saturation
                                + " (expected 1.0 = min(0+1.5, 1))");
                    });
        } finally {
            eater.discard();
        }

        // ── VANILLA MUTATION PATHS ───────────────────────────────────────────────────────────
        ServerPlayer apple = TotalityFakePlayer.create(level, "[FoodSystemVerification-apple]");
        try {
            BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(apple);
            PlayerBaselineResources.reconcile(apple);
            adminSet(apple, 40);
            FoodVanillaCompatibilityBridge.resyncMirrorIfStale(apple);

            safe(r, "VANILLA PATH (ordinary FoodProperties eating): a real vanilla Apple (nutrition "
                    + "4) restores exactly 20 true Food (4 x 5) via FoodPropertiesEatAuthorityMixin, "
                    + "using the item's real intended nutrition rather than a measured mirror delta",
                    () -> {
                        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.APPLE);
                        stack.finishUsingItem(apple.level(), apple);
                        long food = queryFood(apple);
                        int vanillaMirror = apple.getFoodData().getFoodLevel();
                        boolean pass = food == 60 && vanillaMirror == 12;
                        return result(pass, "food=" + food + " (expected 60), vanillaMirror=" + vanillaMirror + " (expected 12)");
                    });
        } finally {
            apple.discard();
        }
        ServerPlayer exhaustion = TotalityFakePlayer.create(level, "[FoodSystemVerification-exhaustion]");
        try {
            BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(exhaustion);
            PlayerBaselineResources.reconcile(exhaustion);

            safe(r, "VANILLA PATH (exhaustion, at the world's actual current difficulty — 2026-09-17 "
                    + "review correction: no longer skipped on Peaceful, since Peaceful now depletes "
                    + "Food identically to every other difficulty): FoodDataExhaustionAuthorityMixin "
                    + "translates a vanilla exhaustion-threshold Food decrement into a real -5 on the "
                    + "true resource and re-syncs vanilla's own field to the corrected proportional "
                    + "mirror — see the dedicated PEACEFUL block below for the explicit, "
                    + "difficulty-forced proof of the new rule", () -> {
                adminSet(exhaustion, 70);
                exhaustion.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100)); // 14
                exhaustion.getFoodData().setSaturation(0.0F);
                exhaustion.getFoodData().addExhaustion(41.0F);
                exhaustion.getFoodData().tick(exhaustion);
                long food = queryFood(exhaustion);
                int vanillaMirror = exhaustion.getFoodData().getFoodLevel();
                boolean pass = food == 65 && vanillaMirror == 13;
                return result(pass, "food=" + food + " (expected 65), vanillaMirror=" + vanillaMirror + " (expected 13)");
            });

            safe(r, "VANILLA PATH (exhaustion, low positive Food, at the world's actual current "
                    + "difficulty): a small positive authoritative Food value (2) can still reach "
                    + "exactly 0 via one real exhaustion-threshold event — never stuck merely because "
                    + "its mirror was already quantized to a coarse interior bucket", () -> {
                adminSet(exhaustion, 2);
                exhaustion.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(2, 100)); // 1
                exhaustion.getFoodData().setSaturation(0.0F);
                exhaustion.getFoodData().addExhaustion(41.0F);
                exhaustion.getFoodData().tick(exhaustion);
                long food = queryFood(exhaustion);
                int vanillaMirror = exhaustion.getFoodData().getFoodLevel();
                boolean pass = food == 0 && vanillaMirror == 0;
                return result(pass, "food=" + food + " (expected 0), vanillaMirror=" + vanillaMirror + " (expected 0)");
            });

            safe(r, "STARVATION: Food reaching 0 does NOT cause vanilla's direct starvation HP "
                    + "damage (FoodDataStarvationDamageAuthorityMixin) — 80 real tick() calls at "
                    + "foodLevel 0 must never reduce Health", () -> {
                adminSet(exhaustion, 0);
                exhaustion.getFoodData().setFoodLevel(0);
                float healthBefore = exhaustion.getHealth();
                for (int i = 0; i < 85; i++) {
                    exhaustion.getFoodData().tick(exhaustion);
                }
                float healthAfter = exhaustion.getHealth();
                return result(healthAfter == healthBefore, "health " + healthBefore + " -> " + healthAfter);
            });
        } finally {
            exhaustion.discard();
        }

        // ── REAL ACTIVITY (2026-09-17 real-client correction) ───────────────────────────────────
        // The exhaustion checks above (and every check before them) inject exhaustion directly via
        // FoodData#addExhaustion, which only proves the DOWNSTREAM chain (existing exhaustion ->
        // Saturation -> Food -5). A real-client test found sustained real Survival sprinting/jumping
        // did not decrease Food at all, in both Peaceful and Normal — a gap these injected-exhaustion
        // checks cannot see, since they never exercise how real activity is SUPPOSED to reach
        // FoodData#addExhaustion in the first place. These checks instead call the real MC 26.2
        // production methods a genuine client's movement/jump packets ultimately drive —
        // ServerPlayer#checkMovementStatistics (called from ServerGamePacketListenerImpl after every
        // real ServerboundMovePlayerPacket) and ServerPlayer#jumpFromGround (called from real jump
        // physics) — never FoodData#addExhaustion directly.
        // Every check below gets its own fresh player. Exhaustion is stateful (checkMovementStatistics
        // adds to it, and it is only consumed by an interleaved tick() call), so reusing one player
        // across checks that each call checkMovementStatistics would let one check's unconsumed
        // exhaustion leak into the next and silently double-count — the exact mistake this comment
        // documents so it is never reintroduced.
        ServerPlayer invulnerabilityProbe = TotalityFakePlayer.create(level, "[FoodSystemVerification-invuln]");
        try {
            safe(r, "REAL ACTIVITY: a Survival player is not invulnerable, so ordinary hunger "
                    + "exhaustion is not gated off the way Creative correctly is "
                    + "(Player#causeFoodExhaustion's own vanilla gate)", () ->
                    result(!invulnerabilityProbe.getAbilities().invulnerable,
                            "abilities.invulnerable=" + invulnerabilityProbe.getAbilities().invulnerable + " (expected false)"));
        } finally {
            invulnerabilityProbe.discard();
        }

        {
            ServerPlayer mover = TotalityFakePlayer.create(level, "[FoodSystemVerification-sprintdistance]");
            try {
                safe(r, "REAL ACTIVITY (sprint-distance, real production method): a single real "
                        + "ServerPlayer#checkMovementStatistics(50,0,0) call — the exact method vanilla's "
                        + "own movement-packet handling calls, never FoodData#addExhaustion directly — "
                        + "accumulates real exhaustion for 50 blocks of real sprint distance (0.1 per "
                        + "block, vanilla's own rate)", () -> {
                            mover.setOnGround(true);
                            mover.setSprinting(true);
                            float before = readExhaustion(mover);
                            mover.checkMovementStatistics(50.0, 0.0, 0.0);
                            float after = readExhaustion(mover);
                            boolean pass = after > before && Math.abs(after - (before + 5.0F)) < 0.001F;
                            return result(pass, "exhaustion " + before + " -> " + after + " (expected +5.0 exactly, "
                                    + "0.1 x 50 blocks) via the real production checkMovementStatistics call");
                        });
            } finally {
                mover.discard();
            }
        }

        {
            ServerPlayer jumper = TotalityFakePlayer.create(level, "[FoodSystemVerification-jump]");
            try {
                safe(r, "REAL ACTIVITY (sprint-jump, real production method): a single real "
                        + "ServerPlayer#jumpFromGround() call while sprinting uses vanilla's own real "
                        + "sprint-jump exhaustion amount (0.2), never an invented amount", () -> {
                            jumper.setSprinting(true);
                            float before = readExhaustion(jumper);
                            jumper.jumpFromGround();
                            float after = readExhaustion(jumper);
                            boolean pass = Math.abs(after - (before + 0.2F)) < 0.001F;
                            return result(pass, "exhaustion " + before + " -> " + after + " (expected +0.2 exactly)");
                        });
            } finally {
                jumper.discard();
            }
        }

        {
            ServerPlayer jumper = TotalityFakePlayer.create(level, "[FoodSystemVerification-jump2]");
            try {
                safe(r, "REAL ACTIVITY (normal jump, real production method): a single real "
                        + "ServerPlayer#jumpFromGround() call while NOT sprinting uses vanilla's own real "
                        + "normal-jump exhaustion amount (0.05), per actual vanilla rules — never treated "
                        + "the same as a sprint-jump", () -> {
                            jumper.setSprinting(false);
                            float before = readExhaustion(jumper);
                            jumper.jumpFromGround();
                            float after = readExhaustion(jumper);
                            boolean pass = Math.abs(after - (before + 0.05F)) < 0.001F;
                            return result(pass, "exhaustion " + before + " -> " + after + " (expected +0.05 exactly)");
                        });
            } finally {
                jumper.discard();
            }
        }

        {
            ServerPlayer creative = TotalityFakePlayer.create(level, "[FoodSystemVerification-creative]");
            try {
                safe(r, "REAL ACTIVITY, Creative/invulnerable: the same real production methods "
                        + "(checkMovementStatistics + jumpFromGround) must still accumulate zero "
                        + "exhaustion for an invulnerable player, exactly like vanilla Creative — proving "
                        + "this correction does not make Creative accumulate exhaustion by accident", () -> {
                            creative.getAbilities().invulnerable = true;
                            creative.setOnGround(true);
                            creative.setSprinting(true);
                            creative.checkMovementStatistics(50.0, 0.0, 0.0);
                            creative.jumpFromGround();
                            float after = readExhaustion(creative);
                            return result(after == 0.0F, "exhaustion=" + after + " (expected 0.0, unchanged)");
                        });
            } finally {
                creative.discard();
            }
        }

        {
            ServerPlayer saturated = TotalityFakePlayer.create(level, "[FoodSystemVerification-saturated]");
            try {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(saturated);
                PlayerBaselineResources.reconcile(saturated);
                safe(r, "REAL ACTIVITY, Saturation > 0 (real production activity, no addExhaustion "
                        + "injection): 9 real checkMovementStatistics(5,0,0) calls interleaved with 9 "
                        + "real FoodData#tick calls — exactly how a real client's per-tick movement "
                        + "packets and ServerPlayer#doTick already interleave — cross the exhaustion "
                        + "threshold exactly once; Saturation must absorb it, Food must stay unchanged",
                        () -> {
                            adminSet(saturated, 70);
                            saturated.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100));
                            saturated.getFoodData().setSaturation(6.0F);
                            saturated.setOnGround(true);
                            saturated.setSprinting(true);
                            for (int i = 0; i < 9; i++) {
                                saturated.checkMovementStatistics(5.0, 0.0, 0.0);
                                saturated.getFoodData().tick(saturated);
                            }
                            long food = queryFood(saturated);
                            float saturationAfter = saturated.getFoodData().getSaturationLevel();
                            boolean pass = food == 70 && saturationAfter == 5.0F;
                            return result(pass, "food=" + food + " (expected 70, unchanged), saturation=" + saturationAfter
                                    + " (expected 5.0, absorbed one real activity-driven event)");
                        });
            } finally {
                saturated.discard();
            }
        }

        {
            ServerPlayer depleted = TotalityFakePlayer.create(level, "[FoodSystemVerification-depleted]");
            try {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(depleted);
                PlayerBaselineResources.reconcile(depleted);
                safe(r, "REAL ACTIVITY, Saturation == 0 (real production activity, the actual upstream "
                        + "proof): once Saturation is exhausted, the same real "
                        + "checkMovementStatistics+tick interleaving drains true Food by 5 and resyncs "
                        + "the mirror — proving real activity genuinely reaches FoodData#addExhaustion "
                        + "and the existing downstream translation, with no direct injection anywhere in "
                        + "this check", () -> {
                            adminSet(depleted, 70);
                            depleted.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100)); // 14
                            depleted.getFoodData().setSaturation(0.0F);
                            depleted.setOnGround(true);
                            depleted.setSprinting(true);
                            for (int i = 0; i < 9; i++) {
                                depleted.checkMovementStatistics(5.0, 0.0, 0.0);
                                depleted.getFoodData().tick(depleted);
                            }
                            long food = queryFood(depleted);
                            int vanillaMirror = depleted.getFoodData().getFoodLevel();
                            boolean pass = food == 65 && vanillaMirror == 13;
                            return result(pass, "food=" + food + " (expected 65), vanillaMirror=" + vanillaMirror
                                    + " (expected 13) — real Survival sprinting must deplete Food, exactly as the "
                                    + "real-client test expected");
                        });
            } finally {
                depleted.discard();
            }
        }

        {
            ServerPlayer lowFood = TotalityFakePlayer.create(level, "[FoodSystemVerification-lowfood]");
            try {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(lowFood);
                PlayerBaselineResources.reconcile(lowFood);
                safe(r, "REAL ACTIVITY, low positive Food (real production activity): a small positive "
                        + "authoritative Food value (2) can still reach exactly 0 via real sprint activity, "
                        + "never stuck merely because its mirror was already quantized", () -> {
                            adminSet(lowFood, 2);
                            lowFood.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(2, 100)); // 1
                            lowFood.getFoodData().setSaturation(0.0F);
                            lowFood.setOnGround(true);
                            lowFood.setSprinting(true);
                            for (int i = 0; i < 9; i++) {
                                lowFood.checkMovementStatistics(5.0, 0.0, 0.0);
                                lowFood.getFoodData().tick(lowFood);
                            }
                            long food = queryFood(lowFood);
                            int vanillaMirror = lowFood.getFoodData().getFoodLevel();
                            boolean pass = food == 0 && vanillaMirror == 0;
                            return result(pass, "food=" + food + " (expected 0), vanillaMirror=" + vanillaMirror + " (expected 0)");
                        });
            } finally {
                lowFood.discard();
            }
        }

        {
            ServerPlayer peacefulActivity = TotalityFakePlayer.create(level, "[FoodSystemVerification-peacefulactivity]");
            Difficulty activityOriginalDifficulty = level.getDifficulty();
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(peacefulActivity);
                PlayerBaselineResources.reconcile(peacefulActivity);
                safe(r, "REAL ACTIVITY on PEACEFUL (real production activity, proves §34's fix and "
                        + "the upstream chain together): the same real checkMovementStatistics+tick "
                        + "interleaving must still deplete Food once Saturation is 0, exactly as on "
                        + "every other difficulty — a real Survival player sprinting on Peaceful must "
                        + "not be silently exempt from physical Food depletion", () -> {
                            adminSet(peacefulActivity, 70);
                            peacefulActivity.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100));
                            peacefulActivity.getFoodData().setSaturation(0.0F);
                            peacefulActivity.setOnGround(true);
                            peacefulActivity.setSprinting(true);
                            for (int i = 0; i < 9; i++) {
                                peacefulActivity.checkMovementStatistics(5.0, 0.0, 0.0);
                                peacefulActivity.getFoodData().tick(peacefulActivity);
                            }
                            long food = queryFood(peacefulActivity);
                            int vanillaMirror = peacefulActivity.getFoodData().getFoodLevel();
                            boolean pass = food == 65 && vanillaMirror == 13;
                            return result(pass, "food=" + food + " (expected 65), vanillaMirror=" + vanillaMirror
                                    + " (expected 13) on a forced Difficulty.PEACEFUL world");
                        });
            } finally {
                server.setDifficulty(activityOriginalDifficulty, true);
                peacefulActivity.discard();
            }
        }

        {
            ServerPlayer staminaProbe = TotalityFakePlayer.create(level, "[FoodSystemVerification-staminaprobe]");
            try {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(staminaProbe);
                PlayerBaselineResources.reconcile(staminaProbe);
                safe(r, "REAL ACTIVITY: Stamina is untouched by this correction — none of the real "
                        + "production activity calls above spend, restore, or otherwise reference Stamina; "
                        + "Food depletion is never coupled to Stamina consumption", () -> {
                            int staminaBefore = zcylas.totality.api.rpg.stamina.PlayerStaminaManager.getStamina(staminaProbe);
                            staminaProbe.setOnGround(true);
                            staminaProbe.setSprinting(true);
                            staminaProbe.checkMovementStatistics(50.0, 0.0, 0.0);
                            staminaProbe.jumpFromGround();
                            int staminaAfter = zcylas.totality.api.rpg.stamina.PlayerStaminaManager.getStamina(staminaProbe);
                            return result(staminaBefore == staminaAfter,
                                    "stamina " + staminaBefore + " -> " + staminaAfter + " (expected unchanged)");
                        });
            } finally {
                staminaProbe.discard();
            }
        }

        ServerPlayer sprint = TotalityFakePlayer.create(level, "[FoodSystemVerification-sprint]");
        try {
            BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(sprint);
            PlayerBaselineResources.reconcile(sprint);

            safe(r, "SPRINT: PlayerFoodSprintGateAuthorityMixin makes "
                    + "hasEnoughFoodToDoExhaustiveManoeuvres() always true, even at 0 Food — Stamina "
                    + "(untouched by this pass) remains the only sprint-endurance authority", () -> {
                adminSet(sprint, 0);
                sprint.getFoodData().setFoodLevel(0);
                Method method = Player.class.getDeclaredMethod("hasEnoughFoodToDoExhaustiveManoeuvres");
                method.setAccessible(true);
                boolean canDoManoeuvres = (boolean) method.invoke(sprint);
                return result(canDoManoeuvres, "hasEnoughFoodToDoExhaustiveManoeuvres()=" + canDoManoeuvres + " at 0 Food");
            });
        } finally {
            sprint.discard();
        }

        // Playtest-reported bug fix (2026-09-22), hardened in the 2026-09-22 review-correction
        // pass: this check used to invoke the REAL static CakeBlock#eat(LevelAccessor, BlockPos,
        // BlockState, Player) via reflection against the REAL, persistent overworld ServerLevel,
        // at cake.blockPosition() — a TotalityFakePlayer's position, which is never explicitly set
        // and therefore stays at vanilla's own default entity position, exactly BlockPos.ZERO
        // (0,0,0) — the WORLD ORIGIN, not necessarily the configured world spawn point (Entity's
        // constructor: "this.setPos(0.0, 0.0, 0.0)", confirmed against the real decompiled 26.2
        // source — never merely assumed). Vanilla's real eat() unconditionally calls
        // level.setBlock(pos, state.setValue(BITES, bites+1), 3) for a fresh (bites=0) cake state
        // — a genuine, permanent world mutation, not a simulation — which is exactly why a real
        // Cake block was appearing at the world origin.
        //
        // This still needs to invoke the REAL CakeBlock#eat (proving CakeBlockEatAuthorityMixin's
        // redirect actually fires in the woven bytecode, not merely that the bridge function it
        // calls works in isolation), against a real ServerLevel (this codebase has no
        // isolated/nonpersistent test-level facility — e.g. GameTest — to run it against instead;
        // building one is out of scope for this fix). Real-world scratch testing therefore still
        // dirties one real, persistent chunk even when fully successful (a mutation is written,
        // then reverted, in the same tick — the chunk is still marked dirty and will be re-saved).
        // This is disclosed honestly, not claimed to be zero-side-effect.
        //
        // Restoring the position's prior BlockState is NOT enough on its own if that position ever
        // turns out to hold something a plain BlockState-equality restore can't fully account for
        // (a block entity's own NBT, for one). So instead: the scratch position (and its 6
        // face-adjacent neighbors) must independently be verified as AIR before this check ever
        // runs; if the preferred position isn't safe, a small bounded search
        // (findSafeAirScratchPosition) looks for an alternative; if none is found nearby, this
        // check is skipped/reported as a clear failure rather than ever overwriting real content.
        // Cleanup always restores AIR (never a captured "original" BlockState — the precondition
        // above already guarantees the original was air), in a try/finally, so no observable
        // mutation survives this self-test regardless of outcome, including an exception.
        BlockPos preferredCakeScratchPos = new BlockPos(16, level.getMaxY() - 5, 16);
        BlockPos cakeScratchPos = findSafeAirScratchPosition(level, preferredCakeScratchPos);
        ServerPlayer cake = TotalityFakePlayer.create(level, "[FoodSystemVerification-cake]");
        try {
            BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(cake);
            PlayerBaselineResources.reconcile(cake);
            adminSet(cake, 40);
            cake.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(40, 100)); // 8 — needsFood() must be true for eat() to proceed

            if (cakeScratchPos == null) {
                r.check("VANILLA PATH (Cake): a safe, validated-AIR scratch position exists near "
                                + preferredCakeScratchPos + " to run this check without touching real content",
                        false, "no safe AIR position (itself and all 6 neighbors air) found within "
                                + SCRATCH_SEARCH_RADIUS + " blocks — skipped the Cake behavior check "
                                + "rather than overwrite real content");
            } else {
                safe(r, "VANILLA PATH (Cake): CakeBlockEatAuthorityMixin closes the known Cake authority "
                        + "hole — eating a cake slice (2 nutrition) restores exactly 10 true Food (2 x 5) "
                        + "and refreshes the mirror inline, the same as ordinary FoodProperties eating", () -> {
                    try {
                        BlockState cakeState = Blocks.CAKE.defaultBlockState();
                        Method eatMethod = CakeBlock.class.getDeclaredMethod(
                                "eat", LevelAccessor.class, BlockPos.class, BlockState.class, Player.class);
                        eatMethod.setAccessible(true);
                        eatMethod.invoke(null, level, cakeScratchPos, cakeState, cake);
                        long food = queryFood(cake);
                        int mirrorValue = cake.getFoodData().getFoodLevel();
                        boolean pass = food == 50 && mirrorValue == 10;
                        return result(pass, "food=" + food + " (expected 50), mirror=" + mirrorValue + " (expected 10)");
                    } finally {
                        level.setBlock(cakeScratchPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                });

                safe(r, "VANILLA PATH (Cake): the scratch position is restored to AIR afterward, and "
                        + "BlockPos.ZERO (0,0,0), the world origin — a TotalityFakePlayer's default, "
                        + "never-explicitly-set position — was never touched by this check at all", () -> {
                    boolean scratchRestored = level.getBlockState(cakeScratchPos).isAir();
                    boolean originUntouched = !level.getBlockState(BlockPos.ZERO).is(Blocks.CAKE);
                    return result(scratchRestored && originUntouched,
                            "scratchRestored=" + scratchRestored + ", originUntouched=" + originUntouched);
                });
            }
        } finally {
            cake.discard();
        }

        ServerPlayer saturationEffect = TotalityFakePlayer.create(level, "[FoodSystemVerification-saturationeffect]");
        try {
            BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(saturationEffect);
            PlayerBaselineResources.reconcile(saturationEffect);
            adminSet(saturationEffect, 40);
            saturationEffect.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(40, 100)); // 8

            safe(r, "VANILLA PATH (Saturation effect): SaturationMobEffectEatAuthorityMixin closes "
                    + "the known Saturation-mob-effect authority hole — MobEffects.SATURATION at "
                    + "amplifier 0 calls FoodData#eat(1, 1.0F), restoring exactly 5 true Food (1 x 5)",
                    () -> {
                        boolean applied = MobEffects.SATURATION.value().applyEffectTick(level, saturationEffect, 0);
                        long food = queryFood(saturationEffect);
                        int mirrorValue = saturationEffect.getFoodData().getFoodLevel();
                        boolean pass = applied && food == 45 && mirrorValue == 9;
                        return result(pass, "applied=" + applied + ", food=" + food + " (expected 45), mirror="
                                + mirrorValue + " (expected 9)");
                    });
        } finally {
            saturationEffect.discard();
        }

        // ── PEACEFUL, FORCED (2026-09-17 review correction) ─────────────────────────────────────
        // Every check in this block runs under a real, server-side-forced Difficulty.PEACEFUL —
        // never merely skipped if the dev server's ambient difficulty (server.properties, "easy" by
        // default) happens not to already be Peaceful. The original difficulty is restored in the
        // finally block regardless of outcome, so no other verification in this same server process
        // observes a changed difficulty.
        Difficulty originalDifficulty = level.getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        try {
            ServerPlayer peaceful = TotalityFakePlayer.create(level, "[FoodSystemVerification-peaceful]");
            try {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(peaceful);
                PlayerBaselineResources.reconcile(peaceful);

                safe(r, "PEACEFUL, auto-restore DISABLED (2026-09-17 real-client correction): "
                        + "ServerPlayerPeacefulFoodRestoreAuthorityMixin suppresses Peaceful's automatic "
                        + "Food restore entirely — Food must not climb on its own merely because "
                        + "difficulty is Peaceful", () -> {
                            adminSet(peaceful, 70);
                            peaceful.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100));
                            // A single call: ServerPlayer#tickRegeneration gates its Food-restore branch
                            // on (this.tickCount % 10 == 0), and a freshly created TotalityFakePlayer's
                            // tickCount is 0 (0 % 10 == 0 is already satisfied) — calling it more than
                            // once via reflection without also advancing tickCount would re-fire the
                            // same branch repeatedly, which is not what real per-tick play does.
                            Method tickRegeneration = ServerPlayer.class.getDeclaredMethod("tickRegeneration");
                            tickRegeneration.setAccessible(true);
                            tickRegeneration.invoke(peaceful);
                            long food = queryFood(peaceful);
                            int mirrorNow = peaceful.getFoodData().getFoodLevel();
                            return result(food == 70 && mirrorNow == FoodVanillaCompatibilityBridge.mirrorOf(70, 100),
                                    "food=" + food + " (expected 70, unchanged), mirror=" + mirrorNow
                                            + " (expected unchanged) — Peaceful must not restore Food");
                        });

                safe(r, "PEACEFUL, Saturation auto-restore DISABLED (2026-09-17 Pizza/Saturation "
                        + "correction): ServerPlayerPeacefulSaturationRestoreAuthorityMixin suppresses "
                        + "Peaceful's automatic Saturation restore entirely — Saturation must not climb "
                        + "on its own merely because difficulty is Peaceful, exactly like Food", () -> {
                            peaceful.getFoodData().setSaturation(10.0F);
                            // Same tickCount%20==0 gate as the Food-refill check above — a freshly
                            // created TotalityFakePlayer's tickCount is 0, so a single real
                            // tickRegeneration() call exercises the exact branch vanilla uses.
                            Method tickRegeneration = ServerPlayer.class.getDeclaredMethod("tickRegeneration");
                            tickRegeneration.setAccessible(true);
                            tickRegeneration.invoke(peaceful);
                            float saturationAfter = peaceful.getFoodData().getSaturationLevel();
                            return result(saturationAfter == 10.0F,
                                    "saturation=" + saturationAfter + " (expected 10.0, unchanged) — "
                                            + "Peaceful must not restore Saturation");
                        });

                safe(r, "PEACEFUL activity: Saturation stays reduced instead of freely regenerating "
                        + "(2026-09-17 Pizza/Saturation correction): a real exhaustion-threshold event "
                        + "reduces Saturation exactly like every other difficulty, and it MUST stay "
                        + "reduced afterward — Peaceful's own automatic-regen tick must not silently "
                        + "undo it on the very next opportunity", () -> {
                            adminSet(peaceful, 70);
                            peaceful.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100));
                            peaceful.getFoodData().setSaturation(6.0F);
                            peaceful.getFoodData().addExhaustion(41.0F);
                            peaceful.getFoodData().tick(peaceful); // real depletion event: 6.0 -> 5.0
                            float saturationAfterDepletion = peaceful.getFoodData().getSaturationLevel();
                            Method tickRegeneration = ServerPlayer.class.getDeclaredMethod("tickRegeneration");
                            tickRegeneration.setAccessible(true);
                            tickRegeneration.invoke(peaceful); // Peaceful's own automatic-regen opportunity
                            float saturationAfterRegenOpportunity = peaceful.getFoodData().getSaturationLevel();
                            boolean pass = saturationAfterDepletion == 5.0F && saturationAfterRegenOpportunity == 5.0F;
                            return result(pass, "saturation " + saturationAfterDepletion + " -> "
                                    + saturationAfterRegenOpportunity + " (expected 5.0 -> 5.0, never "
                                    + "creeping back toward 6.0 or higher)");
                        });

                safe(r, "PEACEFUL, exhaustion with Saturation > 0 (2026-09-17 review correction): "
                        + "Saturation absorbs the exhaustion-threshold event first, exactly like every "
                        + "other difficulty — Food itself must not drain while Saturation remains", () -> {
                            adminSet(peaceful, 70);
                            peaceful.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100));
                            peaceful.getFoodData().setSaturation(6.0F);
                            peaceful.getFoodData().addExhaustion(41.0F);
                            peaceful.getFoodData().tick(peaceful);
                            long food = queryFood(peaceful);
                            float saturationAfter = peaceful.getFoodData().getSaturationLevel();
                            boolean pass = food == 70 && saturationAfter == 5.0F;
                            return result(pass, "food=" + food + " (expected 70, unchanged), saturation="
                                    + saturationAfter + " (expected 5.0, absorbed one point)");
                        });

                safe(r, "PEACEFUL, exhaustion with Saturation == 0 (2026-09-17 review correction, the "
                        + "actual fix): once Saturation is exhausted, the same exhaustion-threshold "
                        + "event now drains true Food by 5 on Peaceful too — FoodDataPeacefulExhaustionDepletionAuthorityMixin "
                        + "unblocks vanilla's own difficulty-gated branch so FoodDataExhaustionAuthorityMixin's "
                        + "existing translation can run", () -> {
                            adminSet(peaceful, 70);
                            peaceful.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(70, 100)); // 14
                            peaceful.getFoodData().setSaturation(0.0F);
                            peaceful.getFoodData().addExhaustion(41.0F);
                            peaceful.getFoodData().tick(peaceful);
                            long food = queryFood(peaceful);
                            int vanillaMirror = peaceful.getFoodData().getFoodLevel();
                            boolean pass = food == 65 && vanillaMirror == 13;
                            return result(pass, "food=" + food + " (expected 65), vanillaMirror=" + vanillaMirror
                                    + " (expected 13) — Peaceful must deplete Food identically to every other difficulty");
                        });

                safe(r, "PEACEFUL, low positive Food (2026-09-17 review correction): a small positive "
                        + "authoritative Food value (2) can still reach exactly 0 via one real "
                        + "exhaustion-threshold event on Peaceful, exactly as on every other difficulty",
                        () -> {
                            adminSet(peaceful, 2);
                            peaceful.getFoodData().setFoodLevel(FoodVanillaCompatibilityBridge.mirrorOf(2, 100)); // 1
                            peaceful.getFoodData().setSaturation(0.0F);
                            peaceful.getFoodData().addExhaustion(41.0F);
                            peaceful.getFoodData().tick(peaceful);
                            long food = queryFood(peaceful);
                            int vanillaMirror = peaceful.getFoodData().getFoodLevel();
                            boolean pass = food == 0 && vanillaMirror == 0;
                            return result(pass, "food=" + food + " (expected 0), vanillaMirror=" + vanillaMirror + " (expected 0)");
                        });

                safe(r, "PEACEFUL, starvation still disabled: Food at 0 on a forced-Peaceful world "
                        + "must still cause no vanilla direct starvation HP damage — unaffected by "
                        + "unblocking the exhaustion-depletion branch above", () -> {
                            adminSet(peaceful, 0);
                            peaceful.getFoodData().setFoodLevel(0);
                            float healthBefore = peaceful.getHealth();
                            for (int i = 0; i < 85; i++) {
                                peaceful.getFoodData().tick(peaceful);
                            }
                            float healthAfter = peaceful.getHealth();
                            return result(healthAfter == healthBefore, "health " + healthBefore + " -> " + healthAfter);
                        });

                safe(r, "PEACEFUL, natural Food-based Health regen still disabled: full Food, an "
                        + "injured player, and enough real tick() calls on a forced-Peaceful world "
                        + "must still never heal — FoodDataNaturalRegenerationMixin/"
                        + "ServerPlayerPeacefulRegenerationMixin are untouched by this pass", () -> {
                            adminSet(peaceful, 100);
                            peaceful.getFoodData().setFoodLevel(20);
                            peaceful.getFoodData().setSaturation(5.0F);
                            peaceful.setHealth(Math.max(1.0F, peaceful.getMaxHealth() - 4.0F));
                            float healthBefore = peaceful.getHealth();
                            for (int i = 0; i < 85; i++) {
                                peaceful.getFoodData().tick(peaceful);
                            }
                            float healthAfter = peaceful.getHealth();
                            return result(healthAfter == healthBefore, "health " + healthBefore + " -> " + healthAfter);
                        });
            } finally {
                peaceful.discard();
            }
        } finally {
            server.setDifficulty(originalDifficulty, true);
        }

        safe(r, "AUTHORITY: totality:food genuinely instantiates as live GENERIC_COMPONENT state "
                + "(would throw if still rejected as external)", () -> {
            ServerPlayer probe = TotalityFakePlayer.create(level, "[FoodSystemVerification-probe]");
            try {
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(probe);
                boolean hasState = ResourceStateComponents.get(probe).hasState(PlayerResourceIds.FOOD);
                return result(hasState, "hasState=" + hasState);
            } finally {
                probe.discard();
            }
        });

        r.summarize();
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
