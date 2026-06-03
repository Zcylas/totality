package zcylas.totality.init;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.mob.stats.MobCombatStatsHolder;
import zcylas.totality.api.rpg.stats.StatsServerEvents;
import zcylas.totality.init.events.CombatServerEvents;
import zcylas.totality.init.events.PlayerConnectionEvents;
import zcylas.totality.init.events.MagicServerEvents;
import zcylas.totality.init.events.VanillaDamageInterceptor;

public class ModEvents {
    public static void register() {
        PlayerComponentEvents.init();
        StatsServerEvents.register();
        MagicServerEvents.register();
        CombatServerEvents.register();       // AttackEntityCallback
        PlayerConnectionEvents.register();   // DISCONNECT cleanup
        // Add to ModEvents.register():
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living
                    && !(entity instanceof net.minecraft.world.entity.player.Player)
                    && living instanceof MobCombatStatsHolder holder) {
                holder.totality$getMobCombatStats().initialize(living);
            }
        });
        VanillaDamageInterceptor.register();
    }

    private ModEvents() {}
}
