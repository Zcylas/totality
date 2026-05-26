package zcylas.totality.networking;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.ComponentSync;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.client.config.ItemSideModeClientCache;
import zcylas.totality.client.config.SideModeClientCache;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;
import zcylas.totality.networking.alchemy.BrewResultPayload;
import zcylas.totality.networking.alchemy.OpenApothecaryTablePayload;
import zcylas.totality.networking.ancestry.OpenAncestrySelectionPayload;
import zcylas.totality.networking.config.ItemSideModeSyncPayload;
import zcylas.totality.networking.config.SideModeSyncPayload;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.mana.SyncManaPayload;
import zcylas.totality.networking.menu.OpenMainMenuPayload;
import zcylas.totality.networking.notification.SendNotificationPayload;
import zcylas.totality.networking.stamina.ClientStaminaManager;
import zcylas.totality.networking.stamina.SyncStaminaPayload;
import zcylas.totality.networking.stats.OpenStatusScreenPayload;
import zcylas.totality.screen.alchemy.ApothecaryTableScreen;
import zcylas.totality.screen.menu.MainMenuScreen;
import zcylas.totality.screen.stats.StatusScreen;

public class TotalityClientPacketHandlers {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                SideModeSyncPayload.TYPE,
                (payload, context) ->
                        SideModeClientCache.set(payload.pos(), payload.sideModes())
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SyncManaPayload.TYPE,
                (payload, context) ->
                        ClientManaManager.sync(payload.mana(), payload.maxMana())
        );
        ClientPlayNetworking.registerGlobalReceiver(
                SyncStaminaPayload.TYPE,
                (payload, context) ->
                        ClientStaminaManager.sync(payload.stamina(), payload.maxStamina())
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ItemSideModeSyncPayload.TYPE,
                (payload, context) ->
                        ItemSideModeClientCache.setAll(payload.pos(), payload.sideModes())
        );

        // Generic component sync — routes to the correct component on LocalPlayer.
        ClientPlayNetworking.registerGlobalReceiver(
                ComponentSync.PACKET_TYPE,
                (payload, context) -> {
                    var registryAccess = context.player().level().registryAccess();

                    // Route to matching component on LocalPlayer
                    ComponentRegistry.get(payload.keyId()).ifPresent(key ->
                            key.maybeGet((ComponentProvider) context.player()).ifPresent(component -> {
                                if (component instanceof SyncedComponent synced) {
                                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                                            Unpooled.wrappedBuffer(payload.data()),
                                            registryAccess
                                    );
                                    synced.applySyncPacket(buf);
                                }
                            })
                    );

                    // Dispatch to any registered client-side listeners
                    ClientComponentSyncListeners.dispatch(payload.keyId(), payload.data(), registryAccess);
                }
        );
        ClientPlayNetworking.registerGlobalReceiver(
            OpenApothecaryTablePayload.TYPE,
            (payload, context) -> Minecraft.getInstance().setScreen(new ApothecaryTableScreen())
        );
        ClientPlayNetworking.registerGlobalReceiver(
                BrewResultPayload.TYPE,
                (payload, context) -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof ApothecaryTableScreen alchemy) {
                        alchemy.onBrewResult(payload.potionName(), payload.discoveredEffects());
                    }
                }
        );
        ClientPlayNetworking.registerGlobalReceiver(
                SendNotificationPayload.TYPE,
                (payload, ctx) -> NotificationManager.add(payload.message(), payload.color()));
        ClientPlayNetworking.registerGlobalReceiver(
                OpenStatusScreenPayload.TYPE,
                (payload, ctx) -> ctx.client().setScreen(new StatusScreen()));
        ClientPlayNetworking.registerGlobalReceiver(
                OpenMainMenuPayload.TYPE,
                (payload, ctx) -> ctx.client().setScreen(new MainMenuScreen()));
        ClientPlayNetworking.registerGlobalReceiver(
                OpenAncestrySelectionPayload.TYPE,
                (payload, ctx) -> ctx.client().setScreen(
                        new zcylas.totality.screen.ancestry.SpeciesSelectionScreen()));
    }

    private TotalityClientPacketHandlers() {}
}