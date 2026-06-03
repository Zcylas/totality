package zcylas.totality.api.ability.impl;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.rpg.combat.UnarmoredDefenseRegistry;

public class UnarmoredDefensePassive extends Ability {

    private final UnarmoredDefenseRegistry.UnarmoredDefenseProvider formula;

    public UnarmoredDefensePassive(Identifier id,
                                   String displayName,
                                   String description,
                                   Identifier icon,
                                   Source source,
                                   String sourceDetail,
                                   String flavourText,
                                   UnarmoredDefenseRegistry.UnarmoredDefenseProvider formula) {
        super(id, displayName, description, Type.PASSIVE, 0,
                icon, source, sourceDetail, flavourText);
        this.formula = formula;
    }

    @Override
    public void onPassiveTick(ServerPlayer player) {
        UnarmoredDefenseRegistry.register(player, formula);
    }

    @Override
    public void onPassiveRemoved(ServerPlayer player) {
        UnarmoredDefenseRegistry.remove(player);
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {}
}