package zcylas.totality.init;

import net.minecraft.resources.Identifier;
import zcylas.totality.networking.ClientComponentSyncListeners;
import zcylas.totality.networking.currency.ClientWalletManager;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.movement.ClientMovementManager;

import java.util.*;

public final class TotalityClientSyncListeners {

    public static void register() {
        // Wallet
        ClientComponentSyncListeners.register(
                Identifier.fromNamespaceAndPath("totality", "wallet"),
                buf -> ClientWalletManager.sync(buf.readLong())
        );

        // Abilities
        ClientComponentSyncListeners.register(
                Identifier.fromNamespaceAndPath("totality", "abilities"),
                buf -> {
                    // Read unlocked
                    int unlockedCount = buf.readInt();
                    Set<Identifier> unlocked = new HashSet<>();
                    for (int i = 0; i < unlockedCount; i++) {
                        unlocked.add(buf.readIdentifier());
                    }
                    // Read cooldowns
                    int cooldownCount = buf.readInt();
                    Map<Identifier, Integer> cooldowns = new HashMap<>();
                    for (int i = 0; i < cooldownCount; i++) {
                        Identifier id = buf.readIdentifier();
                        int ticks = buf.readInt();
                        cooldowns.put(id, ticks);
                    }
                    // Read equipped
                    Identifier equipped = buf.readBoolean() ? buf.readIdentifier() : null;
                    // Read favorites
                    int favCount = buf.readInt();
                    List<Identifier> favorites = new ArrayList<>();
                    for (int i = 0; i < favCount; i++) {
                        favorites.add(buf.readIdentifier());
                    }
                    ClientAbilityManager.sync(unlocked, cooldowns, equipped, favorites);
                }
        );
        ClientComponentSyncListeners.register(
                Identifier.fromNamespaceAndPath("totality", "movement"),
                buf -> ClientMovementManager.sync(buf.readBoolean())
        );
    }

    private TotalityClientSyncListeners() {}
}