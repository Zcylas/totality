// api/rpg/ancestry/SpeciesRegistry.java
package zcylas.totality.api.rpg.ancestry;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for all playable species.
 * Call {@link #init()} during mod initialization to trigger class loading.
 */
public final class SpeciesRegistry {

    private static final Map<Identifier, SpeciesData> REGISTRY = new LinkedHashMap<>();

    private SpeciesRegistry() {}

    // ── API ───────────────────────────────────────────────────────────────────

    public static SpeciesData register(SpeciesData data) {
        REGISTRY.put(data.getId(), data);
        return data;
    }

    public static SpeciesData get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static Collection<SpeciesData> all() {
        return REGISTRY.values();
    }

    public static List<SpeciesData> getForCategory(SpeciesCategory category) {
        return REGISTRY.values().stream()
                .filter(s -> s.getCategory() == category)
                .toList();
    }

    public static List<SpeciesCategory> getUsedCategories() {
        return REGISTRY.values().stream()
                .map(SpeciesData::getCategory)
                .distinct()
                .toList();
    }

    /** Force class load and registration. Call from registerApi(). */
    public static void init() {}

    // ── Entries ───────────────────────────────────────────────────────────────

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    // ── Humanoid ──────────────────────────────────────────────────────────────

    public static final SpeciesData HUMAN = register(new SpeciesData(
            id("human"), "Human", SpeciesCategory.HUMANOID,
            "The most widespread people in the known world. Humans are defined not " +
                    "by their biology but by their culture and ambition. Adaptable, driven, " +
                    "and remarkably diverse, they have spread across every corner of the world.",
            0.95f, 1.05f));

    public static final SpeciesData ELF = register(new SpeciesData(
            id("elf"), "Elf", SpeciesCategory.HUMANOID,
            "Ancient and graceful beings attuned to magic and nature. Elves live far " +
                    "longer than most races and carry with them centuries of memory, wisdom, " +
                    "and a deep connection to the unseen currents of the world.",
            1.0f, 1.15f));

    public static final SpeciesData DWARF = register(new SpeciesData(
            id("dwarf"), "Dwarf", SpeciesCategory.HUMANOID,
            "Stout and enduring, dwarves are renowned for their craftsmanship, their " +
                    "loyalty, and their unbreakable bond with stone and metal. They are " +
                    "among the most resilient peoples in existence.",
            0.75f, 0.85f));

    public static final SpeciesData HALFLING = register(new SpeciesData(
            id("halfling"), "Halfling", SpeciesCategory.HUMANOID,
            "Small in stature but remarkably resilient, halflings have a legendary " +
                    "knack for surviving situations that would fell larger folk. " +
                    "Their quiet courage and natural luck are the stuff of legend.",
            0.55f, 0.65f));

    public static final SpeciesData GNOME = register(new SpeciesData(
            id("gnome"), "Gnome", SpeciesCategory.HUMANOID,
            "Small, curious, and boundlessly inventive, gnomes approach the world " +
                    "with an enthusiasm that larger races often find infectious. " +
                    "Their natural affinity for magic and tinkering has produced some " +
                    "of the greatest minds the world has ever seen.",
            0.60f, 0.75f));

    // ── Giant-kin ─────────────────────────────────────────────────────────────

    public static final SpeciesData GOLIATH = register(new SpeciesData(
            id("goliath"), "Goliath", SpeciesCategory.GIANT_KIN,
            "Towering and powerful, goliaths are a nomadic people who dwell in " +
                    "the highest mountain peaks. They live by a strict code of competition " +
                    "and self-sufficiency, always striving to surpass their own limits.",
            1.35f, 1.45f));

    // ── Celestial ─────────────────────────────────────────────────────────────

    public static final SpeciesData AASIMAR = register(new SpeciesData(
            id("aasimar"), "Aasimar", SpeciesCategory.CELESTIAL,
            "Touched by celestial power, aasimar carry a spark of the divine within " +
                    "them. Born of mortal parents but shaped by heavenly influence, they " +
                    "are placed in the world to serve the forces of light — though not " +
                    "all choose to answer that calling.",
            0.95f, 1.05f));

    // ── Alien ─────────────────────────────────────────────────────────────────

    public static final SpeciesData GALLIFREYAN = register(new SpeciesData(
            id("gallifreyan"), "Gallifreyan", SpeciesCategory.ALIEN,
            "An ancient and highly evolved people from the planet Gallifrey. " +
                    "Gallifreyans possess a binary vascular system, formidable mental " +
                    "discipline, and a unique relationship with time itself that sets " +
                    "them apart from every other species in the known universe.",
            0.95f, 1.05f));

    public static final SpeciesData VILTRUMITE = register(new SpeciesData(
            id("viltrumite"), "Viltrumite", SpeciesCategory.ALIEN,
            "A near-extinct race of conquerors from the planet Viltrum. Viltrumites are " +
                    "among the most physically powerful beings in the universe, capable of " +
                    "flight, superhuman strength, and near-immortality. Their empire once " +
                    "spanned countless worlds before the Scourge Virus reduced their " +
                    "pure-blooded population to a mere handful.",
            0.95f, 1.10f));

    public static final SpeciesData KRYPTONIAN = register(new SpeciesData(
            id("kryptonian"), "Kryptonian", SpeciesCategory.ALIEN,
            "A powerful alien species from the lost world of Krypton. Under the light " +
                    "of a yellow sun, Kryptonians develop extraordinary strength, durability, " +
                    "flight, enhanced senses, and other abilities that make them appear almost divine.",
            0.95f, 1.08f));
}