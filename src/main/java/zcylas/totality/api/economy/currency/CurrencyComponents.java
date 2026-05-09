package zcylas.totality.api.economy.currency;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class CurrencyComponents {

    public static final ComponentKey<WalletComponent> WALLET = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "wallet"),
            WalletComponent.class
    );

    private CurrencyComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                WALLET,
                WalletComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                WALLET,
                () -> new WalletComponent(null)
        );
    }
}