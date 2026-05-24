package zcylas.totality.api.magic.grimoire.rune;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractRune {

    private final Identifier id;
    private final String name;

    // Augment descriptions — rune-specific descriptions of what each augment does
    public Map<String, String> augmentDescriptions = new ConcurrentHashMap<>();

    public AbstractRune(String id, String name) {
        this.id = Identifier.fromNamespaceAndPath("totality", id);
        this.name = name;
        // Populate augment descriptions on construction
        buildAugmentDescriptions(augmentDescriptions);
    }

    public Identifier getId() { return id; }
    public String getName() { return name; }

    public abstract int getManaCost();
    public abstract int getTier();
    public abstract Identifier getIcon();
    public abstract int getTypeIndex();

    public String getDescription() { return ""; }

    /**
     * Returns the set of augment IDs compatible with this rune.
     * Empty set means no augments are compatible.
     * Forms return empty since augments apply to effects.
     */
    public Set<String> getCompatibleAugments() {
        return Set.of();
    }

    /**
     * Override to provide per-augment descriptions for this rune.
     * Key is the augment ID (e.g. "amplify"), value is the description.
     */
    public void buildAugmentDescriptions(Map<String, String> map) {}

    /**
     * Get the description of what a specific augment does to this rune.
     */
    public String getAugmentDescription(String augmentId) {
        return augmentDescriptions.getOrDefault(augmentId, "");
    }
}