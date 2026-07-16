package zcylas.totality.api.rpg.classes.warlock;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.magic.spell.CasterProgression;
import zcylas.totality.api.magic.spell.SpellcastingProgressionRegistry;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.classes.covenant.*;
import zcylas.totality.api.rpg.combat.armor.ArmorProficiency;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.List;
import java.util.Map;

public final class WarlockClass {

    // ── Subclass IDs ──────────────────────────────────────────────────────────
    public static final Identifier HEXBLADE_ID = TotalityClasses.id("hexblade");
    public static final Identifier ARCHFEY_ID  = TotalityClasses.id("archfey");

    // ── Covenant IDs ──────────────────────────────────────────────────────────
    public static final Identifier CAT_HEXBLADE = TotalityClasses.id("covenant_category.hexblade");
    public static final Identifier CAT_ARCHFEY  = TotalityClasses.id("covenant_category.archfey");
    public static final Identifier ARTAGAN      = TotalityClasses.id("covenant.artagan");
    public static final Identifier UK_OTOA      = TotalityClasses.id("covenant.uk_otoa");

    // ── ClassData ─────────────────────────────────────────────────────────────
    public static final ClassData DATA = new ClassData(
            TotalityClasses.WARLOCK_ID,
            ClassCategory.ARCANE,
            "Warlock",
            "Wielders of eldritch power drawn from a pact with an otherworldly patron.",
            Dice.D8, Dice.D6, Dice.D10,
            List.of(AbilityScore.WIS, AbilityScore.CHA),
            List.of(WeaponCategory.SIMPLE_MELEE, WeaponCategory.SIMPLE_RANGED),
            List.of(ArmorProficiency.LIGHT),
            AbilityScore.CHA,
            Map.of(
                    Skill.ILLUSION,      1.5,
                    Skill.DECEPTION,     1.5,
                    Skill.INTIMIDATION,  1.5,
                    Skill.INVESTIGATION, 1.2,
                    Skill.PERCEPTION,    1.2
            ),
            List.of(),
            1  // Otherworldly Patron (subclass) chosen at initial class selection
    );
    public static final SubclassData THE_HEXBLADE = new SubclassData(
                    HEXBLADE_ID, TotalityClasses.WARLOCK_ID,
            "The Hexblade",
                    "A pact forged with a mysterious entity from the Shadowfell. " +
                    "Hexblade warlocks channel dark power into their weapons, cursing enemies " +
                    "and stealing their vitality.",
                    List.of(CAT_HEXBLADE), List.of()
            );

    public static final SubclassData THE_ARCHFEY = new SubclassData(
            ARCHFEY_ID, TotalityClasses.WARLOCK_ID,
            "The Archfey",
            "A pact bound to a lord or lady of the fey. Archfey warlocks weave " +
                    "enchantments and illusions, bending minds and slipping between reality and dream.",
            List.of(CAT_ARCHFEY), List.of()
    );

    // ── Covenant Categories ───────────────────────────────────────────────────
    public static final CovenantCategory HEXBLADE_CATEGORY = new CovenantCategory(
            CAT_HEXBLADE,
            "Hexblade Patrons",
            "Entities of shadow and cursed steel who forge pacts through power and ambition."
    );

    public static final CovenantCategory ARCHFEY_CATEGORY = new CovenantCategory(
            CAT_ARCHFEY,
            "Archfey Patrons",
            "Ancient fey lords and ladies whose whims reshape the world around them."
    );

    // ── Covenants ─────────────────────────────────────────────────────────────
    public static final CovenantData COVENANT_ARTAGAN = new CovenantData(
            ARTAGAN, CAT_ARCHFEY, "Artagan",
            "A whimsical and unpredictable lord of the Feywild, who wandered the mortal world " +
                    "disguised as a deity called The Traveler. Artagan's warlocks inherit his gift for " +
                    "trickery, illusion, and bending reality to their amusement — whether their patron " +
                    "is paying attention or not.",
            List.of()
    );

    public static final CovenantData COVENANT_UK_OTOA = new CovenantData(
            UK_OTOA, CAT_HEXBLADE, "Uk'otoa",
            "A leviathan sealed beneath the waves, whispering promises of power to those " +
                    "who swear to free it. Its warlocks command water, darkness, and the paralyzing " +
                    "terror of the deep.",
            List.of()
    );

    // ── Registration ──────────────────────────────────────────────────────────
    public static void register() {
        ClassRegistry.register(DATA);
        SubclassRegistry.register(THE_HEXBLADE);
        SubclassRegistry.register(THE_ARCHFEY);
        CovenantRegistry.registerCategory(HEXBLADE_CATEGORY);
        CovenantRegistry.registerCategory(ARCHFEY_CATEGORY);
        CovenantRegistry.register(COVENANT_ARTAGAN);
        CovenantRegistry.register(COVENANT_UK_OTOA);
        SpellcastingProgressionRegistry.register(TotalityClasses.WARLOCK_ID, CasterProgression.WARLOCK);
        // No level-up handler yet — Warlock class abilities added after spell system
    }

    private WarlockClass() {}
}