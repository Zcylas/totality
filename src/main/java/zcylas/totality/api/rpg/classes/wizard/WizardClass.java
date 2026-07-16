package zcylas.totality.api.rpg.classes.wizard;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.magic.spell.CasterProgression;
import zcylas.totality.api.magic.spell.SpellcastingProgressionRegistry;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.List;
import java.util.Map;

public final class WizardClass {

    // ── Subclass IDs ──────────────────────────────────────────────────────────
    public static final Identifier DESTRUCTION_ID = TotalityClasses.id("school_of_destruction");
    public static final Identifier NECROMANCY_ID  = TotalityClasses.id("school_of_necromancy");

    // ── ClassData ─────────────────────────────────────────────────────────────
    public static final ClassData DATA = new ClassData(
            TotalityClasses.WIZARD_ID,
            ClassCategory.ARCANE,
            "Wizard",
            "Scholars of arcane magic who bend reality through sheer intellect. " +
                    "Fragile in body but devastating in power, Wizards command the broadest " +
                    "and most potent spell list of any class.",
            Dice.D6, Dice.D4, Dice.D12,
            List.of(AbilityScore.INT, AbilityScore.WIS),
            List.of(WeaponCategory.SIMPLE_MELEE, WeaponCategory.SIMPLE_RANGED),
            List.of(),
            AbilityScore.INT,
            Map.of(
                    Skill.DESTRUCTION,  1.5,
                    Skill.CONJURATION,  1.5,
                    Skill.ALTERATION,   1.3,
                    Skill.ILLUSION,     1.3,
                    Skill.RESTORATION,  1.2
            ),
            List.of(),
            2  // Arcane Tradition chosen at class level 2
    );
    public static final SubclassData SCHOOL_OF_DESTRUCTION = new SubclassData(
            DESTRUCTION_ID, TotalityClasses.WIZARD_ID,
            "School of Destruction",
            "Wizards of the School of Destruction channel raw magical force into " +
                    "devastating spells — fire, frost, lightning and pure arcane energy. " +
                    "They are the artillery of the arcane world, sculpting explosions to " +
                    "erase everything in their path.",
                    List.of(), List.of()
                    );

    public static final SubclassData SCHOOL_OF_NECROMANCY = new SubclassData(
            NECROMANCY_ID, TotalityClasses.WIZARD_ID,
            "School of Necromancy",
            "Necromancers study the line between life and death, bending it to their will. " +
                    "They raise the fallen as servants, drain the living of vitality, and wield " +
                    "death itself as a weapon.",
            List.of(), List.of()
    );

    // ── Registration ──────────────────────────────────────────────────────────
    public static void register() {
        ClassRegistry.register(DATA);
        SubclassRegistry.register(SCHOOL_OF_DESTRUCTION);
        SubclassRegistry.register(SCHOOL_OF_NECROMANCY);
        SpellcastingProgressionRegistry.register(TotalityClasses.WIZARD_ID, CasterProgression.FULL);

        ClassLevelUpRegistry.register(TotalityClasses.WIZARD_ID,
                (player, playerLevel, classLevel) -> {
                    // Trigger Arcane Tradition selection at class level 2
                    if (classLevel == 2 && !ClassComponents.get(player).hasSubclass()) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                new zcylas.totality.networking.classes.OpenSubclassSelectionPayload(
                                        TotalityClasses.WIZARD_ID.toString()));
                    }
                });
    }

    private WizardClass() {}
}