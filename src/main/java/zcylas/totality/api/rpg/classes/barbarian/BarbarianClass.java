package zcylas.totality.api.rpg.classes.barbarian;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.classes.feature.ClassFeatureRegistry;import zcylas.totality.api.rpg.combat.armor.ArmorProficiency;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.List;
import java.util.Map;

public final class BarbarianClass {

    // ── Subclass IDs ──────────────────────────────────────────────────────────
    public static final Identifier BERSERKER_ID  = TotalityClasses.id("path_of_the_berserker");
    public static final Identifier WILD_HEART_ID = TotalityClasses.id("path_of_the_wild_heart");

    // ── ClassData ─────────────────────────────────────────────────────────────
    public static final ClassData DATA = new ClassData(
            TotalityClasses.BARBARIAN_ID,
            ClassCategory.MARTIAL,
            "Barbarian",
            "Primal warriors who channel rage into unstoppable physical power. " +
                    "Barbarians are the toughest fighters on the battlefield — hard to kill, " +
                    "harder to stop.",
            Dice.D12, Dice.D12, Dice.D4,
            List.of(AbilityScore.STR, AbilityScore.CON),
            List.of(WeaponCategory.SIMPLE_MELEE, WeaponCategory.SIMPLE_RANGED,
                    WeaponCategory.MARTIAL_MELEE, WeaponCategory.MARTIAL_RANGED),
            List.of(ArmorProficiency.LIGHT, ArmorProficiency.MEDIUM, ArmorProficiency.SHIELD),
            null,
            Map.of(
                    Skill.INTIMIDATION, 1.5,
                    Skill.ONE_HANDED,   1.3,
                    Skill.TWO_HANDED,   1.5
            ),
            List.of(),
            3  // Primal Path chosen at class level 3
    );

    // ── Subclasses ────────────────────────────────────────────────────────────
    public static final SubclassData BERSERKER = new SubclassData(
            BERSERKER_ID, TotalityClasses.BARBARIAN_ID,
            "Path of the Berserker",
            "The most primal path a Barbarian can walk. Berserkers enter a frenzied " +
                    "state beyond ordinary rage, attacking in a relentless frenzy at the cost " +
                    "of exhaustion when the battle ends.",
            List.of(), List.of()
    );

    public static final SubclassData WILD_HEART = new SubclassData(
            WILD_HEART_ID, TotalityClasses.BARBARIAN_ID,
            "Path of the Wild Heart",
            "Barbarians who walk this path form a spiritual bond with the animal world. " +
                    "They call upon the spirits of bear, eagle, wolf and more — each granting " +
                    "a different kind of primal power.",
            List.of(), List.of()
    );

    // ── Registration ──────────────────────────────────────────────────────────
    public static void register() {
        ClassRegistry.register(DATA);
        SubclassRegistry.register(BERSERKER);
        SubclassRegistry.register(WILD_HEART);

        ClassLevelUpRegistry.register(TotalityClasses.BARBARIAN_ID,
                (player, playerLevel, classLevel) -> {
                    BarbarianRageAbility.updateChargePool(player);
                    ClassFeatureRegistry.onClassLevelUp(player, TotalityClasses.BARBARIAN_ID, classLevel);
                    // Trigger Primal Path selection at class level 3
                    if (classLevel == 3 && !ClassComponents.get(player).hasSubclass()) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                new zcylas.totality.networking.classes.OpenSubclassSelectionPayload(
                                        TotalityClasses.BARBARIAN_ID.toString()));
                    }
                });
    }

    private BarbarianClass() {}
}