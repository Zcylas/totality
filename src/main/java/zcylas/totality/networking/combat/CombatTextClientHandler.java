// networking/combat/CombatTextClientHandler.java
package zcylas.totality.networking.combat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.combat.damage.DamageTypeRegistry;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.combat.condition.Conditions;
import zcylas.totality.client.combat.CombatTextManager;
import zcylas.totality.client.renderer.hud.MobHealthBarHud;

public final class CombatTextClientHandler {

    private CombatTextClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                CombatTextPayload.TYPE, (payload, context) -> {
                    context.client().execute(() -> {
                        handle(payload);

                        // Update mob health bar — must be on game thread
                        if (context.client().level != null
                                && context.client().player != null
                                && payload.attackerEntityId() == context.client().player.getId()) {
                            net.minecraft.world.entity.Entity entity =
                                    context.client().level.getEntity(payload.entityId());
                            if (entity instanceof LivingEntity living) {
                                MobHealthBarHud.onPlayerHitMob(living);
                            }
                        }
                    });
                });
    }

    private static void handle(CombatTextPayload payload) {
        Vec3 pos = new Vec3(payload.x(), payload.y(), payload.z());
        TotalityDamageType type = payload.damageTypeId() != null
                ? DamageTypeRegistry.get(payload.damageTypeId())
                : null;

        switch (payload.textType()) {
            case IMMUNE -> CombatTextManager.spawnImmune(type, pos);
            case DAMAGE, RESIST, VULNERABLE -> CombatTextManager.spawnDamage(
                    type, payload.amount(), pos,
                    payload.resisted(), payload.vulnerable());
            case CONDITION -> {
                // Find condition by label name for now
                // Later we can send condition ID directly
                CombatTextManager.spawnCondition(type,
                        Conditions.getByName(payload.label()), pos);
            }
            case HEAL -> CombatTextManager.spawnDamage(
                    null, payload.amount(), pos, false, false);
        }
    }
}