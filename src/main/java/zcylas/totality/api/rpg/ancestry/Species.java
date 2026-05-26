package zcylas.totality.api.rpg.ancestry;

import java.util.Arrays;
import java.util.List;

/**
 * Defines a playable species in the Totality ancestry system.
 *
 * Species represents the biological/fantasy identity of the player —
 * their body type, visual base, lore, and category.
 *
 * Stats and abilities live on Origin, not Species.
 * Species only contains things truly universal to all variants of that species.
 */
public enum Species {

    // ── Humanoid ──────────────────────────────────────────────────────────────
    HUMAN("Human", SpeciesCategory.HUMANOID,
            "The most widespread people in the known world. Humans are defined not " +
                    "by their biology but by their culture and ambition. Adaptable, driven, " +
                    "and remarkably diverse, they have spread across every corner of the world.",
            0.95f, 1.05f),

    ELF("Elf", SpeciesCategory.HUMANOID,
            "Ancient and graceful beings attuned to magic and nature. Elves live far " +
                    "longer than most races and carry with them centuries of memory, wisdom, " +
                    "and a deep connection to the unseen currents of the world.",
            1.0f, 1.15f),

    DWARF("Dwarf", SpeciesCategory.HUMANOID,
            "Stout and enduring, dwarves are renowned for their craftsmanship, their " +
                    "loyalty, and their unbreakable bond with stone and metal. They are " +
                    "among the most resilient peoples in existence.",
            0.75f, 0.85f),

    HALFLING("Halfling", SpeciesCategory.HUMANOID,
            "Small in stature but remarkably resilient, halflings have a legendary " +
                    "knack for surviving situations that would fell larger folk. " +
                    "Their quiet courage and natural luck are the stuff of legend.",
            0.55f, 0.65f),

    GNOME("Gnome", SpeciesCategory.HUMANOID,
            "Small, curious, and boundlessly inventive, gnomes approach the world " +
                    "with an enthusiasm that larger races often find infectious. " +
                    "Their natural affinity for magic and tinkering has produced some " +
                    "of the greatest minds the world has ever seen.",
            0.60f, 0.75f),

    // ── Giant-kin ─────────────────────────────────────────────────────────────
    GOLIATH("Goliath", SpeciesCategory.GIANT_KIN,
            "Towering and powerful, goliaths are a nomadic people who dwell in " +
                    "the highest mountain peaks. They live by a strict code of competition " +
                    "and self-sufficiency, always striving to surpass their own limits.",
            1.35f, 1.45f),

    // ── Celestial ─────────────────────────────────────────────────────────────
    AASIMAR("Aasimar", SpeciesCategory.CELESTIAL,
            "Touched by celestial power, aasimar carry a spark of the divine within " +
                    "them. Born of mortal parents but shaped by heavenly influence, they " +
                    "are placed in the world to serve the forces of light — though not " +
                    "all choose to answer that calling.",
            0.95f, 1.05f),
    // ── Alien ─────────────────────────────────────────────────────────────────
    GALLIFREYAN("Gallifreyan", SpeciesCategory.ALIEN,
            "An ancient and highly evolved people from the planet Gallifrey. " +
                    "Gallifreyans possess a binary vascular system, formidable mental " +
                    "discipline, and a unique relationship with time itself that sets " +
                    "them apart from every other species in the known universe.",
            0.95f, 1.05f),
    VILTRUMITE("Viltrumite", SpeciesCategory.ALIEN,
            "A near-extinct race of conquerors from the planet Viltrum. Viltrumites are " +
                    "among the most physically powerful beings in the universe, capable of " +
                    "flight, superhuman strength, and near-immortality. Their empire once " +
                    "spanned countless worlds before the Scourge Virus reduced their " +
                    "pure-blooded population to a mere handful.",
            0.95f, 1.10f),
    KRYPTONIAN("Kryptonian", SpeciesCategory.ALIEN,
        "A powerful alien species from the lost world of Krypton. Under the light " +
                "of a yellow sun, Kryptonians develop extraordinary strength, durability, " +
                "flight, enhanced senses, and other abilities that make them appear almost divine.",
                0.95f, 1.08f);

    private final String displayName;
    private final SpeciesCategory category;
    private final String description;
    private final float minHeight;
    private final float maxHeight;

    Species(String displayName, SpeciesCategory category, String description,
            float minHeight, float maxHeight) {
        this.displayName = displayName;
        this.category    = category;
        this.description = description;
        this.minHeight   = minHeight;
        this.maxHeight   = maxHeight;
    }

    public String getDisplayName()      { return displayName; }
    public SpeciesCategory getCategory() { return category; }
    public String getDescription()      { return description; }
    public float getMinHeight()         { return minHeight; }
    public float getMaxHeight()         { return maxHeight; }

    /** Returns all species belonging to the given category. */
    public static List<Species> getForCategory(SpeciesCategory category) {
        return Arrays.stream(values())
                .filter(s -> s.category == category)
                .toList();
    }

    /** Returns all categories that have at least one species. */
    public static List<SpeciesCategory> getUsedCategories() {
        return Arrays.stream(values())
                .map(s -> s.category)
                .distinct()
                .toList();
    }

    /** Generates a random height scale within this species' range. */
    public float randomHeight(net.minecraft.util.RandomSource random) {
        return minHeight + random.nextFloat() * (maxHeight - minHeight);
    }
}