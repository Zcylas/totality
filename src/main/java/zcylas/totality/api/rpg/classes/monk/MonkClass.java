package zcylas.totality.api.rpg.classes.monk;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;

import java.util.List;
import java.util.Map;

public final class MonkClass {

    // ── Subclass IDs ──────────────────────────────────────────────────────────
    public static final Identifier OPEN_HAND_ID = TotalityClasses.id("way_of_the_open_hand");
    public static final Identifier SHADOW_ID    = TotalityClasses.id("way_of_shadow");

    // ── ClassData ─────────────────────────────────────────────────────────────
    public static final ClassData DATA = new ClassData(
            TotalityClasses.MONK_ID,
            ClassCategory.MARTIAL,
            "Monk",
            "Disciplined martial artists who harness Ki — the life force flowing through " +
                    "all living things. Monks strike fast, move faster, and need no weapon to be deadly.",
            Dice.D8, Dice.D10, Dice.D8,
            List.of(AbilityScore.STR, AbilityScore.DEX),
            List.of(WeaponCategory.SIMPLE_MELEE, WeaponCategory.SIMPLE_RANGED),
            List.of(),
            null,
            Map.of(
                    Skill.UNARMED,    1.5,
                    Skill.PERCEPTION, 1.2
            ),
            List.of(),
            3  // Monastic Tradition chosen at class level 3
    );
    public static final SubclassData OPEN_HAND = new SubclassData(
                    OPEN_HAND_ID, TotalityClasses.MONK_ID,
            "Way of the Open Hand",
                    "The oldest and most practiced of the monastic traditions. Open Hand monks " +
                    "master the pure art of unarmed combat — manipulating Ki to push, topple, " +
                    "and paralyze opponents with surgical precision.",
                    List.of(), List.of()
                    );

    public static final SubclassData SHADOW = new SubclassData(
            SHADOW_ID, TotalityClasses.MONK_ID,
            "Way of Shadow",
            "Monks of the Way of Shadow follow a tradition that values stealth and " +
                    "subtlety. They move like ghosts through darkness, striking from positions " +
                    "of perfect concealment.",
            List.of(), List.of()
    );

    // ── Registration ──────────────────────────────────────────────────────────
    public static void register() {
        ClassRegistry.register(DATA);
        SubclassRegistry.register(OPEN_HAND);
        SubclassRegistry.register(SHADOW);

        ClassLevelUpRegistry.register(TotalityClasses.MONK_ID,
                (player, playerLevel, classLevel) -> {
                    if (classLevel == 3 && !ClassComponents.get(player).hasSubclass()) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                new zcylas.totality.networking.classes.OpenSubclassSelectionPayload(
                                        TotalityClasses.MONK_ID.toString()));
                    }
                });
    }

    private MonkClass() {}
}