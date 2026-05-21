package zcylas.totality.init;

import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.rpg.stats.StatsServerEvents;
import zcylas.totality.init.events.CombatServerEvents;
import zcylas.totality.init.events.PlayerConnectionEvents;
import zcylas.totality.init.events.MagicServerEvents;

public class ModEvents {
    public static void register() {
        PlayerComponentEvents.init();
        StatsServerEvents.register();
        MagicServerEvents.register();
        CombatServerEvents.register();       // AttackEntityCallback
        PlayerConnectionEvents.register();   // DISCONNECT cleanup
    }
    private ModEvents() {}
}
