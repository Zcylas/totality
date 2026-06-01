// api/combat/condition/ConditionServerTick.java
package zcylas.totality.api.combat.condition;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ConditionServerTick {

    private ConditionServerTick() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ConditionServerTick::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living) {
                    ConditionComponent.get(living).tick();
                    // TODO: apply per-tick effects here (BURNING deals fire damage etc.)
                    // TODO: sync to client if changed
                }
            }
        }
    }
}