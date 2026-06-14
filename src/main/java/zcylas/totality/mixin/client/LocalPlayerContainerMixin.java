package zcylas.totality.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zcylas.totality.screen.equipment.AccessoryInventoryScreen;

/**
 * When the server opens a new container (e.g. AccessoryInventoryScreen) it first sends
 * ClientboundContainerClosePacket, which triggers clientSideCloseContainer() → mc.setScreen(null).
 * That null-screen flash is visible as a half-second "looking forward" glitch in the entity render.
 * Suppress the setScreen(null) call when we are currently showing an inventory-style screen that
 * is about to be replaced — the incoming ClientboundOpenScreenPacket will set the correct screen.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerContainerMixin {

    @Redirect(
            method = "clientSideCloseContainer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void suppressNullScreenFlash(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        // screen is always null here (closing inventory goes to null).
        // Suppress the flash when we're on either side of a screen swap:
        // - AccessoryInventoryScreen → InventoryScreen: server fires doCloseContainer() for the
        //   accessory menu, sending ContainerClosePacket that arrives while InventoryScreen is open.
        // - InventoryScreen → AccessoryInventoryScreen: server's openMenu() sends ContainerClosePacket
        //   for the previous container before ClientboundOpenScreenPacket.
        // In vanilla, clientSideCloseContainer() is never called while InventoryScreen is showing
        // (InventoryMenu has id 0, server never sends close for it), so suppressing here is safe.
        if (mc.screen instanceof AccessoryInventoryScreen || mc.screen instanceof InventoryScreen) {
            return;
        }
        mc.setScreen(screen);
    }
}