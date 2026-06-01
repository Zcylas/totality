// api/rpg/ancestry/OriginRegistry.java
package zcylas.totality.api.rpg.ancestry;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.stats.AbilityScoreBonus;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for all playable origins.
 * Call {@link #init()} during mod initialization to trigger class loading.
 */
public final class OriginRegistry {

    private static final Map<Identifier, OriginData> REGISTRY = new LinkedHashMap<>();

    private OriginRegistry() {}

    // ── API ───────────────────────────────────────────────────────────────────

    public static OriginData register(OriginData data) {
        REGISTRY.put(data.getId(), data);
        return data;
    }

    public static OriginData get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static Collection<OriginData> all() {
        return REGISTRY.values();
    }

    public static List<OriginData> getForSpecies(Identifier speciesId) {
        return REGISTRY.values().stream()
                .filter(o -> o.getSpeciesId().equals(speciesId))
                .toList();
    }

    public static List<OriginData> getUnlockedForSpecies(Identifier speciesId) {
        return REGISTRY.values().stream()
                .filter(o -> o.getSpeciesId().equals(speciesId)
                        && o.getUnlockState() == UnlockState.UNLOCKED)
                .toList();
    }

    public static boolean hasOrigins(Identifier speciesId) {
        return REGISTRY.values().stream()
                .anyMatch(o -> o.getSpeciesId().equals(speciesId));
    }

    /** Force class load and registration. Call from registerApi(). */
    public static void init() {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static Identifier sp(String speciesPath) {
        return Identifier.fromNamespaceAndPath("totality", speciesPath);
    }

    private static Identifier ability(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    // ── Human Origins ─────────────────────────────────────────────────────────

    public static final OriginData STANDARD_HUMAN = register(new OriginData(
            id("standard_human"), "Standard Human", sp("human"), SourceTag.DND_5E,
            "The most common human variant, defined by remarkable adaptability. " +
                    "Standard humans receive a small bonus to all attributes, " +
                    "reflecting their natural versatility and drive.",
            new AbilityScoreBonus.Builder()
                    .str(1).dex(1).con(1).end(1).intel(1).wis(1).cha(1).fth(1).build(),
            0.95f, 1.05f, UnlockState.UNLOCKED));

    public static final OriginData NORD = register(new OriginData(
            id("nord"), "Nord", sp("human"), SourceTag.ELDER_SCROLLS,
            "Born in the frozen province of Skyrim, Nords are a hardy and warlike " +
                    "people. They are fierce warriors hardened by brutal winters and " +
                    "an unbreakable fighting spirit that has made them legendary across Tamriel.",
            new AbilityScoreBonus.Builder().str(2).con(1).build(),
            1.0f, 1.08f, UnlockState.UNLOCKED));

    public static final OriginData IMPERIAL = register(new OriginData(
            id("imperial"), "Imperial", sp("human"), SourceTag.ELDER_SCROLLS,
            "Natives of the civilized heartland of Cyrodiil, Imperials are diplomats " +
                    "and administrators by nature. Their gift for commerce and negotiation " +
                    "has allowed them to build and maintain the greatest empire Tamriel has ever seen.",
            new AbilityScoreBonus.Builder().cha(1).wis(1).intel(1).build(),
            0.97f, 1.03f, UnlockState.UNLOCKED));

    public static final OriginData REDGUARD = register(new OriginData(
            id("redguard"), "Redguard", sp("human"), SourceTag.ELDER_SCROLLS,
            "The most naturally talented warriors in Tamriel, Redguards come from " +
                    "the arid province of Hammerfell. Their culture is defined by martial " +
                    "excellence, honor, and an almost supernatural stamina in battle.",
            new AbilityScoreBonus.Builder().str(1).dex(1).end(1).build(),
            0.97f, 1.05f, UnlockState.UNLOCKED));

    public static final OriginData BRETON = register(new OriginData(
            id("breton"), "Breton", sp("human"), SourceTag.ELDER_SCROLLS,
            "Born of the intermingling of human and elven blood in High Rock, Bretons " +
                    "possess a natural affinity for magic. They are gifted spellcasters " +
                    "with an innate resistance to the arcane.",
            new AbilityScoreBonus.Builder().intel(1).wis(1).build(),
            0.95f, 1.0f, UnlockState.UNLOCKED));

    // ── Elf Origins ───────────────────────────────────────────────────────────

    public static final OriginData HIGH_ELF = register(new OriginData(
            id("high_elf"), "High Elf", sp("elf"), SourceTag.DND_5E,
            "Proud and learned, high elves have devoted centuries to the mastery " +
                    "of arcane magic. They are tall, perceptive, and carry themselves " +
                    "with an air of quiet superiority.",
            new AbilityScoreBonus.Builder().dex(1).intel(1).build(),
            1.08f, 1.15f, UnlockState.UNLOCKED));

    public static final OriginData WOOD_ELF = register(new OriginData(
            id("wood_elf"), "Wood Elf", sp("elf"), SourceTag.DND_5E,
            "Swift and stealthy, wood elves are one with the forests they inhabit. " +
                    "They are expert hunters and trackers, moving through the wilderness " +
                    "like shadows.",
            new AbilityScoreBonus.Builder().dex(1).wis(1).build(),
            0.98f, 1.05f, UnlockState.UNLOCKED));

    public static final OriginData DARK_ELF = register(new OriginData(
            id("dark_elf"), "Dark Elf", sp("elf"), SourceTag.DND_5E,
            "Exiled from the surface world, dark elves have thrived in the depths " +
                    "through cunning and ruthlessness. They are masters of shadow magic and poison.",
            new AbilityScoreBonus.Builder().dex(1).cha(1).build(),
            1.0f, 1.08f, UnlockState.UNLOCKED));

    public static final OriginData DUNMER = register(new OriginData(
            id("dunmer"), "Dunmer", sp("elf"), SourceTag.ELDER_SCROLLS,
            "The ash-born of Morrowind. Forged by volcano and prophecy, the Dunmer " +
                    "possess a natural affinity for mysticism and resilience. They are " +
                    "noted for their stealth, magic, and fierce independence.",
            new AbilityScoreBonus.Builder().intel(1).dex(1).build(),
            1.0f, 1.08f, UnlockState.UNLOCKED));

    public static final OriginData DROW = register(new OriginData(
            id("drow"), "Drow", sp("elf"), SourceTag.BALDURS_GATE_3,
            "Children of the Underdark, drow are dark elves who have built a " +
                    "powerful and ruthless civilization beneath the surface. They are " +
                    "gifted with innate magic and deadly precision.",
            new AbilityScoreBonus.Builder().dex(1).cha(1).build(),
            1.0f, 1.08f, UnlockState.UNLOCKED));

    // ── Dwarf Origins ─────────────────────────────────────────────────────────

    public static final OriginData HILL_DWARF = register(new OriginData(
            id("hill_dwarf"), "Hill Dwarf", sp("dwarf"), SourceTag.DND_5E,
            "Hardier than their mountain kin, hill dwarves have developed an almost " +
                    "supernatural toughness. They are wise, perceptive, and exceptionally " +
                    "difficult to kill.",
            new AbilityScoreBonus.Builder().con(2).wis(1).build(),
            0.75f, 0.80f, UnlockState.UNLOCKED));

    public static final OriginData MOUNTAIN_DWARF = register(new OriginData(
            id("mountain_dwarf"), "Mountain Dwarf", sp("dwarf"), SourceTag.DND_5E,
            "Trained from birth in the arts of war and stonecraft, mountain dwarves " +
                    "are the strongest of their kind. They are disciplined, powerful, " +
                    "and built like the rock they call home.",
            new AbilityScoreBonus.Builder().con(2).str(2).build(),
            0.80f, 0.85f, UnlockState.UNLOCKED));

    // ── Halfling Origins ──────────────────────────────────────────────────────

    public static final OriginData LIGHTFOOT = register(new OriginData(
            id("lightfoot"), "Lightfoot", sp("halfling"), SourceTag.DND_5E,
            "Lightfoot halflings are remarkably stealthy and have a gift for blending " +
                    "into crowds. They move through the world quietly, rarely noticed " +
                    "by those who tower above them.",
            new AbilityScoreBonus.Builder().dex(2).cha(1).build(),
            0.55f, 0.60f, UnlockState.UNLOCKED));

    public static final OriginData STOUT = register(new OriginData(
            id("stout"), "Stout", sp("halfling"), SourceTag.DND_5E,
            "Stout halflings are hardier than their lightfoot kin. Some believe they " +
                    "carry a trace of dwarven blood, which would explain their toughness " +
                    "and their fondness for a good meal and a warm hearth.",
            new AbilityScoreBonus.Builder().dex(2).con(1).build(),
            0.58f, 0.65f, UnlockState.UNLOCKED));

    // ── Gnome Origins ─────────────────────────────────────────────────────────

    public static final OriginData FOREST_GNOME = register(new OriginData(
            id("forest_gnome"), "Forest Gnome", sp("gnome"), SourceTag.DND_5E,
            "Forest gnomes have a natural gift for illusion and an innate ability " +
                    "to befriend small animals. They live in hidden woodland communities, " +
                    "rarely seen by other races.",
            new AbilityScoreBonus.Builder().intel(2).dex(1).build(),
            0.65f, 0.70f, UnlockState.UNLOCKED));

    public static final OriginData ROCK_GNOME = register(new OriginData(
            id("rock_gnome"), "Rock Gnome", sp("gnome"), SourceTag.DND_5E,
            "The most common gnomes in the world, rock gnomes have a natural " +
                    "inventiveness and hardiness. They are tinkerers and artificers " +
                    "at heart, always curious about how things work.",
            new AbilityScoreBonus.Builder().intel(2).con(1).build(),
            0.68f, 0.75f, UnlockState.UNLOCKED));

    public static final OriginData DEEP_GNOME = register(new OriginData(
            id("deep_gnome"), "Deep Gnome", sp("gnome"), SourceTag.DND_5E,
            "Also known as svirfneblin, deep gnomes dwell in the Underdark far " +
                    "beneath the surface. Grim and practical compared to their surface " +
                    "cousins, they have adapted to survive in one of the most dangerous " +
                    "environments in the world.",
            new AbilityScoreBonus.Builder().intel(2).dex(1).build(),
            0.63f, 0.68f, UnlockState.UNLOCKED));

    // ── Goliath Origins ───────────────────────────────────────────────────────

    public static final OriginData GOLIATH_DND = register(new OriginData(
            id("goliath_dnd"), "Goliath", sp("goliath"), SourceTag.DND_5E,
            "Towering and powerful, goliaths live in the highest mountain peaks. " +
                    "They live by a strict code of competition and self-sufficiency, " +
                    "always striving to surpass their own limits.",
            new AbilityScoreBonus.Builder().str(2).con(1).build(),
            1.35f, 1.45f, UnlockState.UNLOCKED));

    // ── Aasimar Origins ───────────────────────────────────────────────────────

    public static final OriginData PROTECTOR = register(new OriginData(
            id("protector"), "Protector Aasimar", sp("aasimar"), SourceTag.DND_5E,
            "Protector aasimar are charged by the powers of good to guard the weak " +
                    "and strike at evil. They are suffused with a divine radiance that " +
                    "can be unleashed to smite enemies and inspire allies.",
            new AbilityScoreBonus.Builder().cha(2).wis(1).build(),
            0.97f, 1.05f, UnlockState.UNLOCKED));

    public static final OriginData SCOURGE = register(new OriginData(
            id("scourge"), "Scourge Aasimar", sp("aasimar"), SourceTag.DND_5E,
            "Scourge aasimar are imbued with a burning desire to destroy evil. " +
                    "They can channel a searing radiance that damages all nearby — " +
                    "including themselves — as they burn away corruption with holy light.",
            new AbilityScoreBonus.Builder().cha(2).con(1).build(),
            0.95f, 1.02f, UnlockState.UNLOCKED));

    public static final OriginData FALLEN = register(new OriginData(
            id("fallen"), "Fallen Aasimar", sp("aasimar"), SourceTag.DND_5E,
            "Not all aasimar answer the call of good. Fallen aasimar have turned " +
                    "away from the light, their divine spark twisted into something darker. " +
                    "They can channel necrotic energy and inspire dread in those around them.",
            new AbilityScoreBonus.Builder().cha(2).str(1).build(),
            0.95f, 1.05f, UnlockState.UNLOCKED));

    // ── Gallifreyan Origins ───────────────────────────────────────────────────

    public static final OriginData GALLIFREYAN = register(new OriginData(
            id("gallifreyan"), "Gallifreyan", sp("gallifreyan"), SourceTag.DOCTOR_WHO,
            "A native of the ancient planet Gallifrey. Even without formal Time Lord " +
                    "training, a Gallifreyan's binary vascular system and naturally evolved " +
                    "biology make them remarkably resilient and mentally formidable.",
            new AbilityScoreBonus.Builder().con(4).intel(1).wis(1).build(),
            0.95f, 1.05f, UnlockState.UNLOCKED));

    // ── Viltrumite Origins ────────────────────────────────────────────────────

    public static final OriginData PUREBLOOD_VILTRUMITE = register(new OriginData(
            id("pureblood_viltrumite"), "Pureblood Viltrumite", sp("viltrumite"),
            SourceTag.INVINCIBLE,
            "One of the last true Viltrumites. Rare beyond measure, purebloods represent " +
                    "the peak of Viltrumite genetics — raw power, near-invulnerability, " +
                    "and an iron will forged through millennia of war and conquest.",
            new AbilityScoreBonus.Builder().str(4).con(3).end(2).build(),
            0.95f, 1.10f, UnlockState.UNLOCKED,
            List.of(ability("viltrumite_physiology"))));

    public static final OriginData HALF_VILTRUMITE = register(new OriginData(
            id("half_viltrumite"), "Half-Viltrumite", sp("viltrumite"),
            SourceTag.INVINCIBLE,
            "Born of Viltrumite and alien blood, half-Viltrumites inherit tremendous " +
                    "physical power alongside a capacity for growth and adaptability that " +
                    "purebloods lack entirely. Mark Grayson proved that hybrid potential " +
                    "can rival even the greatest of the Empire.",
            new AbilityScoreBonus.Builder().str(2).con(2).end(1).dex(1).build(),
            0.95f, 1.05f, UnlockState.UNLOCKED,
            List.of(ability("viltrumite_physiology"))));

    // ── Kryptonian Origins ────────────────────────────────────────────────────

    public static final OriginData KRYPTONIAN = register(new OriginData(
            id("kryptonian"), "Kryptonian", sp("kryptonian"), SourceTag.DC_COMICS,
            "A native of the lost planet Krypton. Under the light of a yellow sun, " +
                    "Kryptonians develop extraordinary strength, durability, flight, " +
                    "enhanced senses, and other abilities that make them appear almost divine.",
            new AbilityScoreBonus.Builder().str(3).con(3).end(2).build(),
            0.95f, 1.08f, UnlockState.UNLOCKED,
            List.of(
                    ability("kryptonian_physiology"),
                    ability("heat_vision")
            )));
}