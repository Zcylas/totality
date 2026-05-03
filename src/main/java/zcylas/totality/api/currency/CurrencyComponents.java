package zcylas.totality.api.currency;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.component.ComponentKey;
import zcylas.totality.api.component.ComponentRegistry;
import zcylas.totality.api.component.PlayerComponentEvents;
import zcylas.totality.api.component.RespawnStrategy;
import zcylas.totality.api.currency.WalletComponent;

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