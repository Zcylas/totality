package zcylas.totality;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.client.color.PotionTintSource;
import zcylas.totality.client.handler.FluidTankScrollHandler;
import zcylas.totality.client.renderer.energy.SidedOverlayRenderer;
import zcylas.totality.client.renderer.entity.GrimoireProjectileRenderer;
import zcylas.totality.client.renderer.entity.basicweapon.ThrownShurikenRenderer;
import zcylas.totality.client.renderer.fluid.FluidTankRenderer;
import zcylas.totality.client.renderer.fluid.FluidTankSpecialRenderer;
import zcylas.totality.client.renderer.hud.TotalityHudRenderer;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;
import zcylas.totality.client.renderer.ritual.RitualAltarRenderer;
import zcylas.totality.client.renderer.ritual.RitualDaisRenderer;
import zcylas.totality.init.*;
import zcylas.totality.item.fluid.FluidTankItem;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.menu.energy.ElectricFurnaceMenu;
import zcylas.totality.menu.energy.EnergyCellMenu;
import zcylas.totality.menu.generator.GeneratorMenu;
import zcylas.totality.networking.TotalityClientPacketHandlers;
import zcylas.totality.networking.ability.ActivateAbilityPayload;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyPayload;
import zcylas.totality.networking.fluid.FluidTankModePayload;
import zcylas.totality.screen.energy.ElectricFurnaceScreen;
import zcylas.totality.screen.energy.EnergyCellScreen;
import zcylas.totality.screen.generator.GeneratorScreen;
import zcylas.totality.screen.magic.GrimoireRadialScreen;
import zcylas.totality.screen.magic.GrimoireScreen;
import zcylas.totality.screen.menu.MainMenuScreen;

import java.awt.*;

public class TotalityClient implements ClientModInitializer {
    private static int scrollCooldown = 0;
    private static final int SCROLL_COOLDOWN_TICKS = 5;
    private static boolean lastAbilityKeyHeld = false;


    @Override
    public void onInitializeClient() {
        registerRenderers();
        registerScreens();
        registerSpecialRenderers();
        registerEntityRenderers();
        registerKeybinds();
        registerEffects();
        registerTintSources();
        registerBlockColors();
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
        BlockEntityRenderers.register(
                ModBlockEntities.RITUAL_ALTAR,
                RitualAltarRenderer::new
        );
        BlockEntityRenderers.register(
                ModBlockEntities.RITUAL_DAIS,
                RitualDaisRenderer::new
        );
        TotalityHudRenderer.register();
        NotificationManager.register();
    }

    private void registerEntityRenderers(){
        EntityRenderers.register(
                ModEntities.GRIMOIRE_PROJECTILE,
                GrimoireProjectileRenderer::new);
        EntityRenderers.register(
                ModEntities.ORBIT_PROJECTILE,
                NoopRenderer::new);
        EntityRenderers.register(ModEntities.LINGER_ENTITY,
                NoopRenderer::new);
        EntityRenderers.register(ModEntities.SUMMON_SKELETON,
                SkeletonRenderer::new);

        //Basic Weapons
            //Shuriken
        EntityRenderers.register(
                ModEntities.THROWN_SHURIKEN,
                ThrownShurikenRenderer::new);
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
        MenuScreens.register(ElectricFurnaceMenu.TYPE, ElectricFurnaceScreen::new);
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
    private void registerBlockColors() {
        net.minecraft.client.color.block.BlockTintSource chalkTint = new net.minecraft.client.color.block.BlockTintSource() {
            @Override
            public int color(net.minecraft.world.level.block.state.BlockState state) {
                return state.getValue(zcylas.totality.block.ritual.ChalkBlock.COLOR).getTint();
            }

            @Override
            public java.util.Set<net.minecraft.world.level.block.state.properties.Property<?>> relevantProperties() {
                return java.util.Set.of(zcylas.totality.block.ritual.ChalkBlock.COLOR);
            }
        };

        net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
                java.util.List.of(chalkTint),
                zcylas.totality.init.blocks.RitualBlocks.CHALK
        );
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
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_MENU.consumeClick()) {
                if (client.player == null) return;
                // Only open if no screen is currently open
                if (client.screen == null) {
                    client.setScreen(new MainMenuScreen());
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.USE_ABILITY.consumeClick()) {
                if (client.player == null || client.level == null) return;

                Identifier equippedId = ClientAbilityManager.getEquippedAbility();
                if (equippedId == null) return;
                if (ClientAbilityManager.isOnCooldown(equippedId)) return;

                Ability targeted = AbilityRegistry.get(equippedId);
                if (targeted == null) return;

                AbilityContext context = targeted.getContext(client, client.player);

                ClientPlayNetworking.send(new ActivateAbilityPayload(
                        targeted.getId(),
                        context != null ? context.pos() : null
                ));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean held = ModKeybinds.USE_ABILITY.isDown();
            // Only send packet when state changes
            if (held != lastAbilityKeyHeld) {
                lastAbilityKeyHeld = held;
                ClientPlayNetworking.send(new VeinminerKeyPayload(held));
            }
        });
    }


    public void registerTintSources(){
        ItemTintSources.ID_MAPPER.put(
                Identifier.fromNamespaceAndPath("totality", "potion_color"),
                PotionTintSource.MAP_CODEC
        );
    }
}

