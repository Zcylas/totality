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

        // ── Power attack: mark advantage for interceptor ──────────────────────
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) return true;
            if (!PowerAttackManager.consumePowerAttack(player)) return true;
            // Mark advantage — VanillaDamageInterceptor reads and clears this
            PowerAttackManager.markPowerAttack(player.getUUID());
            return true; // let interceptor handle with advantage
        });
    }
}