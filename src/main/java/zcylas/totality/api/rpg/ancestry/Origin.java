package zcylas.totality.api.rpg.ancestry;

import zcylas.totality.api.rpg.stats.AbilityScoreBonus;

import java.util.Arrays;
import java.util.List;

/**
 * Defines a playable origin in the Totality ancestry system.
 *
 * Origin is the full mechanical package for a species variant.
 * It contains all stat bonuses, passives, abilities, source tag,
 * height override, and unlock requirements.
 *
 * Stats live here, not on Species. This allows different universes'
 * versions of the same species to have completely different stat packages.
 *
 * Examples:
 *   Human → Standard Human [D&D 5e] → +1 all stats
 *   Human → Nord [Elder Scrolls]    → +2 STR, +1 CON
 *   Elf   → High Elf [D&D 5e]       → +1 DEX, +1 INT
 *   Elf   → Dunmer [Elder Scrolls]  → +1 INT, +1 DEX
 */
public enum Origin {



    // ── Human Origins ─────────────────────────────────────────────────────────
    STANDARD_HUMAN("Standard Human", Species.HUMAN, SourceTag.DND_5E,
            "The most common human variant, defined by remarkable adaptability. " +
                    "Standard humans receive a small bonus to all attributes, " +
                    "reflecting their natural versatility and drive.",
            new AbilityScoreBonus.Builder()
                    .str(1).dex(1).con(1).end(1).intel(1).wis(1).cha(1).fth(1)
                    .build(),
            0.95f, 1.05f,
            UnlockState.UNLOCKED),

    NORD("Nord", Species.HUMAN, SourceTag.ELDER_SCROLLS,
            "Born in the frozen province of Skyrim, Nords are a hardy and warlike " +
                    "people. They are fierce warriors hardened by brutal winters and " +
                    "an unbreakable fighting spirit that has made them legendary across Tamriel.",
            new AbilityScoreBonus.Builder()
                    .str(2).con(1)
                    .build(),
            1.0f, 1.08f,
            UnlockState.UNLOCKED),

    IMPERIAL("Imperial", Species.HUMAN, SourceTag.ELDER_SCROLLS,
            "Natives of the civilized heartland of Cyrodiil, Imperials are diplomats " +
                    "and administrators by nature. Their gift for commerce and negotiation " +
                    "has allowed them to build and maintain the greatest empire Tamriel has ever seen.",
            new AbilityScoreBonus.Builder()
                    .cha(1).wis(1).intel(1)
                    .build(),
            0.97f, 1.03f,
            UnlockState.UNLOCKED),

    REDGUARD("Redguard", Species.HUMAN, SourceTag.ELDER_SCROLLS,
            "The most naturally talented warriors in Tamriel, Redguards come from " +
                    "the arid province of Hammerfell. Their culture is defined by martial " +
                    "excellence, honor, and an almost supernatural stamina in battle.",
            new AbilityScoreBonus.Builder()
                    .str(1).dex(1).end(1)
                    .build(),
            0.97f, 1.05f,
            UnlockState.UNLOCKED),

    BRETON("Breton", Species.HUMAN, SourceTag.ELDER_SCROLLS,
            "Born of the intermingling of human and elven blood in High Rock, Bretons " +
                    "possess a natural affinity for magic. They are gifted spellcasters " +
                    "with an innate resistance to the arcane.",
            new AbilityScoreBonus.Builder()
                    .intel(1).wis(1)
                    .build(),
            0.95f, 1.0f,
            UnlockState.UNLOCKED),

    // ── Elf Origins ───────────────────────────────────────────────────────────
    HIGH_ELF("High Elf", Species.ELF, SourceTag.DND_5E,
            "Proud and learned, high elves have devoted centuries to the mastery " +
                    "of arcane magic. They are tall, perceptive, and carry themselves " +
                    "with an air of quiet superiority.",
            new AbilityScoreBonus.Builder()
                    .dex(1).intel(1)
                    .build(),
            1.08f, 1.15f,
            UnlockState.UNLOCKED),

