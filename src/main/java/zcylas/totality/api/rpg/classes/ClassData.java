package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.rpg.combat.armor.ArmorProficiency;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.skills.core.Skill;

import java.util.List;
import java.util.Map;

public record ClassData(
        Identifier id,
        ClassCategory category,
        String displayName,
        String description,
        Dice hpDie,
        Dice staminaDie,
        Dice manaDie,
        List<AbilityScore>       savingThrowProficiencies,
        List<WeaponCategory>     weaponProficiencies,
        List<ArmorProficiency>   armorProficiencies,
        @Nullable AbilityScore   spellcastingAbility,
        Map<Skill, Double>       skillXpMultipliers,
        List<Identifier>         startingAbilities,
        /**
         * The class level at which the player chooses their subclass.
         * 1 = chosen at initial class selection (Warlock, Cleric, Sorcerer).
         * 2 = chosen when hitting class level 2 (Wizard, Druid).
         * 3 = chosen when hitting class level 3 (Barbarian, Fighter, Monk, etc.).
         */
        int subclassUnlockClassLevel
) {}