package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

import java.util.Optional;

public final class SpellSlotComponents {

    public static final ComponentKey<SpellSlotComponent> SPELL_SLOTS =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "spell_slots"),
                    SpellSlotComponent.class
            );

    private SpellSlotComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                SPELL_SLOTS,
                SpellSlotComponent::new,          // ServerPlayer → SpellSlotComponent
                RespawnStrategy.ALWAYS_COPY        // preserve slot state through death
        );
        PlayerComponentEvents.registerClientComponent(
                SPELL_SLOTS,
                () -> new SpellSlotComponent(null)
        );
    }

    public static SpellSlotComponent get(ServerPlayer player) {
        return SPELL_SLOTS.get((ComponentProvider) player);
    }

    /**
     * Gets the component without throwing if it is absent — the safe, read-only lookup path a
     * generic Resource API query must use instead of {@link #get}, which throws
     * {@code IllegalStateException} for a player the component was never attached to. Mirrors
     * {@code ResourceComponents.maybeGet(ServerPlayer)}'s precedent for the legacy Mana/Stamina
     * store (Phase 2C), added here for the standard spell-slot external adapter (Phase 2D).
     */
    public static Optional<SpellSlotComponent> maybeGet(ServerPlayer player) {
        return SPELL_SLOTS.maybeGet((ComponentProvider) player);
    }
}