    WOOD_ELF("Wood Elf", Species.ELF, SourceTag.DND_5E,
            "Swift and stealthy, wood elves are one with the forests they inhabit. " +
                    "They are expert hunters and trackers, moving through the wilderness " +
                    "like shadows.",
            new AbilityScoreBonus.Builder()
                    .dex(1).wis(1)
                    .build(),
            0.98f, 1.05f,
            UnlockState.UNLOCKED),

    DARK_ELF("Dark Elf", Species.ELF, SourceTag.DND_5E,
            "Exiled from the surface world, dark elves have thrived in the depths " +
                    "through cunning and ruthlessness. They are masters of shadow magic " +
                    "and poison.",
            new AbilityScoreBonus.Builder()
                    .dex(1).cha(1)
                    .build(),
            1.0f, 1.08f,
            UnlockState.UNLOCKED),

    DUNMER("Dunmer", Species.ELF, SourceTag.ELDER_SCROLLS,
            "The ash-born of Morrowind. Forged by volcano and prophecy, the Dunmer " +
                    "possess a natural affinity for mysticism and resilience. They are " +
                    "noted for their stealth, magic, and fierce independence.",
            new AbilityScoreBonus.Builder()
                    .intel(1).dex(1)
                    .build(),
            1.0f, 1.08f,
            UnlockState.UNLOCKED),

    DROW("Drow", Species.ELF, SourceTag.BALDURS_GATE_3,
            "Children of the Underdark, drow are dark elves who have built a " +
                    "powerful and ruthless civilization beneath the surface. They are " +
                    "gifted with innate magic and deadly precision.",
            new AbilityScoreBonus.Builder()
                    .dex(1).cha(1)
                    .build(),
            1.0f, 1.08f,
            UnlockState.UNLOCKED),

    // ── Dwarf Origins ─────────────────────────────────────────────────────────
    HILL_DWARF("Hill Dwarf", Species.DWARF, SourceTag.DND_5E,
            "Hardier than their mountain kin, hill dwarves have developed an almost " +
                    "supernatural toughness. They are wise, perceptive, and exceptionally " +
                    "difficult to kill.",
            new AbilityScoreBonus.Builder()
                    .con(2).wis(1)
                    .build(),
            0.75f, 0.80f,
            UnlockState.UNLOCKED),

    MOUNTAIN_DWARF("Mountain Dwarf", Species.DWARF, SourceTag.DND_5E,
            "Trained from birth in the arts of war and stonecraft, mountain dwarves " +
                    "are the strongest of their kind. They are disciplined, powerful, " +
                    "and built like the rock they call home.",
            new AbilityScoreBonus.Builder()
                    .con(2).str(2)
                    .build(),
            0.80f, 0.85f,
            UnlockState.UNLOCKED),

    // ── Halfling Origins ──────────────────────────────────────────────────────
    LIGHTFOOT("Lightfoot", Species.HALFLING, SourceTag.DND_5E,
            "Lightfoot halflings are remarkably stealthy and have a gift for blending " +
                    "into crowds. They move through the world quietly, rarely noticed " +
                    "by those who tower above them.",
            new AbilityScoreBonus.Builder()
                    .dex(2).cha(1)
                    .build(),
            0.55f, 0.60f,
            UnlockState.UNLOCKED),

    STOUT("Stout", Species.HALFLING, SourceTag.DND_5E,
            "Stout halflings are hardier than their lightfoot kin. Some believe they " +
                    "carry a trace of dwarven blood, which would explain their toughness " +
                    "and their fondness for a good meal and a warm hearth.",
            new AbilityScoreBonus.Builder()
                    .dex(2).con(1)
                    .build(),
            0.58f, 0.65f,
            UnlockState.UNLOCKED),

    // ── Gnome Origins ─────────────────────────────────────────────────────────
    FOREST_GNOME("Forest Gnome", Species.GNOME, SourceTag.DND_5E,
            "Forest gnomes have a natural gift for illusion and an innate ability " +
                    "to befriend small animals. They live in hidden woodland communities, " +
                    "rarely seen by other races.",
            new AbilityScoreBonus.Builder()
                    .intel(2).dex(1)
                    .build(),
            0.65f, 0.70f,
            UnlockState.UNLOCKED),

