package zcylas.totality.api.ability;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.ability.impl.*;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.ability.kryptonian.HeatVisionAbility;
import zcylas.totality.api.ability.trait.Traits;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AbilityRegistry {

    private static final Map<Identifier, Ability> ABILITIES = new LinkedHashMap<>();

    // ── Default abilities ─────────────────────────────────────────────────────
    public static final HarvestAbility  HARVEST  = register(new HarvestAbility());
    public static final VeinminerAbility VEINMINER = register(new VeinminerAbility());
    public static final GroundSlamAbility GROUND_SLAM = register(new GroundSlamAbility());
    public static final HeatVisionAbility HEAT_VISION = register(new HeatVisionAbility());
    // ── Viltrumite ────────────────────────────────────────────────────────────
    public static final PhysiologyPassive VILTRUMITE_PHYSIOLOGY = register(new PhysiologyPassive(
            Identifier.fromNamespaceAndPath("totality", "viltrumite_physiology"),
            "Viltrumite Physiology",
            "Your inner-ear graviton field grants mastery over gravity itself, enabling flight. " +
                    "Your reinforced biology renders you immune to fall damage and resistant to knockback.",
            Identifier.fromNamespaceAndPath("totality", "textures/ability/viltrumite_physiology.png"),
            Ability.Source.ANCESTRY,
            "Viltrumite",
            "Born to conquer. Built to survive.",
            Set.of(MovementMode.FLIGHT, MovementMode.POWER_SPRINT, MovementMode.SUPER_LEAP),
            List.of(
                    Traits.noFallDamage(),
                    Traits.knockbackResistance("viltrumite_knockback", 1.0),
                    Traits.attackDamage("viltrumite_attack", 4.0),
                    Traits.armor("viltrumite", 4.0, 3.0)
            )
    ));

    // ── Kryptonian ────────────────────────────────────────────────────────────
    public static final PhysiologyPassive KRYPTONIAN_PHYSIOLOGY = register(new PhysiologyPassive(
            Identifier.fromNamespaceAndPath("totality", "kryptonian_physiology"),
            "Kryptonian Physiology",
            "Empowered by the light of a yellow sun, your Kryptonian cells absorb solar energy " +
                    "and convert it into extraordinary physical capability. Flight, immense strength, " +
                    "and near-invulnerability are yours under the right star.",
            Identifier.fromNamespaceAndPath("totality", "textures/ability/kryptonian_physiology.png"),
            Ability.Source.ANCESTRY,
            "Kryptonian",
            "Last survivor of a dead world. First of something greater.",
            Set.of(MovementMode.FLIGHT, MovementMode.POWER_SPRINT, MovementMode.SUPER_LEAP),
            List.of(
                    Traits.noFallDamage(),
                    Traits.knockbackResistance("kryptonian_knockback", 0.8),
                    Traits.attackDamage("kryptonian_attack", 3.0),
                    Traits.armor("kryptonian", 3.0, 2.0)
            )
    ));


    //Classes
        //Barbarian
    public static final BarbarianRageAbility BARBARIAN_RAGE = register(new BarbarianRageAbility());

    public static final UnarmoredDefensePassive BARBARIAN_UNARMORED_DEFENSE = register(new UnarmoredDefensePassive(
            Identifier.fromNamespaceAndPath("totality", "barbarian_unarmored_defense"),
            "Unarmored Defense",
            "While not wearing armor, your AC equals 10 + STR modifier + CON modifier. " +
                    "You can use a shield and still gain this benefit.",
            Identifier.fromNamespaceAndPath("totality",
                    "textures/ability/barbarian_unarmored_defense.png"),
            Ability.Source.CLASS,
            "Barbarian",
            "Skin like iron, bones like stone.",
            (player, stats) -> 10 + stats.getModifier(AbilityScore.STR)
                    + stats.getModifier(AbilityScore.CON)
    ));
    // ── Registry ──────────────────────────────────────────────────────────────

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