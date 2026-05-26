package zcylas.totality.networking.stamina;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import zcylas.totality.api.rpg.combat.CombatStateManager;
import zcylas.totality.api.rpg.combat.bow.BowStaminaHandler;
import zcylas.totality.api.rpg.combat.exhaustion.ExhaustionManager;
import zcylas.totality.api.rpg.resources.ResourceComponents;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

public class StaminaServerTick {
    // Static counter shared across all players on this server instance — fine for single-server use
    private static int tickCounter = 0;

    private static final Identifier SPEED_MODIFIER_ID =
            Identifier.fromNamespaceAndPath("totality", "exhaustion_speed");
    private static final double SPEED_PENALTY = -0.20;

    private static final Identifier ATTACK_MODIFIER_ID =
            Identifier.fromNamespaceAndPath("totality", "exhaustion_attack");
    private static final double ATTACK_PENALTY = -0.25;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {

                // ── Tick combat timer ─────────────────────────────────────────
                CombatStateManager.tick(player);

                // ── Sprint drain — every 3 ticks, only on ground ──────────────
                if (player.isSprinting() && !player.isCreative() && player.onGround()
                        && !BowStaminaHandler.isBowDrawn(player)) {
                    if (tickCounter % 3 == 0) {
                        PlayerStaminaManager.removeStamina(player, 1);
                        syncStamina(player);
                    }
                    if (PlayerStaminaManager.getStamina(player) <= 0) {
                        player.setSprinting(false);
                    }
                }

                // ── Bow stamina drain ─────────────────────────────────────────
                BowStaminaHandler.tick(player, tickCounter);

                // ── Exhaustion state tick ─────────────────────────────────────
                ExhaustionManager.tick(player);

                // ── Exhaustion attribute penalties ────────────────────────────
                boolean penalized = ExhaustionManager.isPenalized(player) && !player.isCreative();

                var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    if (penalized) {
                        speedAttr.addOrUpdateTransientModifier(new AttributeModifier(
                                SPEED_MODIFIER_ID, SPEED_PENALTY,
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    } else {
                        speedAttr.removeModifier(SPEED_MODIFIER_ID);
                    }
                }

                var attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attackAttr != null) {
                    if (penalized) {
                        attackAttr.addOrUpdateTransientModifier(new AttributeModifier(
                                ATTACK_MODIFIER_ID, ATTACK_PENALTY,
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    } else {
                        attackAttr.removeModifier(ATTACK_MODIFIER_ID);
                    }
                }

                // ── Stamina regen — every 20 ticks ────────────────────────────
                if (tickCounter % 20 == 0) {
                    boolean regenBlocked = player.isSprinting()
                            || BowStaminaHandler.isBowDrawn(player);

                    if (!regenBlocked) {
                        int current = PlayerStaminaManager.getStamina(player);
                        int max     = PlayerStaminaManager.getMaxStamina(player);
                        if (current < max) {
                            float regenMultiplier = ExhaustionManager.getRegenMultiplier(player);
                            int regenAmount = Math.max(1,
                                    (int)(PlayerStaminaManager.calculateRegenAmount(player)
                                            * regenMultiplier));
                            PlayerStaminaManager.addStamina(player, regenAmount);
                            PlayerStaminaManager.setStamina(player,
                                    PlayerStaminaManager.getStamina(player));
                            syncStamina(player);
                        }
                    }
                }
            }

            if (tickCounter >= 60) tickCounter = 0;
        });
    }

    public static void syncStamina(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncStaminaPayload(
                PlayerStaminaManager.getStamina(player),
                PlayerStaminaManager.getMaxStamina(player)));
    }

    private StaminaServerTick() {}
}