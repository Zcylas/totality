package zcylas.totality.init.events;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import zcylas.totality.api.rpg.combat.CombatStateManager;

public class CombatServerEvents {

    public static void register() {
        // ── Combat state: player attacks something ────────────────────────────
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer sp)
                CombatStateManager.onDamage(sp);
            return InteractionResult.PASS;
        });
    }

    private CombatServerEvents() {}
}