    ROCK_GNOME("Rock Gnome", Species.GNOME, SourceTag.DND_5E,
            "The most common gnomes in the world, rock gnomes have a natural " +
                    "inventiveness and hardiness. They are tinkerers and artificers " +
                    "at heart, always curious about how things work.",
            new AbilityScoreBonus.Builder()
                    .intel(2).con(1)
                    .build(),
            0.68f, 0.75f,
            UnlockState.UNLOCKED),

    DEEP_GNOME("Deep Gnome", Species.GNOME, SourceTag.DND_5E,
            "Also known as svirfneblin, deep gnomes dwell in the Underdark far " +
                    "beneath the surface. Grim and practical compared to their surface " +
                    "cousins, they have adapted to survive in one of the most dangerous " +
                    "environments in the world.",
            new AbilityScoreBonus.Builder()
                    .intel(2).dex(1)
                    .build(),
            0.63f, 0.68f,
            UnlockState.UNLOCKED),

    // ── Goliath Origins ───────────────────────────────────────────────────────
    GOLIATH_DND("Goliath", Species.GOLIATH, SourceTag.DND_5E,
            "Towering and powerful, goliaths live in the highest mountain peaks. " +
                    "They live by a strict code of competition and self-sufficiency, " +
                    "always striving to surpass their own limits.",
            new AbilityScoreBonus.Builder()
                    .str(2).con(1)
                    .build(),
            1.35f, 1.45f,
            UnlockState.UNLOCKED),

    // ── Aasimar Origins ───────────────────────────────────────────────────────
    PROTECTOR("Protector Aasimar", Species.AASIMAR, SourceTag.DND_5E,
            "Protector aasimar are charged by the powers of good to guard the weak " +
                    "and strike at evil. They are suffused with a divine radiance that " +
                    "can be unleashed to smite enemies and inspire allies.",
            new AbilityScoreBonus.Builder()
                    .cha(2).wis(1)
                    .build(),
            0.97f, 1.05f,
            UnlockState.UNLOCKED),

