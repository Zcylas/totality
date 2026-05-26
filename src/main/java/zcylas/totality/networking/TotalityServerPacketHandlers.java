package zcylas.totality.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.industrial.energy.HasSidedEnergy;
import zcylas.totality.api.industrial.item.HasSidedItems;
import zcylas.totality.api.magic.grimoire.GrimoireCaster;
import zcylas.totality.api.magic.grimoire.MagicComponents;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.ancestry.Origin;
import zcylas.totality.api.rpg.ancestry.Species;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.item.fluid.FluidTankItem;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.networking.alchemy.BrewServerHandler;
import zcylas.totality.networking.ancestry.SelectAncestryPayload;
import zcylas.totality.networking.combat.PowerAttackPayload;
import zcylas.totality.networking.config.ItemSideModePayload;
import zcylas.totality.networking.config.SideModePayload;
import zcylas.totality.networking.fluid.FluidTankModePayload;
import zcylas.totality.networking.magic.grimoire.SwitchGrimoireSlotPayload;
import zcylas.totality.networking.magic.grimoire.UpdateGrimoirePayload;

public class TotalityServerPacketHandlers {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                FluidTankModePayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    ItemStack held = context.player().getMainHandItem();
                    if (held.getItem() instanceof FluidTankItem) {
                        FluidTankItem.setInsertMode(held, payload.insertMode());
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(
                SideModePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var level = context.player().level();
                    var be = level.getBlockEntity(payload.pos());
                    if (be instanceof HasSidedEnergy sided) {
                        sided.getEnergy().cycleSideMode(payload.face());
                        be.setChanged();
                        sided.syncSideModes(context.player(), payload.pos());
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(
                UpdateGrimoirePayload.TYPE, (payload, context) -> {
                    context.server().execute(() -> {
                        ServerPlayer player = context.player();
                        ItemStack stack     = findGrimoire(player);
                        if (!stack.isEmpty()) {
                            stack.set(MagicComponents.GRIMOIRE_CASTER, payload.caster());
                        }
                    });
                });

        ServerPlayNetworking.registerGlobalReceiver(
                SwitchGrimoireSlotPayload.TYPE, (payload, context) -> {
                    context.server().execute(() -> {
                        ServerPlayer player = context.player();
                        ItemStack stack     = findGrimoire(player);
                        if (!stack.isEmpty()) {
                            GrimoireCaster current = stack.getOrDefault(
                                    MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
                            stack.set(MagicComponents.GRIMOIRE_CASTER,
                                    current.withCurrentSlot(payload.slot()));
                        }
                    });
                });
        ServerPlayNetworking.registerGlobalReceiver(
                ItemSideModePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var level = context.player().level();
                    var be = level.getBlockEntity(payload.pos());
                    if (be instanceof HasSidedItems sided) {
                        sided.getItemSides().cycleSideMode(payload.face());
                        be.setChanged();
                        sided.syncItemSideModes(context.player(), payload.pos());
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(
                PowerAttackPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    PowerAttackManager.onPowerAttackReceived(context.player());
                }));
        BrewServerHandler.register();
    }

    private static ItemStack findGrimoire(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof GrimoireItem)
            return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof GrimoireItem)
            return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private TotalityServerPacketHandlers() {}

}
