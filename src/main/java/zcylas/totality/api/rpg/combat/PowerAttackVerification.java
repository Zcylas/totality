package zcylas.totality.api.rpg.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gamerules.GameRules;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for {@link PowerAttackManager#isValidTarget}/{@link
 * PowerAttackManager#isAttackerLegal} (correction pass, Parts C and D) — the shared predicates
 * used by BOTH the mainhand path ({@code MinecraftAttackMixin} client-side gate,
 * {@code TotalityServerPacketHandlers}'s {@code PowerAttackPayload} handler server-side) and the
 * offhand path ({@code MinecraftAttackMixin}'s offhand hold-charge, {@code OffhandAttackHandler}
 * server-side) — since every one of those call sites delegates to these exact same two methods,
 * proving the predicates correct here transitively proves "mainhand and offhand use consistent
 * server legality" (Part D's explicit requirement) by construction, not by duplicating the same
 * assertions against two separate code paths. Same {@link VerificationReporter} convention as the
 * shop/economy suites.
 *
 * <p>What this suite CANNOT verify (documented, not silently skipped): the actual client-side
 * hold/charge/cancel state machines in {@code MinecraftAttackMixin} for BOTH hands — whether
 * holding LMB on a block or RMB while dual-wielding on a block/air never starts a charge, whether
 * losing the target mid-charge cancels without spending Stamina/flash/sound, whether normal
 * mining/interaction continues unaffected, and whether the client-side {@code isAttackerLegal}
 * pre-check actually suppresses the flash/sound in a live render — all require driving real
 * mouse/keyboard input against a live client window, which this environment has no tool to do
 * (the same standing limitation documented throughout the Provisioner phases). Those remain
 * Stefan's manual checklist. This suite instead proves the shared predicates themselves are
 * correct, which every client and server call site delegates to unconditionally.
 */
public final class PowerAttackVerification {

    private PowerAttackVerification() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(PowerAttackVerification::runSelfTestIfDev);
    }

    static void runSelfTestIfDev(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "PowerAttackVerification");
        ServerPlayer player = TotalityFakePlayer.create(server.overworld(), "[PowerAttackVerification]");

        checkValidHostileLivingTargetPermitsPowerAttack(r, player, server);
        checkValidPlayerTargetPermitsPowerAttack(r, player, server);
        checkNullTargetRejected(r, player);
        checkSelfTargetRejected(r, player);
        checkNonLivingEntityRejected(r, player, server);
        checkDeadEntityRejected(r, player, server);
        checkOutOfRangeEntityRejected(r, player, server);
        checkExactRangeBoundaryAccepted(r, player, server);
        checkOnPowerAttackReceivedConsumesStaminaOnlyWhenCalled(r, player);

        checkInvulnerableTargetRejectedByAttackerLegality(r, player, server);
        checkOrdinaryHostileMobLegalRegardlessOfPvpRule(r, player, server);
        checkPvpDisabledRejectsPlayerTarget(r, player, server);

        r.summarize();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Phase 4 correction pass, Part D — isAttackerLegal (checked BEFORE Stamina is committed)
    //
    // NOTE: no positive "ordinary player-vs-player target is legal" check, and no dedicated
    // team-friendly-fire check, exist here. TotalityFakePlayer.canHarmPlayer(Player) is hardcoded
    // `false` for every fake player instance (a defensive test-fixture design shared and relied on
    // by many suites, not something this pass should change) — two TotalityFakePlayers can
    // therefore NEVER be mutually attacker-legal against each other, regardless of PvP/team state.
    // This makes BOTH a meaningful positive player-vs-player case AND a team-friendly-fire-specific
    // case (which would need canHarmPlayer to actually return true for an allied team, distinct
    // from the PvP-disabled early return) impossible to construct with this fixture — a team setup
    // around two fake players would "pass" vacuously regardless of whether the friendly-fire branch
    // is even reached, since canHarmPlayer already always returns false. checkPvpDisabledRejects
    // PlayerTarget below IS meaningful (the PVP-rule branch returns early, before canHarmPlayer is
    // ever consulted); checkOrdinaryHostileMobLegalRegardlessOfPvpRule is this suite's positive-case
    // proof that the predicate doesn't over-restrict. Team friendly-fire specifically remains
    // Stefan's manual checklist (a real second client-connected player is required to exercise it).
    // ─────────────────────────────────────────────────────────────────────

    private static void checkInvulnerableTargetRejectedByAttackerLegality(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "An invulnerable target is never attacker-legal, spending no Stamina", () -> {
            Zombie zombie = new Zombie(EntityTypes.ZOMBIE, server.overworld());
            zombie.setPos(player.getX() + 2, player.getY(), player.getZ());
            zombie.setInvulnerable(true);
            try {
                boolean pass = !PowerAttackManager.isAttackerLegal(player, zombie);
                return result(pass, "isAttackerLegal=" + PowerAttackManager.isAttackerLegal(player, zombie));
            } finally {
                zombie.setInvulnerable(false);
            }
        });
    }

    private static void checkOrdinaryHostileMobLegalRegardlessOfPvpRule(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "An ordinary attackable mob remains attacker-legal even with PvP disabled — "
                + "no blanket restriction against neutral/allied non-player mobs is invented", () -> {
            Zombie zombie = new Zombie(EntityTypes.ZOMBIE, server.overworld());
            zombie.setPos(player.getX() + 2, player.getY(), player.getZ());
            boolean pvpBefore = server.overworld().getGameRules().get(GameRules.PVP);
            server.overworld().getGameRules().set(GameRules.PVP, false, server);
            try {
                boolean pass = PowerAttackManager.isAttackerLegal(player, zombie);
                return result(pass, "isAttackerLegal=" + pass);
            } finally {
                server.overworld().getGameRules().set(GameRules.PVP, pvpBefore, server);
            }
        });
    }

    private static void checkPvpDisabledRejectsPlayerTarget(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "A player target is not attacker-legal while the server's PvP game rule is disabled, spending no Stamina", () -> {
            ServerPlayer other = TotalityFakePlayer.create(server.overworld(), "[PowerAttackVerification-pvp-target]");
            other.setPos(player.getX() + 2, player.getY(), player.getZ());
            boolean pvpBefore = server.overworld().getGameRules().get(GameRules.PVP);
            server.overworld().getGameRules().set(GameRules.PVP, false, server);
            try {
                boolean pass = !PowerAttackManager.isAttackerLegal(player, other);
                return result(pass, "isAttackerLegal=" + PowerAttackManager.isAttackerLegal(player, other));
            } finally {
                server.overworld().getGameRules().set(GameRules.PVP, pvpBefore, server);
            }
        });
    }

    private static void checkValidHostileLivingTargetPermitsPowerAttack(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "A valid hostile living target within melee reach is a legal Power Attack target", () -> {
            Zombie zombie = new Zombie(EntityTypes.ZOMBIE, server.overworld());
            zombie.setPos(player.getX() + 2, player.getY(), player.getZ());
            boolean pass = PowerAttackManager.isValidTarget(player, zombie);
            return result(pass, "isValidTarget=" + pass);
        });
    }

    private static void checkValidPlayerTargetPermitsPowerAttack(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Another player within melee reach is a legal Power Attack target "
                + "(existing PvP/team/invulnerability rules are enforced downstream, unchanged, by vanilla attack resolution)", () -> {
            ServerPlayer other = TotalityFakePlayer.create(server.overworld(), "[PowerAttackVerification-target]");
            other.setPos(player.getX() + 2, player.getY(), player.getZ());
            boolean pass = PowerAttackManager.isValidTarget(player, other);
            return result(pass, "isValidTarget=" + pass);
        });
    }

    private static void checkNullTargetRejected(VerificationReporter r, ServerPlayer player) {
        safe(r, "A null target (block, air, or no crosshair pick at all) is never a legal Power Attack target",
                () -> result(!PowerAttackManager.isValidTarget(player, null), "isValidTarget should be false for null"));
    }

    private static void checkSelfTargetRejected(VerificationReporter r, ServerPlayer player) {
        safe(r, "The player themself is never a legal Power Attack target",
                () -> result(!PowerAttackManager.isValidTarget(player, player), "isValidTarget should be false for self"));
    }

    private static void checkNonLivingEntityRejected(VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "A non-living entity (e.g. an item entity) is never a legal Power Attack target", () -> {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                    server.overworld(), player.getX() + 1, player.getY(), player.getZ(),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            boolean pass = !PowerAttackManager.isValidTarget(player, item);
            return result(pass, "isValidTarget=" + PowerAttackManager.isValidTarget(player, item));
        });
    }

    private static void checkDeadEntityRejected(VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "A dead/no-longer-alive entity is never a legal Power Attack target", () -> {
            Zombie zombie = new Zombie(EntityTypes.ZOMBIE, server.overworld());
            zombie.setPos(player.getX() + 2, player.getY(), player.getZ());
            zombie.hurtServer(server.overworld(), server.overworld().damageSources().generic(), 1000.0f);
            boolean pass = !PowerAttackManager.isValidTarget(player, zombie);
            return result(pass, "isAlive=" + zombie.isAlive() + ", isValidTarget=" + PowerAttackManager.isValidTarget(player, zombie));
        });
    }

    private static void checkOutOfRangeEntityRejected(VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "An entity beyond melee reach is never a legal Power Attack target", () -> {
            Zombie zombie = new Zombie(EntityTypes.ZOMBIE, server.overworld());
            zombie.setPos(player.getX() + PowerAttackManager.MELEE_TARGET_RANGE + 5, player.getY(), player.getZ());
            boolean pass = !PowerAttackManager.isValidTarget(player, zombie);
            return result(pass, "isValidTarget=" + PowerAttackManager.isValidTarget(player, zombie));
        });
    }

    private static void checkExactRangeBoundaryAccepted(VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "An entity exactly at melee reach is still a legal Power Attack target (inclusive boundary)", () -> {
            Zombie zombie = new Zombie(EntityTypes.ZOMBIE, server.overworld());
            zombie.setPos(player.getX() + PowerAttackManager.MELEE_TARGET_RANGE, player.getY(), player.getZ());
            boolean pass = PowerAttackManager.isValidTarget(player, zombie);
            return result(pass, "isValidTarget=" + pass);
        });
    }

    /**
     * Confirms {@link PowerAttackManager#onPowerAttackReceived} itself still unconditionally
     * consumes Stamina when actually invoked — the reason gating IS the fix: an invalid target
     * must simply never reach this call at all (enforced by the packet handler's
     * {@code isValidTarget} check, and by the client mixin never sending the payload in the first
     * place), not by this method somehow refusing to charge Stamina on its own.
     */
    private static void checkOnPowerAttackReceivedConsumesStaminaOnlyWhenCalled(VerificationReporter r, ServerPlayer player) {
        safe(r, "onPowerAttackReceived consumes Stamina when invoked (confirms gating happens upstream, at the target check)", () -> {
            zcylas.totality.api.rpg.stamina.PlayerStaminaManager.setStamina(player, 100);
            int before = zcylas.totality.api.rpg.stamina.PlayerStaminaManager.getStamina(player);
            PowerAttackManager.onPowerAttackReceived(player);
            int after = zcylas.totality.api.rpg.stamina.PlayerStaminaManager.getStamina(player);
            PowerAttackManager.onPlayerLeave(player); // clears the pending-power-attack flag this just set
            boolean pass = after < before;
            return result(pass, "before=" + before + ", after=" + after);
        });
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
