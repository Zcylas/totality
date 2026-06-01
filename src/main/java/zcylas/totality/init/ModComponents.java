package zcylas.totality.init;

import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.movement.MovementComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.magic.grimoire.MagicComponents;
import zcylas.totality.api.magic.grimoire.rune.RuneComponents;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.combat.CombatComponents;
import zcylas.totality.api.rpg.resources.ResourceComponents;
import zcylas.totality.api.rpg.skills.core.MasteriesComponents;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyComponents;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.industrial.energy.UEComponents;
import zcylas.totality.api.industrial.fluid.FluidComponents;
import zcylas.totality.api.rpg.skills.alchemy.potions.PotionDataComponent;

public class ModComponents {

    public static void register(){
        UEComponents.register();
        FluidComponents.register();
        MagicComponents.register();
        CurrencyComponents.register();
        AlchemyComponents.register();
        PotionDataComponent.register();
        StatsComponents.register();
        SkillsComponents.register();
        MasteriesComponents.register();
        AbilityComponents.register();
        ResourceComponents.register();
        CombatComponents.register();
        ItemComponents.register();
        RuneComponents.register();
        AncestryComponents.register();
        MovementComponents.register();
        ClassComponents.register();
        ChargeComponents.register();
    }

    private ModComponents() {}

}
