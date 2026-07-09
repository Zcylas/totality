package zcylas.totality.api.ability.impl;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.rpg.rest.RestManager;
import zcylas.totality.networking.rest.OpenRestChoicePayload;

/** BG3-style "camp anywhere" Rest trigger — opens the same Short/Long/Cancel popup as a bed. */
public class RestAbility extends Ability {

    public RestAbility() {
        super(
                Identifier.fromNamespaceAndPath("totality", "rest"),
                "Rest",
                "Take a Short or Long Rest, wherever you are.",
                Type.ACTIVE,
                0,
                Identifier.fromNamespaceAndPath("totality", "textures/ability/rest.png"),
                Source.DEFAULT,
                "Default Ability",
                "Even heroes need to catch their breath."
        );
    }

    @Override
    public boolean isDefault() { return true; }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        ServerPlayNetworking.send(player, new OpenRestChoicePayload(null, RestManager.getShortRestRemaining(player)));
    }
}
