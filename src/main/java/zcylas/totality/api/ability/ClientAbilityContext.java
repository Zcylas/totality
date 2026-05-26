package zcylas.totality.api.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only interface for abilities that provide a crosshair context prompt.
 * Only implement this on the client side — never referenced from server code.
 */
public interface ClientAbilityContext {
    @Nullable AbilityContext getContext(Minecraft mc, LocalPlayer player);
}