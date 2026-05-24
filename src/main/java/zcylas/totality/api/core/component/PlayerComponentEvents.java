package zcylas.totality.api.core.component;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.networking.ancestry.OpenAncestrySelectionPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PlayerComponentEvents {

    private record Registration<C extends TotalityComponent>(
            ComponentKey<C> key,
            Function<ServerPlayer, C> factory,
            RespawnStrategy<? super C> respawnStrategy
    ) {}

    private record ClientRegistration<C extends TotalityComponent>(
            ComponentKey<C> key,
            Supplier<C> factory
    ) {}

    private static final List<Registration<?>> REGISTRATIONS = new ArrayList<>();
    private static final List<ClientRegistration<?>> CLIENT_REGISTRATIONS = new ArrayList<>();

    private PlayerComponentEvents() {}

    public static <C extends TotalityComponent> void registerForPlayers(
            ComponentKey<C> key,
            Function<ServerPlayer, C> factory,
            RespawnStrategy<? super C> respawnStrategy
    ) {
        REGISTRATIONS.add(new Registration<>(key, factory, respawnStrategy));
    }

    public static <C extends TotalityComponent> void registerClientComponent(
            ComponentKey<C> key,
            Supplier<C> factory
    ) {
        CLIENT_REGISTRATIONS.add(new ClientRegistration<>(key, factory));
    }

    public static void attachComponentsTo(ServerPlayer player) {
        ComponentContainer container = ((ComponentProvider) player).getComponentContainer();
        for (var reg : REGISTRATIONS) {
            attach(reg, player, container);
        }
    }

    public static void attachClientComponentsTo(ComponentContainer container) {
        for (var reg : CLIENT_REGISTRATIONS) {
            attachClient(reg, container);
        }
    }

    public static void init() {
        ServerPlayerEvents.JOIN.register(player -> {
            // Sync all components
            for (var reg : REGISTRATIONS) {
                reg.key().maybeGet((ComponentProvider) player).ifPresent(component -> {
                    if (component instanceof SyncedComponent) {
                        reg.key().sync((ComponentProvider) player);
                    }
                });
            }
            player.refreshDimensions();

            // Open ancestry selection if not yet chosen
            if (!AncestryComponents.get(player).hasAncestry()) {
                ServerPlayNetworking.send(player, new OpenAncestrySelectionPayload());
            }
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            HolderLookup.Provider registries = newPlayer.level().registryAccess();
            boolean keepInventory = oldPlayer.level().getGameRules()
                    .get(GameRules.KEEP_INVENTORY);
            for (var reg : REGISTRATIONS) {
                copyComponent(reg, oldPlayer, newPlayer, registries, alive, keepInventory);
            }
        });
    }

    private static <C extends TotalityComponent> void attach(
            Registration<C> reg,
            ServerPlayer player,
            ComponentContainer container
    ) {
        if (container.get(reg.key()) == null) {
            container.put(reg.key(), reg.factory().apply(player));
        }
    }

    private static <C extends TotalityComponent> void attachClient(
            ClientRegistration<C> reg,
            ComponentContainer container
    ) {
        if (container.get(reg.key()) == null) {
            container.put(reg.key(), reg.factory().get());
        }
    }

    @SuppressWarnings("unchecked")
    private static <C extends TotalityComponent> void copyComponent(
            Registration<C> reg,
            ServerPlayer from,
            ServerPlayer to,
            HolderLookup.Provider registries,
            boolean lossless,
            boolean keepInventory
    ) {
        C fromComp = reg.key().maybeGet((ComponentProvider) from).orElse(null);
        C toComp   = reg.key().maybeGet((ComponentProvider) to).orElse(null);
        if (fromComp == null || toComp == null) return;

        ((RespawnStrategy<C>) reg.respawnStrategy())
                .onRespawn(fromComp, toComp, registries, lossless, keepInventory);
    }

    public static int getClientRegistrationsSize() {
        return CLIENT_REGISTRATIONS.size();
    }
}