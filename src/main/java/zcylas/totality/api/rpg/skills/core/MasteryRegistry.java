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
        // ── MINING ────────────────────────────────────────────────────────────
        register(Skill.MINING,

                // Level 0 — spine base
                Mastery.of("mining_mastery", "Mining Mastery", 0,
                        "Mining tools gain 1% more mining speed and 1% more reach per level of Mining."),

                // Level 20 — spine
                Mastery.of("steady_hands", "Steady Hands", 20,
                        "Reduces mining animation time for stone-type blocks by 10%, " +
                                "making each swing feel snappier without affecting tool speed."),

                // Level 40 — spine, rank 1
                Mastery.of2("durable_tools", "Durable Tools",
                        40, "Mining tools lose 10% less durability per use.",
                        80, "Mining tools lose 20% less durability per use."),

                // Level 50 — spine
                Mastery.of("fortunate_son", "Fortunate Son", 50,
                        "You gain 1% more mining fortune per level of Mining." ),

                // Level 60 — spine
                Mastery.of("stone_sense", "Stone Sense", 60,
                        "Unlocks the Stone Sense ability. When active, nearby ores within 8 blocks " +
                                "are revealed through walls with a subtle glow. Toggle on and off at will."),

                // Level 70 — spine, rank 1
                Mastery.of2("deep_delver", "Deep Delver",
                        70, "You take 30% less fall damage and are immune to suffocation from stone and gravel.",
                        90, "You are fully immune to fall damage underground and take no suffocation damage from any block."),

                // Level 100 — pickaxe head (3 wide)
                Mastery.ofAbility("veinminer", "Veinminer", 100,
                        "Unlocks the Veinminer ability. While active, breaking an ore block " +
                                "will break the entire connected vein at once.", "totality:veinminer"),

                Mastery.of("tunneler", "Tunneler", 100,
                        "Unlocks the Tunneler ability. While active, your pickaxe breaks " +
                                "a 3x3 area of stone-type blocks instead of a single block."),

                Mastery.of("prospector", "Prospector", 100,
                        "Breaking an ore block has a chance to drop a bonus gem.")
        );
        register(Skill.ALCHEMY,

                // Level 0 — spine base
                Mastery.of("alchemy_mastery", "Alchemy Mastery", 0,
                        "Potions you brew are 1% more potent per level of Alchemy. " +
                                "At level 100: all brewed potions are 100% stronger."),

                // Level 20 — spine
                Mastery.of("poisoner", "Poisoner", 20,
                        "Poisons you mix are 1% more powerful per level of Alchemy. " +
                                "At level 100: all poisons are 100% more potent."),

                // Level 30 — spine
                Mastery.of("physician", "Physician", 30,
                        "Potions that restore Health, Stamina, or Mana are 25% more effective."),

                // Level 40 — spine
                Mastery.of("advanced_lab", "Advanced Lab", 40,
                        "You may upgrade one Apothecary Table to an Advanced version for 2500 gold. " +
                                "Potions mixed at an Advanced Lab are 25% stronger. " +
                                "Sneak and interact with the table to disassemble it and move the upgrade."),

                // Level 50 — spine
                Mastery.of("experimenter", "Experimenter", 50,
                        "Eating an ingredient reveals its second effect in addition to the first. " +
                                "NPC Alchemists can still be hired to reveal all effects for a price."),

                // Level 60 — spine
                Mastery.of("green_thumb", "Green Thumb", 60,
                        "Harvesting alchemy ingredients using the Harvest ability yields double the amount."),

                // Level 80 — spine
                Mastery.of("surgeon", "Surgeon", 80,
                        "When looting corpses, you may find unique stat-boosting ingredients, " +
                                "additional food, and rare alchemy components not found elsewhere."),

                // Level 90 — 2-wide
                Mastery.of("witchmaster", "Witchmaster", 90,
                        "When you use a beneficial potion or ingredient, there is a 50% chance to " +
                                "receive a powerful random side effect, chosen from a pool of 40 unique effects."),

                Mastery.of("amplify_lethality", "Amplify Lethality", 90,
                        "Unlocks the Amplify Lethality power. Once per day, point at a target to " +
                                "silently reduce their poison resistance by 250% for 10 seconds."),

                // Level 100 — spine
                Mastery.of("that_which_does_not_kill_you", "That Which Does Not Kill You...", 100,
                        "Upon learning this mastery, you imbibe a deadly toxin that deals 150 damage " +
                                "per second. If you survive for 60 seconds, you receive 3 mastery points and a " +
                                "permanent 25% bonus to all potions and poisons you create."),

                // Level 150 — spine
                Mastery.of("knowledge_seeker", "Knowledge Seeker", 150,
                        "Allows you to craft the Philosopher's Stone, which grants the ability to " +
                                "transmute metal ingots into more valuable metals."),

                // Level 200 — spine top
                Mastery.of("purity", "Purity", 200,
                        "You create two potions or poisons instead of one. All negative effects are " +
                                "removed from created potions, and all positive effects are removed from " +
                                "created poisons.")
        );
        register(Skill.RITUAL_ARTS

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