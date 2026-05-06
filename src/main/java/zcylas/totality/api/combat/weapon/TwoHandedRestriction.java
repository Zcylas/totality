package zcylas.totality.api.combat.weapon;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.networking.notification.SendNotificationPayload;

public class TwoHandedRestriction {

    /**
     * Registers the two-handed restriction.
     * Prevents using/equipping items in the offhand when holding a two-handed weapon.
     * Call from your main Totality initializer.
     */
    public static void register() {
        // Hook into item use — fires when player tries to use offhand item
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (serverPlayer.isCreative()) return InteractionResult.PASS;

            // Only care about offhand use
            if (hand != InteractionHand.OFF_HAND) return InteractionResult.PASS;

            ItemStack mainHand = serverPlayer.getMainHandItem();

            // If main hand has a two-handed weapon, block offhand use
            if (VanillaWeaponTypes.isTwoHanded(mainHand)) {
                SendNotificationPayload.send(serverPlayer,
                        "⚔ You need both hands for this weapon.",
                        SendNotificationPayload.GRAY);
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });
    }

    private TwoHandedRestriction() {}
}