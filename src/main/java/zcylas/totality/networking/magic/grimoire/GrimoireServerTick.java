package zcylas.totality.networking.magic.grimoire;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.magic.GrimoireCaster;
import zcylas.totality.api.magic.MagicComponents;
import zcylas.totality.item.magic.GrimoireItem;

public class GrimoireServerTick {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncGrimoireHud(player);
            }
        });
    }

    public static void syncGrimoireHud(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off  = player.getOffhandItem();

        ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                : off.getItem() instanceof GrimoireItem ? off
                : ItemStack.EMPTY;

        if (grimoire.isEmpty()) {
            ServerPlayNetworking.send(player, new SyncGrimoireHudPayload(0, ""));
            return;
        }

        GrimoireCaster caster = grimoire.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);

        ServerPlayNetworking.send(player, new SyncGrimoireHudPayload(
                caster.currentSlot(),
                caster.spellName().isEmpty() ? "Unnamed" : caster.spellName()));
    }

    private GrimoireServerTick() {}
}