package zcylas.totality.api.rpg.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for {@link PowerAttackManager#isValidTarget} (correction pass,
 * Part C) — the single predicate shared by the client mixin ({@code MinecraftAttackMixin}, which
 * gates whether a hold-charge may begin/continue at all) and the server packet handler
 * ({@code TotalityServerPacketHandlers}, which re-validates before ever consuming Stamina or
 * marking advantage). Same {@link VerificationReporter} convention as the shop/economy suites.
 *
 * <p>What this suite CANNOT verify (documented, not silently skipped): the actual client-side
 * hold/charge/cancel state machine in {@code MinecraftAttackMixin} — whether holding LMB on a
 * block never starts a charge, whether losing the target mid-charge cancels without spending
 * Stamina, whether normal mining continues unaffected — all require driving real mouse/keyboard
 * input against a live client window, which this environment has no tool to do (the same standing
 * limitation documented throughout the Provisioner phases). Those remain Stefan's manual
 * checklist, items 12-15. This suite instead proves the shared predicate itself is correct, which
 * both the client and server sides delegate to unconditionally.
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

        r.summarize();
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
