package zcylas.totality;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.client.handler.FluidTankScrollHandler;
import zcylas.totality.client.renderer.energy.SidedOverlayRenderer;
import zcylas.totality.client.renderer.entity.GrimoireProjectileRenderer;
import zcylas.totality.client.renderer.fluid.FluidTankRenderer;
import zcylas.totality.client.renderer.fluid.FluidTankSpecialRenderer;
import zcylas.totality.client.renderer.mana.ManaHudRenderer;
import zcylas.totality.init.ModBlockEntities;
import zcylas.totality.init.ModEffects;
import zcylas.totality.init.ModEntities;
import zcylas.totality.init.ModKeybinds;
import zcylas.totality.item.fluid.FluidTankItem;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.menu.energy.EnergyCellMenu;
import zcylas.totality.menu.generator.GeneratorMenu;
import zcylas.totality.networking.TotalityClientPacketHandlers;
import zcylas.totality.networking.fluid.FluidTankModePayload;
import zcylas.totality.screen.energy.EnergyCellScreen;
import zcylas.totality.screen.generator.GeneratorScreen;
import zcylas.totality.screen.magic.GrimoireRadialScreen;
import zcylas.totality.screen.magic.GrimoireScreen;

public class TotalityClient implements ClientModInitializer {
    private static int scrollCooldown = 0;
    private static final int SCROLL_COOLDOWN_TICKS = 5;


    @Override
    public void onInitializeClient() {
        registerRenderers();
        registerScreens();
        registerSpecialRenderers();
        registerEntityRenderers();
        registerKeybinds();
        registerEffects();
        TotalityClientPacketHandlers.register();
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> FluidTankScrollHandler.tick());
        SidedOverlayRenderer.register();
    }

    private void registerRenderers(){
        BlockEntityRenderers.register(
                ModBlockEntities.FLUID_TANK,
                FluidTankRenderer::new
        );
        ManaHudRenderer.register();

    }

    private void registerEntityRenderers(){
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                ModEntities.GRIMOIRE_PROJECTILE,
                GrimoireProjectileRenderer::new);
    }

    private void registerSpecialRenderers(){
        SpecialModelRenderers.ID_MAPPER.put(
                Identifier.fromNamespaceAndPath("totality", "fluid_tank"),
                FluidTankSpecialRenderer.Unbaked.CODEC);
    }

    private void registerEffects(){

        net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents.CUSTOM.register(
                (entity, tickElytra) -> entity.hasEffect(ModEffects.GLIDE));

    }

    private void registerScreens(){
        MenuScreens.register(GeneratorMenu.TYPE, GeneratorScreen::new);
        MenuScreens.register(EnergyCellMenu.TYPE, EnergyCellScreen::new);
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

        // Sync to server
        ClientPlayNetworking.send(new FluidTankModePayload(insert));

        client.player.sendOverlayMessage(
                Component.literal(insert ? "[INSERT]" : "[EXTRACT]")
                        .withStyle(insert ? ChatFormatting.GREEN : ChatFormatting.GOLD));

        return true;
    }

    public static void registerKeybinds(){
        ModKeybinds.register();
// Handle keybind press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_GRIMOIRE.consumeClick()) {
                if (client.player == null) return;
                ItemStack main = client.player.getMainHandItem();
                ItemStack off  = client.player.getOffhandItem();
                ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                        : off.getItem() instanceof GrimoireItem ? off
                        : ItemStack.EMPTY;
                if (!grimoire.isEmpty()) {
                    client.setScreen(new GrimoireScreen(grimoire));
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_RADIAL.consumeClick()) {
                if (client.player == null) return;
                ItemStack main = client.player.getMainHandItem();
                ItemStack off  = client.player.getOffhandItem();
                ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                        : off.getItem() instanceof GrimoireItem ? off
                        : ItemStack.EMPTY;
                if (!grimoire.isEmpty()) {
                    client.setScreen(new GrimoireRadialScreen(grimoire));
                }
            }
        });
    }

}