    SCOURGE("Scourge Aasimar", Species.AASIMAR, SourceTag.DND_5E,
            "Scourge aasimar are imbued with a burning desire to destroy evil. " +
                    "They can channel a searing radiance that damages all nearby — " +
                    "including themselves — as they burn away corruption with holy light.",
            new AbilityScoreBonus.Builder()
                    .cha(2).con(1)
                    .build(),
            0.95f, 1.02f,
            UnlockState.UNLOCKED),
    FALLEN("Fallen Aasimar", Species.AASIMAR, SourceTag.DND_5E,
            "Not all aasimar answer the call of good. Fallen aasimar have turned " +
                    "away from the light, their divine spark twisted into something darker. " +
                    "They can channel necrotic energy and inspire dread in those around them.",
            new AbilityScoreBonus.Builder()
                    .cha(2).str(1)
                    .build(),
            0.95f, 1.05f,
            UnlockState.UNLOCKED),
    // ── Gallifreyan Origins───────────────────────────────────────────────────
    GALLIFREYAN("Gallifreyan", Species.GALLIFREYAN, SourceTag.DOCTOR_WHO,
            "A native of the ancient planet Gallifrey. Even without formal Time Lord " +
                    "training, a Gallifreyan's binary vascular system and naturally evolved " +
                    "biology make them remarkably resilient and mentally formidable.",
            new AbilityScoreBonus.Builder()
                    .con(4).intel(1).wis(1)
                    .build(),
            0.95f, 1.05f,
            UnlockState.UNLOCKED),
    // ── Viltrumite Origins ────────────────────────────────────────────────────
    PUREBLOOD_VILTRUMITE("Pureblood Viltrumite", Species.VILTRUMITE, SourceTag.INVINCIBLE,
            "One of the last true Viltrumites. Rare beyond measure, purebloods represent " +
                    "the peak of Viltrumite genetics — raw power, near-invulnerability, " +
                    "and an iron will forged through millennia of war and conquest.",
            new AbilityScoreBonus.Builder()
                    .str(4).con(3).end(2)
                    .build(),
            0.95f, 1.10f,
            UnlockState.UNLOCKED,
            List.of(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "totality", "viltrumite_physiology"))),

    HALF_VILTRUMITE("Half-Viltrumite", Species.VILTRUMITE, SourceTag.INVINCIBLE,
            "Born of Viltrumite and alien blood, half-Viltrumites inherit tremendous " +
                    "physical power alongside a capacity for growth and adaptability that " +
                    "purebloods lack entirely. Mark Grayson proved that hybrid potential " +
                    "can rival even the greatest of the Empire.",
            new AbilityScoreBonus.Builder()
                    .str(2).con(2).end(1).dex(1)
                    .build(),
            0.95f, 1.05f,
            UnlockState.UNLOCKED,
            List.of(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "totality", "viltrumite_physiology"))),
    // ── Kryptonian Origins ───────────────────────────────────────────────────────
    KRYPTONIAN("Kryptonian", Species.KRYPTONIAN, SourceTag.DC_COMICS,
            "A native of the lost planet Krypton...",
            new AbilityScoreBonus.Builder()
                    .str(3).con(3).end(2)
                    .build(),
            0.95f, 1.08f,
            UnlockState.UNLOCKED,
            List.of(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "totality", "kryptonian_physiology")));

    private final String displayName;
    private final Species species;
    private final SourceTag sourceTag;
    private final String description;
    private final AbilityScoreBonus abilityScoreBonus;
    private final float minHeight;
    private final float maxHeight;
    private final UnlockState defaultUnlockState;
    private final List<net.minecraft.resources.Identifier> startingAbilities;

    Origin(String displayName, Species species, SourceTag sourceTag,
           String description, AbilityScoreBonus abilityScoreBonus,
           float minHeight, float maxHeight, UnlockState defaultUnlockState, List<net.minecraft.resources.Identifier> startingAbilities) {
        this.displayName         = displayName;
        this.species             = species;
        this.sourceTag           = sourceTag;
        this.description         = description;
        this.abilityScoreBonus   = abilityScoreBonus;
        this.minHeight           = minHeight;
        this.maxHeight           = maxHeight;
        this.defaultUnlockState  = defaultUnlockState;
        this.startingAbilities   = startingAbilities;
    }

    Origin(String displayName, Species species, SourceTag sourceTag,
           String description, AbilityScoreBonus abilityScoreBonus,
           float minHeight, float maxHeight, UnlockState defaultUnlockState) {
        this(displayName, species, sourceTag, description, abilityScoreBonus,
                minHeight, maxHeight, defaultUnlockState, List.of());
    }

    public String getDisplayName()               { return displayName; }
    public Species getSpecies()                  { return species; }
    public SourceTag getSourceTag()              { return sourceTag; }
    public String getDescription()               { return description; }
    public AbilityScoreBonus getAbilityScoreBonus() { return abilityScoreBonus; }
    public float getMinHeight()                  { return minHeight; }
    public float getMaxHeight()                  { return maxHeight; }
    public UnlockState getDefaultUnlockState()   { return defaultUnlockState; }

    /** Generates a random height within this origin's range. */
    public float randomHeight(net.minecraft.util.RandomSource random) {
        return minHeight + random.nextFloat() * (maxHeight - minHeight);
    }

    /** Returns all origins belonging to the given species. */
    public static List<Origin> getForSpecies(Species species) {
        return Arrays.stream(values())
                .filter(o -> o.species == species)
                .toList();
    }

    /** Returns true if the given species has any origins. */
    public static boolean hasOrigins(Species species) {
        return Arrays.stream(values())
                .anyMatch(o -> o.species == species);
    }

    /** Returns all unlocked origins for a species. */
    public static List<Origin> getUnlockedForSpecies(Species species) {
        return Arrays.stream(values())
                .filter(o -> o.species == species
                        && o.defaultUnlockState == UnlockState.UNLOCKED)
                .toList();
    }

    public List<net.minecraft.resources.Identifier> getStartingAbilities() {
        return startingAbilities;
    }
}