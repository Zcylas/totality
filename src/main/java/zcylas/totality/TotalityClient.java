package zcylas.totality;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.ClientClassManager;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.client.color.PotionTintSource;
import zcylas.totality.client.combat.CombatTextRenderer;
import zcylas.totality.client.handler.FluidTankScrollHandler;
import zcylas.totality.client.hud.resource.ISecondaryResource;
import zcylas.totality.client.hud.resource.SecondaryResourceRegistry;
import zcylas.totality.client.renderer.ability.HeatVisionBeamRenderer;
import zcylas.totality.client.renderer.energy.SidedOverlayRenderer;
import zcylas.totality.client.renderer.entity.GrimoireProjectileRenderer;
import zcylas.totality.client.renderer.entity.npc.BankerNpcRenderer;
import zcylas.totality.client.renderer.entity.npc.TotalityNpcRenderer;
import zcylas.totality.client.renderer.entity.magic.SpellBoltRenderer;
import zcylas.totality.client.renderer.entity.basicweapon.ThrownShurikenRenderer;
import zcylas.totality.client.renderer.fluid.FluidTankRenderer;
import zcylas.totality.client.renderer.fluid.FluidTankSpecialRenderer;
import zcylas.totality.client.renderer.hud.MobHealthBarHud;
import zcylas.totality.client.renderer.hud.TotalityHudRenderer;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;
import zcylas.totality.client.renderer.ritual.RitualAltarRenderer;
import zcylas.totality.client.renderer.ritual.RitualDaisRenderer;
import zcylas.totality.init.*;
import zcylas.totality.item.fluid.FluidTankItem;
import zcylas.totality.menu.energy.ElectricFurnaceMenu;
import zcylas.totality.menu.energy.EnergyCellMenu;
import zcylas.totality.menu.generator.GeneratorMenu;
import zcylas.totality.networking.TotalityClientPacketHandlers;
import zcylas.totality.networking.fluid.FluidTankModePayload;
import zcylas.totality.screen.energy.ElectricFurnaceScreen;
import zcylas.totality.screen.energy.EnergyCellScreen;
import zcylas.totality.screen.generator.GeneratorScreen;

import java.awt.*;

public class TotalityClient implements ClientModInitializer {
    private static int scrollCooldown = 0;
    private static final int SCROLL_COOLDOWN_TICKS = 5;


    @Override
    public void onInitializeClient() {
        // ── Renderers ─────────────────────────────────────────────────────────
        registerRenderers();
        registerEntityRenderers();
        registerSpecialRenderers();
        SidedOverlayRenderer.register();

        // ── Screens ───────────────────────────────────────────────────────────
        net.minecraft.client.gui.screens.MenuScreens.register(
                zcylas.totality.menu.ComponentPouchMenu.TYPE,
                zcylas.totality.screen.pouch.ComponentPouchScreen::new);
        registerScreens();

        // ── Colors & tints ────────────────────────────────────────────────────
        registerTintSources();
        TotalityBlockColors.register();

        // ── Effects ───────────────────────────────────────────────────────────
        registerEffects();

        // ── Networking ────────────────────────────────────────────────────────
        TotalityClientPacketHandlers.register();
        TotalityClientSyncListeners.register();

        // ── Keybinds & tick handlers ──────────────────────────────────────────
        TotalityKeybindHandlers.register();
        TotalityMovementHandler.register();
        zcylas.totality.client.item.AttunementClientManager.register();
        zcylas.totality.client.spell.ClientCastManager.register();
        zcylas.totality.client.spell.CastBarHud.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> FluidTankScrollHandler.tick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> MobHealthBarHud.tick());

        // A fresh join never gets an explicit "cleared" rest sync from the server (it only sends
        // one when a rest actually starts/changes), so without this a rest HUD/forced camera left
        // over from a previous world/session would otherwise be stuck forever.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                zcylas.totality.client.rest.ClientRestManager.reset());
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
        zcylas.totality.client.hud.rest.RestHud.register();
        NotificationManager.register();
        zcylas.totality.client.quest.QuestTrackerHud.register();
        MobHealthBarHud.register();
        CombatTextRenderer.register();
        HeatVisionBeamRenderer.register();

        SecondaryResourceRegistry.register(new ISecondaryResource() {
            @Override public String getName() { return "Rage"; }
            @Override public int getCurrent(Minecraft client) {
                try { return ChargeComponents.PLAYER_CHARGES
                        .get((ComponentProvider) client.player)
                        .getCurrent(BarbarianRageAbility.CHARGE_ID);
                } catch (Exception e) { return 0; }
            }
            @Override public int getMax(Minecraft client) {
                try { return ChargeComponents.PLAYER_CHARGES
                        .get((ComponentProvider) client.player)
                        .getMax(BarbarianRageAbility.CHARGE_ID);
                } catch (Exception e) { return 0; }
            }
            @Override public int getColor() { return 0xFFCC3333; }
            @Override public boolean shouldShow(Minecraft client) {
                return ClientClassManager.hasClass()
                        && TotalityClasses.BARBARIAN_ID.equals(
                        ClientClassManager.getPrimaryClassId());
            }
            @Override public net.minecraft.resources.Identifier getActivePipSprite() {
                return zcylas.totality.client.gui.TotalityGuiSprites.HUD_RAGE_PIP;
            }
            @Override public net.minecraft.resources.Identifier getSpentPipSprite() {
                return zcylas.totality.client.gui.TotalityGuiSprites.HUD_RAGE_PIP_SPENT;
            }
        });
    }

    private void registerEntityRenderers(){
        EntityRenderers.register(
                ModEntities.GRIMOIRE_PROJECTILE,
                GrimoireProjectileRenderer::new);
        EntityRenderers.register(
                ModEntities.SPELL_BOLT,
                SpellBoltRenderer::new);
        EntityRenderers.register(
                ModEntities.FIREBALL_PROJECTILE,
                NoopRenderer::new);
        EntityRenderers.register(
                ModEntities.ORBIT_PROJECTILE,
                NoopRenderer::new);
        EntityRenderers.register(ModEntities.LINGER_ENTITY,
                NoopRenderer::new);
        EntityRenderers.register(ModEntities.SUMMON_SKELETON,
                SkeletonRenderer::new);

        EntityRenderers.register(ModEntities.TOTALITY_NPC, TotalityNpcRenderer::new);
        EntityRenderers.register(ModEntities.BANKER, BankerNpcRenderer::new);
        EntityRenderers.register(ModEntities.REST_SEAT, NoopRenderer::new);

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
        MenuScreens.register(
                zcylas.totality.menu.equipment.AccessoryInventoryMenu.TYPE,
                zcylas.totality.screen.equipment.AccessoryInventoryScreen::new);

        // Inject ring button into vanilla InventoryScreen
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
                (client, screen, scaledWidth, scaledHeight) -> {
                    if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen invScreen) {
                        ((zcylas.totality.mixin.ScreenAccessor)(Object) invScreen).totality$addRenderableWidget(
                                new zcylas.totality.screen.equipment.AccessoryInventoryButton(invScreen));
                    }
                });
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

    public void registerTintSources(){
        ItemTintSources.ID_MAPPER.put(
                Identifier.fromNamespaceAndPath("totality", "potion_color"),
                PotionTintSource.MAP_CODEC
        );
    }
}