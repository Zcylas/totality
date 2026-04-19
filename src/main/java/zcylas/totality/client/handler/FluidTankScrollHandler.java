package zcylas.totality.client.handler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.item.fluid.FluidTankItem;
import zcylas.totality.networking.fluid.FluidTankModePayload;

public class FluidTankScrollHandler {

    private static int scrollCooldown = 0;
    private static final int SCROLL_COOLDOWN_TICKS = 5;

    public static void tick() {
        if (scrollCooldown > 0) scrollCooldown--;
    }

    public static boolean onScroll(double scrollDelta) {
        if (scrollCooldown > 0) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        if (!client.player.isShiftKeyDown()) return false;
        if (scrollDelta == 0) return false;

        ItemStack held = client.player.getMainHandItem();
        if (!(held.getItem() instanceof FluidTankItem)) return false;

        FluidTankItem.toggleMode(held);
        scrollCooldown = SCROLL_COOLDOWN_TICKS;

        boolean insert = FluidTankItem.isInsertMode(held);
        ClientPlayNetworking.send(new FluidTankModePayload(insert));

        client.player.sendOverlayMessage(
                Component.literal(insert ? "[INSERT]" : "[EXTRACT]")
                        .withStyle(insert ? ChatFormatting.GREEN : ChatFormatting.GOLD));

        return true;
    }

    private FluidTankScrollHandler() {}
}