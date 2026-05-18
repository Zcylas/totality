package zcylas.totality.networking.ability.veinminer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VeinminerKeyHandler {

    private static final Set<UUID> HOLDING = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                VeinminerKeyPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    if (payload.held()) {
                        HOLDING.add(player.getUUID());
                    } else {
                        HOLDING.remove(player.getUUID());
                    }
                }
        );
    }

    public static boolean isHolding(ServerPlayer player) {
        return HOLDING.contains(player.getUUID());
    }

    /** Clean up on disconnect. */
    public static void onPlayerLeave(ServerPlayer player) {
        HOLDING.remove(player.getUUID());
    }

    private VeinminerKeyHandler() {}
}