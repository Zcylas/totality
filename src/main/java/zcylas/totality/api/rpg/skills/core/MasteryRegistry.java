package zcylas.totality.api.rpg.skills.core;

import java.util.*;

/**
 * Defines all masteries for each skill.
 * Adding a new skill's masteries = add a new entry here.
 * The screen reads from this registry automatically — no screen changes needed.
 *
 * Masteries are sorted by base required level (lowest first = bottom of tree).
 */
public final class MasteryRegistry {

    private static final Map<Skill, List<Mastery>> REGISTRY = new EnumMap<>(Skill.class);

    static {
        // ── ONE-HANDED ────────────────────────────────────────────────────────
        register(Skill.ONE_HANDED,

                // Level 0
                Mastery.of("one_handed_mastery", "One-Handed Mastery", 0,
                        "One-handed weapons do 1% more damage and 5% more critical damage per level of One-Handed."),

                // Level 20
                Mastery.of("disciplined_fighter", "Disciplined Fighter", 20,
                        "Power attacks with one-handed weapons cost 25% less Stamina."),

                Mastery.of2("quick_slash", "Quick Slash",
                        20, "Swords and daggers have a 10% chance of dealing critical damage.",
                        70, "Swords and daggers have a 20% chance of dealing critical damage."),

                // Level 30
                Mastery.of2("ravage", "Ravage",
                        30, "Dual wielding attacks are 20% faster.",
                        30, "Dual wielding attacks are 35% faster."),

                Mastery.of3("clash_of_champions", "Clash of Champions",
                        30, "Attacks with swords reduce the target's attack damage by 10% for 3 seconds.",
                        60, "Attacks with swords reduce the target's attack damage by 15% for 3 seconds.",
                        90, "Attacks with swords reduce the target's attack damage by 20% for 3 seconds."),

                // Level 40
                Mastery.of("furious_strength", "Furious Strength", 40,
                        "Power attacks do 0.1% more damage per point of Stamina. Unlocks decapitations."),

                // Level 50
                Mastery.of2("fighters_stance", "Fighter's Stance",
                        50, "Power attacks with one-handed weapons deal 25% more damage and can decapitate enemies.",
                        100, "Power attacks with one-handed weapons deal 50% more damage and can decapitate enemies."),

                // Level 70
                Mastery.of("critical_charge", "Critical Charge", 70,
                        "Can perform a one-handed sprinting power attack that does double critical damage. " +
                                "One-handed power attacks are 20% more likely to critically hit."),

                // Level 80
                Mastery.of("execute", "Execute", 80,
                        "Power attacks with one-handed weapons deal 50% extra damage against targets below half Health."),

                // Level 90
                Mastery.of("bladedancer", "Bladedancer", 90,
                        "Take 30% less attack damage while power attacking with two weapons."),

                // Level 100
                Mastery.of("wandering_warrior", "Wandering Warrior", 100,
                        "Whenever you defeat at least 4 humanoids or animals in a single battle, " +
                                "gain a permanent +1% bonus to one-handed damage. Stacks up to +20%."),

                Mastery.of("onslaught", "Onslaught", 100,
                        "Repeated power attacks against a single target with one-handed weapons deal up to double damage.")
        );
    }

    private MasteryRegistry() {}

    private static void register(Skill skill, Mastery... masteries) {
        List<Mastery> list = new ArrayList<>(Arrays.asList(masteries));
        // Sort by base required level — lowest first (bottom of tree)
        list.sort(Comparator.comparingInt(Mastery::getBaseRequiredLevel));
        REGISTRY.put(skill, Collections.unmodifiableList(list));
    }

    /**
     * Returns all masteries for a skill, sorted by required level ascending.
     */
    public static List<Mastery> getMasteries(Skill skill) {
        return REGISTRY.getOrDefault(skill, List.of());
    }

    /**
     * Returns a specific mastery by id within a skill.
     */
    public static Optional<Mastery> get(Skill skill, String masteryId) {
        return getMasteries(skill).stream()
                .filter(m -> m.getId().equals(masteryId))
                .findFirst();
    }
}