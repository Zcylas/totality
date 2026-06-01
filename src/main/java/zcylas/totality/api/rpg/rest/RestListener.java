package zcylas.totality.api.rpg.rest;

import net.minecraft.server.level.ServerPlayer;

public interface RestListener {

    void onRest(ServerPlayer player, RestType type);

    /**
     * Lower number = higher priority.
     * Resources should restore before abilities (0 before 10).
     */
    default int restPriority() { return 10; }
}