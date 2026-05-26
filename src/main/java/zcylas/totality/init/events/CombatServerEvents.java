package zcylas.totality.init.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import zcylas.totality.api.rpg.combat.CombatStateManager;
import zcylas.totality.api.rpg.combat.PowerAttackManager;

public class CombatServerEvents {

    public static void register() {
        // ── Combat state: player attacks something ────────────────────────────
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer sp)
                CombatStateManager.onDamage(sp);
            return InteractionResult.PASS;
        });

        // ── Power attack: apply damage multiplier ─────────────────────────────
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) return true;
            if (!PowerAttackManager.consumePowerAttack(player)) return true;

            float multiplied = amount * PowerAttackManager.getDamageMultiplier(player);
            entity.hurt(source, multiplied);
            return false; // cancel the original hit, we already applied the multiplied one
        });
    }
}