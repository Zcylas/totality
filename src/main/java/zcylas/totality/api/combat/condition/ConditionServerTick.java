package zcylas.totality.api.combat.condition;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;

public final class ConditionServerTick {

    private ConditionServerTick() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ConditionServerTick::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ConditionHolder holder)) continue;
                if (!(entity instanceof LivingEntity living)) continue;
                ConditionComponent comp = holder.totality$getConditions();
                comp.tick();
                applyTickEffects(living, comp);
            }
        }
    }

    private static void applyTickEffects(LivingEntity entity, ConditionComponent comp) {
        int tick = (int)(entity.level().getGameTime() % 20);

        // NO_CONDITIONS prevents re-applying the condition that caused this damage,
        // breaking the infinite loop (e.g. BURNING tick damage re-applying BURNING).
        if (comp.has(Conditions.BURNING) && tick == 0) {
            ActiveCondition ac = comp.get(Conditions.BURNING);
            TotalityDamage.hurt(entity,
                    ac != null ? ac.getApplier() : null,
                    DamageTypes.FIRE, 1f,
                    DamageFlags.NO_CONDITIONS, DamageFlags.SILENT);
        }
        if (comp.has(Conditions.BLEEDING) && tick == 0) {
            ActiveCondition ac = comp.get(Conditions.BLEEDING);
            TotalityDamage.hurt(entity,
                    ac != null ? ac.getApplier() : null,
                    DamageTypes.PIERCING, 1f,
                    DamageFlags.NO_CONDITIONS, DamageFlags.SILENT);
        }
        if (comp.has(Conditions.POISONED) && tick % 10 == 0) {
            ActiveCondition ac = comp.get(Conditions.POISONED);
            TotalityDamage.hurt(entity,
                    ac != null ? ac.getApplier() : null,
                    DamageTypes.POISON, 1f,
                    DamageFlags.NO_CONDITIONS, DamageFlags.SILENT);
        }
        if (comp.has(Conditions.REGENERATING) && tick % 10 == 0) {
            if (entity.getHealth() < entity.getMaxHealth()) entity.heal(0.2f);
        }
    }
}