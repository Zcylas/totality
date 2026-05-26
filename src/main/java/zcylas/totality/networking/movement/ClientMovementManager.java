package zcylas.totality.networking.movement;

import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.core.movement.MovementModeProvider;
import zcylas.totality.networking.ability.ClientAbilityManager;

import java.util.EnumSet;
import java.util.Set;

public final class ClientMovementManager {

    private static boolean activelyFlying = false;

    private ClientMovementManager() {}

    public static void sync(boolean flying) {
        activelyFlying = flying;
    }

    public static boolean isActivelyFlying() {
        return activelyFlying;
    }

    /**
     * Computes available movement modes from unlocked passive abilities
     * that implement MovementModeProvider. Called each tick by the handler.
     */
    public static Set<MovementMode> getAvailableModes() {
        Set<MovementMode> modes = EnumSet.noneOf(MovementMode.class);
        for (var id : ClientAbilityManager.getUnlocked()) {
            Ability ability = AbilityRegistry.get(id);
            if (ability instanceof MovementModeProvider provider
                    && ability.getType() == Ability.Type.PASSIVE) {
                modes.addAll(provider.getGrantedModes());
            }
        }
        return modes;
    }

    public static boolean hasMode(MovementMode mode) {
        return getAvailableModes().contains(mode);
    }
}