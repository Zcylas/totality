package zcylas.totality.client.item;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.item.TotalityItem;
import zcylas.totality.networking.item.AttunementPayload;
import zcylas.totality.networking.item.UnAttunePayload;

public final class AttunementClientManager {

    public static final int ATTUNE_TICKS = 200; // 10 seconds

    private static int     progress    = 0;
    private static int     lastSlot    = -1;
    private static boolean attuning    = false;
    /** True when the player is un-attuning (holding R on an already-attuned item). */
    private static boolean unattuning  = false;

    private AttunementClientManager() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AttunementClientManager::tick);
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.screen == null) { reset(); return; }

        com.mojang.blaze3d.platform.Window window = client.getWindow();
        boolean rDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                window, org.lwjgl.glfw.GLFW.GLFW_KEY_R);
        if (!rDown) { reset(); return; }

        int slot = getHoveredSlot(client);
        if (slot < 0) { reset(); return; }

        ItemStack stack = getStackInSlot(client, slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof TotalityItem ti)) { reset(); return; }
        if (!ti.requiresAttunement()) { reset(); return; }

        boolean alreadyAttuned = ti.isAttuned(stack, client.player);

        if (slot != lastSlot) { progress = 0; lastSlot = slot; }

        attuning   = !alreadyAttuned;
        unattuning =  alreadyAttuned;
        progress++;

        if (progress >= ATTUNE_TICKS) {
            if (alreadyAttuned) {
                ClientPlayNetworking.send(new UnAttunePayload(slot));
            } else {
                ClientPlayNetworking.send(new AttunementPayload(slot));
            }
            reset();
        }
    }

    private static void reset() {
        progress   = 0;
        lastSlot   = -1;
        attuning   = false;
        unattuning = false;
    }

    // ── HUD query ─────────────────────────────────────────────────────────────

    public static boolean isAttuning()   { return attuning; }
    public static boolean isUnattuning() { return unattuning; }
    public static boolean isActive()     { return attuning || unattuning; }
    public static int     getProgress()  { return progress; }
    public static float   getFraction()  { return (float) progress / ATTUNE_TICKS; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int getHoveredSlot(Minecraft client) {
        if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            var slot = ((zcylas.totality.mixin.client.AbstractContainerScreenAccessor) screen)
                    .totality$getHoveredSlot();
            return slot != null ? slot.index : -1;
        }
        return -1;
    }

    private static ItemStack getStackInSlot(Minecraft client, int slotIndex) {
        if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            var slots = screen.getMenu().slots;
            if (slotIndex >= 0 && slotIndex < slots.size())
                return slots.get(slotIndex).getItem();
        }
        return ItemStack.EMPTY;
    }
}