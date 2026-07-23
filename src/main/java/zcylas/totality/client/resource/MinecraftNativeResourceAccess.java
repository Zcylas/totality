package zcylas.totality.client.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import zcylas.totality.api.rpg.resources.client.NativeResourceAccess;

/**
 * Thin, singleton {@link NativeResourceAccess} wrapping {@link Minecraft#getInstance()}'s active
 * client world and local player. Deliberately minimal — all value normalization and result
 * construction lives in the pure {@code NativeClientResourceReader}, not here, so only this thin
 * wrapper is untestable without a running client (mirroring why {@code ClientResourceSyncManager}
 * itself is not directly unit-tested in this codebase).
 */
@Environment(EnvType.CLIENT)
public final class MinecraftNativeResourceAccess implements NativeResourceAccess {

    public static final MinecraftNativeResourceAccess INSTANCE = new MinecraftNativeResourceAccess();

    private MinecraftNativeResourceAccess() {}

    private static LocalPlayer player() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return null;
        }
        return client.player;
    }

    @Override
    public boolean hasLocalPlayer() {
        return player() != null;
    }

    @Override
    public float health() {
        return player().getHealth();
    }

    @Override
    public float maxHealth() {
        return player().getMaxHealth();
    }

    @Override
    public int foodLevel() {
        return player().getFoodData().getFoodLevel();
    }

    @Override
    public int airSupply() {
        return player().getAirSupply();
    }

    @Override
    public int maxAirSupply() {
        return player().getMaxAirSupply();
    }
}
