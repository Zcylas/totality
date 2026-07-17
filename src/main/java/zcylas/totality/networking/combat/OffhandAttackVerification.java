package zcylas.totality.networking.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.rpg.combat.DualWieldCooldownTracker;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.api.rpg.combat.weapon.VanillaWeaponTypes;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for {@link OffhandAttackHandler#handle} (post-review
 * correction) — drives the REAL handler directly (package-private, not a reimplementation) with
 * hand-built {@link OffhandAttackPayload}s, the same "exercise production code" discipline every
 * other suite in this codebase follows.
 *
 * <p>Root cause this suite targets: previously, a Power-Attack-flagged offhand request that
 * failed {@link PowerAttackManager#isAttackerLegal} still fell through into the normal-attack
 * path (only the {@code usePower} advantage flag was denied), spending ordinary offhand Stamina,
 * damaging the offhand weapon, and landing a hit — none of which an illegal Power Attack may do.
 * The fix returns immediately once an illegal Power Attack is detected, before Stamina/durability/
 * attack-execution are ever decided.
 *
 * <p>Each check uses a FRESH {@link TotalityFakePlayer} (a new random UUID) specifically so
 * {@link DualWieldCooldownTracker}'s per-UUID cooldown map — which persists across checks within
 * one suite run since game time barely advances between them — can never block a later check's
 * attack attempt; a fresh UUID has no recorded cooldown at all. That same cooldown map doubles as
 * a proxy for "did an attack actually execute": {@code canOffhandAttack} reads {@code true} for an
 * untouched UUID and would read {@code false} immediately after {@code markOffhandAttack} ran, so
 * checking it after an illegal attempt distinguishes "nothing happened" from "the attack silently
 * went through".
 *
 * <p>This suite runs DEFERRED — {@value #SUITE_DELAY_TICKS} ticks after server start, via the
 * existing {@link ServerScheduler}, the SAME pattern {@code ProvisionerVerification}'s own
 * {@code ProvisionerEntityBackedSmokeTest} already established — rather than directly at {@code
 * SERVER_STARTED} like most other suites. Reason: {@code OffhandAttackHandler.handle} resolves its
 * target via {@code player.level().getEntity(int)}, and a freshly {@code addFreshEntity}'d entity
 * is provably NOT visible to that lookup before the server has processed at least one real tick
 * (confirmed empirically both here and in {@code MerchantSellVerification}'s own entity-backed
 * revalidation checks) — at {@code SERVER_STARTED} every target in this suite would silently
 * resolve to {@code null}, making {@code isValidTarget} reject it for the WRONG reason and every
 * "illegal target" check pass vacuously regardless of whether the actual fix works.
 *
 * <p>What this suite CANNOT verify (documented, not skipped): team friendly-fire specifically —
 * {@link TotalityFakePlayer#canHarmPlayer} is hardcoded {@code false} for every fake-player
 * instance (the same fixture limitation {@code PowerAttackVerification} already documents), so a
 * player-vs-fake-player scenario can never isolate "friendly fire" from "the fixture's own
 * default" or from the PvP-rule branch specifically. The PvP-disabled check below still
 * legitimately exercises the {@code GameRules.PVP} branch (it returns before ever reaching
 * {@code canHarmPlayer}), so it is not affected by that limitation. Real client-side flash/sound
 * suppression is likewise not exercised here — it's client-only and requires a live window.
 */
public final class OffhandAttackVerification {

    /** Matches {@code ProvisionerVerification.SMOKE_TEST_DELAY_TICKS} — long enough that every
     *  entity this suite spawns is resolvable via {@code Level#getEntity(int)} the normal way. */
    private static final int SUITE_DELAY_TICKS = 40;

    private OffhandAttackVerification() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(OffhandAttackVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;
        ServerScheduler.getInstance().queue(OffhandAttackVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "OffhandAttackVerification");

        checkLegalOffhandPowerAttackWorksAndSpendsPowerStamina(r, server);
        checkIllegalInvulnerableTargetSpendsNoStaminaOrDurabilityOrAttack(r, server);
        checkPvpDisabledOffhandPowerAttackSpendsNoStamina(r, server);
        checkLegalOrdinaryOffhandAttackUnchanged(r, server);
        checkInsufficientPowerStaminaStillDowngradesToNormalAttack(r, server);

        r.summarize();
    }

    /**
     * Deliberately does NOT assert the target's health drops — {@code CombatResolver.resolveAttack}
     * rolls a real d20-vs-armor-class attack roll, which can legitimately MISS regardless of
     * whether the swing itself was legal and executed; asserting a guaranteed hit would make this
     * check flaky on RNG unrelated to the fix under test. The Stamina cost (exactly {@code
     * powerCost}, never the plain ordinary cost) and the cooldown being marked (proof the method
     * ran to completion rather than returning early) are the deterministic signals that the swing
     * was correctly attempted as a Power Attack.
     */
    private static void checkLegalOffhandPowerAttackWorksAndSpendsPowerStamina(VerificationReporter r, MinecraftServer server) {
        safe(r, "A legal offhand Power Attack works normally and spends the Power Attack Stamina cost", () -> {
            ServerPlayer player = dualWieldingPlayer(server, "[OffhandAttackVerification-legal-power]");
            Zombie target = freshZombie(server, player);
            try {
                int powerCost = PowerAttackManager.getOffhandStaminaCost(player);
                PlayerStaminaManager.setStamina(player, 100);
                int staminaBefore = PlayerStaminaManager.getStamina(player);

                OffhandAttackHandler.handle(player, new OffhandAttackPayload(target.getId(), true));

                int staminaAfter = PlayerStaminaManager.getStamina(player);
                boolean pass = staminaBefore - staminaAfter == powerCost
                        && !DualWieldCooldownTracker.canOffhandAttack(player); // cooldown now marked -> attack executed
                return result(pass, "staminaBefore=" + staminaBefore + ", staminaAfter=" + staminaAfter + ", powerCost=" + powerCost);
            } finally {
                target.discard();
            }
        });
    }

    private static void checkIllegalInvulnerableTargetSpendsNoStaminaOrDurabilityOrAttack(VerificationReporter r, MinecraftServer server) {
        safe(r, "An illegal (invulnerable) offhand Power Attack target: no Stamina, no durability loss, no attack, no downgrade", () -> {
            ServerPlayer player = dualWieldingPlayer(server, "[OffhandAttackVerification-invulnerable]");
            Zombie target = freshZombie(server, player);
            target.setInvulnerable(true);
            try {
                PlayerStaminaManager.setStamina(player, 100);
                int staminaBefore = PlayerStaminaManager.getStamina(player);
                ItemStack offhandBefore = player.getOffhandItem().copy();
                float healthBefore = target.getHealth();

                OffhandAttackHandler.handle(player, new OffhandAttackPayload(target.getId(), true));

                int staminaAfter = PlayerStaminaManager.getStamina(player);
                ItemStack offhandAfter = player.getOffhandItem();
                boolean pass = staminaAfter == staminaBefore
                        && offhandAfter.getDamageValue() == offhandBefore.getDamageValue()
                        && target.getHealth() == healthBefore
                        && DualWieldCooldownTracker.canOffhandAttack(player); // never marked -> no attack executed
                return result(pass, "staminaBefore=" + staminaBefore + ", staminaAfter=" + staminaAfter
                        + ", durabilityBefore=" + offhandBefore.getDamageValue() + ", durabilityAfter=" + offhandAfter.getDamageValue()
                        + ", healthBefore=" + healthBefore + ", healthAfter=" + target.getHealth());
            } finally {
                target.setInvulnerable(false);
                target.discard();
            }
        });
    }

    private static void checkPvpDisabledOffhandPowerAttackSpendsNoStamina(VerificationReporter r, MinecraftServer server) {
        safe(r, "A PvP-disabled offhand Power Attack against a player target: no Stamina, no durability loss, no attack", () -> {
            ServerPlayer player = dualWieldingPlayer(server, "[OffhandAttackVerification-pvp-attacker]");
            ServerPlayer targetPlayer = TotalityFakePlayer.create(server.overworld(), "[OffhandAttackVerification-pvp-target]");
            targetPlayer.setPos(player.getX() + 1, player.getY(), player.getZ());
            // TotalityFakePlayer is never added to the level by its own factory (unlike a real
            // player, which PlayerList.placeNewPlayer registers) — OffhandAttackHandler.handle
            // resolves its target via player.level().getEntity(id), so without this the target
            // would resolve to null and the check would pass VACUOUSLY (indistinguishable from a
            // correctly-rejected PvP-disabled attack) rather than actually exercising the
            // GameRules.PVP branch. Mirrors freshZombie's identical addFreshEntity requirement.
            // Expected side effect (integrated-server dev harness only, harmless): since this fake
            // player was never routed through PlayerList.placeNewPlayer, the client render thread
            // logs a single "Server attempted to add player prior to sending player info" WARN for
            // it — cosmetic, does not affect this check's pass/fail, and unreachable in real
            // gameplay where every player joins through the normal connection flow.
            server.overworld().addFreshEntity(targetPlayer);

            boolean pvpBefore = server.overworld().getGameRules().get(GameRules.PVP);
            server.overworld().getGameRules().set(GameRules.PVP, false, server);
            try {
                // Precondition: prove the target actually resolves via the same lookup
                // OffhandAttackHandler.handle uses, so a failure below is never mistaken for a
                // vacuous pass caused by an unresolved entity.
                boolean targetResolves = player.level().getEntity(targetPlayer.getId()) == targetPlayer;

                PlayerStaminaManager.setStamina(player, 100);
                int staminaBefore = PlayerStaminaManager.getStamina(player);
                ItemStack offhandBefore = player.getOffhandItem().copy();

                OffhandAttackHandler.handle(player, new OffhandAttackPayload(targetPlayer.getId(), true));

                int staminaAfter = PlayerStaminaManager.getStamina(player);
                ItemStack offhandAfter = player.getOffhandItem();
                boolean pass = targetResolves
                        && staminaAfter == staminaBefore
                        && offhandAfter.getDamageValue() == offhandBefore.getDamageValue()
                        && DualWieldCooldownTracker.canOffhandAttack(player);
                return result(pass, "targetResolves=" + targetResolves
                        + ", staminaBefore=" + staminaBefore + ", staminaAfter=" + staminaAfter
                        + ", durabilityBefore=" + offhandBefore.getDamageValue() + ", durabilityAfter=" + offhandAfter.getDamageValue());
            } finally {
                server.overworld().getGameRules().set(GameRules.PVP, pvpBefore, server);
                targetPlayer.discard();
            }
        });
    }

    /** Same "don't assert a guaranteed hit" reasoning as the legal-power-attack check above —
     *  the attack ROLL can miss regardless of legality; the deterministic ordinary Stamina cost
     *  and the marked cooldown are what actually prove this path is unaffected by Part C. */
    private static void checkLegalOrdinaryOffhandAttackUnchanged(VerificationReporter r, MinecraftServer server) {
        safe(r, "A legal ordinary (non-power) offhand attack remains unchanged: spends ordinary Stamina", () -> {
            ServerPlayer player = dualWieldingPlayer(server, "[OffhandAttackVerification-ordinary]");
            Zombie target = freshZombie(server, player);
            try {
                int ordinaryCost = VanillaWeaponTypes.getAttackCost(player.getOffhandItem());
                PlayerStaminaManager.setStamina(player, 100);
                int staminaBefore = PlayerStaminaManager.getStamina(player);

                OffhandAttackHandler.handle(player, new OffhandAttackPayload(target.getId(), false));

                int staminaAfter = PlayerStaminaManager.getStamina(player);
                boolean pass = staminaBefore - staminaAfter == ordinaryCost
                        && !DualWieldCooldownTracker.canOffhandAttack(player);
                return result(pass, "staminaBefore=" + staminaBefore + ", staminaAfter=" + staminaAfter + ", ordinaryCost=" + ordinaryCost);
            } finally {
                target.discard();
            }
        });
    }

    /** Same "don't assert a guaranteed hit" reasoning again — the meaningful, deterministic proof
     *  that Stamina insufficiency still gracefully downgrades (rather than being rejected outright
     *  like an illegal target) is that exactly the ORDINARY cost is spent, never the power cost,
     *  and the swing still executes to completion (cooldown marked). */
    private static void checkInsufficientPowerStaminaStillDowngradesToNormalAttack(VerificationReporter r, MinecraftServer server) {
        safe(r, "Insufficient Power Attack Stamina still gracefully downgrades to a legal normal offhand attack (unchanged)", () -> {
            ServerPlayer player = dualWieldingPlayer(server, "[OffhandAttackVerification-low-stamina]");
            Zombie target = freshZombie(server, player);
            try {
                int powerCost = PowerAttackManager.getOffhandStaminaCost(player);
                int ordinaryCost = VanillaWeaponTypes.getAttackCost(player.getOffhandItem());
                // Enough for an ordinary swing, not enough for the power-attack cost.
                int lowStamina = Math.max(ordinaryCost, powerCost - 1);
                PlayerStaminaManager.setStamina(player, lowStamina);
                int staminaBefore = PlayerStaminaManager.getStamina(player);

                OffhandAttackHandler.handle(player, new OffhandAttackPayload(target.getId(), true));

                int staminaAfter = PlayerStaminaManager.getStamina(player);
                boolean pass = staminaBefore - staminaAfter == ordinaryCost // downgraded cost, not the power cost
                        && !DualWieldCooldownTracker.canOffhandAttack(player); // the swing still executed
                return result(pass, "staminaBefore=" + staminaBefore + ", staminaAfter=" + staminaAfter
                        + ", ordinaryCost=" + ordinaryCost + ", powerCost=" + powerCost);
            } finally {
                target.discard();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fixtures and helpers
    // ─────────────────────────────────────────────────────────────────────

    /** A fresh (new random UUID) fake player dual-wielding two plain iron swords — satisfies
     *  {@code OffhandAttackHandler.isDualWielding} without needing a custom weapon item. */
    private static ServerPlayer dualWieldingPlayer(MinecraftServer server, String name) {
        ServerPlayer player = TotalityFakePlayer.create(server.overworld(), name);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        return player;
    }

    private static Zombie freshZombie(MinecraftServer server, ServerPlayer near) {
        Zombie zombie = new Zombie(EntityTypes.ZOMBIE, server.overworld());
        zombie.setPos(near.getX() + 1, near.getY(), near.getZ());
        server.overworld().addFreshEntity(zombie);
        return zombie;
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, Supplier<CheckResult> body) {
        try {
            CheckResult checkResult = body.get();
            r.check(label, checkResult.pass(), checkResult.detail());
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
