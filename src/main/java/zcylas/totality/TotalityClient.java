package zcylas.totality;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
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
import zcylas.totality.client.renderer.entity.npc.ProvisionerNpcRenderer;
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
        zcylas.totality.client.renderer.entity.npc.ProvisionerRendererVerification.runIfDev();
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
        zcylas.totality.init.KeybindVerification.runIfDev();
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

        // Phase 3A generic Resource sync state must never leak between sessions/worlds — clear on
        // both ends of the connection lifecycle (a fresh JOIN never gets an explicit "cleared"
        // packet from the server, matching the ClientRestManager precedent immediately above).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                zcylas.totality.networking.resource.ClientResourceSyncManager.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                zcylas.totality.networking.resource.ClientResourceSyncManager.clear());

        // Connection JOIN/DISCONNECT alone misses one case: a dimension change (Nether portal,
        // /execute in, respawn anchor, ...) replaces the client's ClientLevel while the same play
        // connection stays open — no JOIN/DISCONNECT fires for that. ClientLevelEvents.
        // AFTER_CLIENT_LEVEL_CHANGE fires whenever Minecraft.setLevel(...) installs a new non-null
        // ClientLevel (both the very first level on join and every subsequent dimension change),
        // never when the level is torn down to null on disconnect — that half is already covered by
        // DISCONNECT above. Ordering is safe: on a dimension change, the server only schedules its
        // fresh full snapshot via ResourceSyncLifecycleEvents' AFTER_PLAYER_CHANGE_LEVEL listener,
        // which is not sent until that server's next tick flush — strictly after this client-side
        // level swap has already happened — so clearing here can never race ahead of and erase a
        // full snapshot that arrives afterward.
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) ->
                zcylas.totality.networking.resource.ClientResourceSyncManager.clear());

        // Drives ClientResyncRequestGate's bounded retry (external-review correction, 2026-07-22):
        // the server's own resync-request rate limiter silently drops anything more frequent than
        // once per 100 ticks, so a request dropped that way needs this tick-driven retry to avoid
        // permanently stranding the client's single-flight gate — see ClientResourceSyncManager.tick()
        // and ClientResyncRequestGate.tick(). Reuses the existing END_CLIENT_TICK event already
        // registered twice above (FluidTankScrollHandler, MobHealthBarHud) rather than adding a new
        // tick-loop mechanism.
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                zcylas.totality.networking.resource.ClientResourceSyncManager.tick());

        // Phase 3B-1: registers the presentation-only client Resource query façade's reader
        // strategies. Registration only — no production consumer reads ClientResourceService yet
        // (see TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md).
        zcylas.totality.client.resource.TotalityClientResourceReaders.register();

        // Phase 3B-2B shadow-parity lifecycle reset — an independent hook alongside the
        // ClientResourceSyncManager registrations above; ClientResourceSyncManager itself never
        // depends on parity existing. A fresh JOIN never gets an explicit "cleared" packet, and
        // death/respawn (a LocalPlayer identity change without JOIN/DISCONNECT) is separately
        // caught by ClientResourceParityLifecycle's own player-identity check inside tick() below.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                zcylas.totality.client.resource.parity.ClientResourceParityCoordinator.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                zcylas.totality.client.resource.parity.ClientResourceParityCoordinator.clear());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) ->
                zcylas.totality.client.resource.parity.ClientResourceParityCoordinator.clear());

        // Phase 3B-2B: polls all four eligible parity pairs (Mana/Stamina/spell slots/Rage) once
        // per END_CLIENT_TICK, registered strictly after ClientResourceSyncManager's own tick/resync
        // handling above — parity only ever observes that tick's already-settled generic and legacy
        // state, never influences either. Purely diagnostic: no logging, no gameplay effect, and no
        // consumer reads these observations yet (see ClientResourceParityObservations).
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                zcylas.totality.client.resource.parity.ClientResourceParityCoordinator.tick());

        // Phase 3B-2C: bounded DEBUG diagnostic logging for a Resource's transition into, or
        // recovery out of, PERSISTENT_MISMATCH — a separate observer of ClientResourceParityObservations'
        // read-only snapshot, never a modification of the coordinator/poll/tracker above. Its own
        // transition memory is connection-scoped (external-review correction, Phase 3B-2C correction
        // pass): cleared only on JOIN/DISCONNECT, deliberately NOT on AFTER_CLIENT_LEVEL_CHANGE — a
        // persistent-mismatch logging episode must survive a dimension change (and a respawn's fresh
        // LocalPlayer) within the same connection so a mismatch that continues across the transition
        // is never logged as a second entry, and so a later recovery within that same connection can
        // still be correlated back to the episode that was actually reported. See
        // ClientResourceParityLogObserver's own Javadoc for the full reasoning.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                zcylas.totality.client.resource.parity.ClientResourceParityLogObserver.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                zcylas.totality.client.resource.parity.ClientResourceParityLogObserver.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                zcylas.totality.client.resource.parity.ClientResourceParityLogObserver.tick());

        // Phase 3B-3: development-only on-demand parity inspection command (/totalitydebug resource
        // parity — deliberately NOT under /totality, which is the existing server command tree's own
        // root; a manual-validation finding confirmed a shared client-side root literal intercepts
        // and breaks every other server-side /totality branch). No-op outside a Fabric development
        // environment — see
        // ClientResourceParityInspectionCommand's own Javadoc for the exact gating contract. Purely
        // read-only: reuses the existing trusted façade (ClientResourceService) and the existing
        // read-only parity snapshot (ClientResourceParityObservations); never mutates a Resource,
        // never touches ClientResourceParityLogObserver's persistent-mismatch episode memory.
        zcylas.totality.client.resource.parity.ClientResourceParityInspectionCommand.registerIfDevelopmentEnvironment();
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
        zcylas.totality.client.renderer.hud.notification.NotificationTimingVerification.runIfDev();
        zcylas.totality.client.renderer.hud.PowerAttackFlash.register();
        zcylas.totality.client.renderer.hud.PowerAttackFlashVerification.runIfDev();
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
        // Phase 4, Part A: dedicated male/female textures, following the same pattern as the
        // Banker (design document Section 8 still holds — gender+name identity only, no new
        // skin-category axis; this is simply the Provisioner's own texture pair, not a second axis).
        EntityRenderers.register(ModEntities.PROVISIONER, ProvisionerNpcRenderer::new);
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