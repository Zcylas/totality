package zcylas.totality.api.combat.weapon;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

public class WeaponStaminaHandler {

    /**
     * Registers the attack callback.
     * Call from your main Totality initializer.
     */
    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            // Server side only
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            // Creative players are exempt
            if (serverPlayer.isCreative()) return InteractionResult.PASS;

            ItemStack mainHand = serverPlayer.getMainHandItem();
            int cost = VanillaWeaponTypes.getAttackCost(mainHand);

            // Drain stamina — exhaustion already handles damage penalty at 0
            PlayerStaminaManager.removeStamina(serverPlayer, cost);
            StaminaServerTick.syncStamina(serverPlayer);

            return InteractionResult.PASS; // never block the attack
        });
    }

    private WeaponStaminaHandler() {}
}