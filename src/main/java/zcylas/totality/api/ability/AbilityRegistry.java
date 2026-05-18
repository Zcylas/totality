package zcylas.totality.api.ability;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.ability.impl.HarvestAbility;
import zcylas.totality.api.ability.impl.VeinminerAbility;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class AbilityRegistry {

    private static final Map<Identifier, Ability> ABILITIES = new LinkedHashMap<>();

    public static final HarvestAbility HARVEST = register(new HarvestAbility());
    public static final VeinminerAbility VEINMINER = register(new VeinminerAbility());
    private static <T extends Ability> T register(T ability) {
        ABILITIES.put(ability.getId(), ability);
        return ability;
    }

    public static Ability get(Identifier id) {
        return ABILITIES.get(id);
    }

    public static Collection<Ability> all() {
        return ABILITIES.values();
    }

    /** All abilities that are unlocked by default for every player. */
    public static Collection<Ability> defaults() {
        return ABILITIES.values().stream()
                .filter(Ability::isDefault)
                .toList();
    }

    public static void register() {
        VEINMINER.registerEvents();
    }

    private AbilityRegistry() {}
}