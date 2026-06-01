// api/rpg/ancestry/SpeciesData.java
package zcylas.totality.api.rpg.ancestry;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Data-driven species definition. Replaces the Species enum.
 * All fields are purely descriptive — stats live on OriginData.
 */
public final class SpeciesData {

    private final Identifier      id;
    private final String          displayName;
    private final SpeciesCategory category;
    private final String          description;
    private final float           minHeight;
    private final float           maxHeight;

    public enum RestProfile {
        NORMAL,   // needs bed or outdoor camp for Long Rest
        TRANCE,   // Elves — Long Rest anywhere, half the wait time
        VIGIL     // Kryptonian/Viltrumite/Gallifreyan — Long Rest anywhere, minimal wait
    }

    public SpeciesData(Identifier id, String displayName, SpeciesCategory category,
                       String description, float minHeight, float maxHeight) {
        this.id          = id;
        this.displayName = displayName;
        this.category    = category;
        this.description = description;
        this.minHeight   = minHeight;
        this.maxHeight   = maxHeight;
    }

    public Identifier      getId()          { return id; }
    public String          getDisplayName() { return displayName; }
    public SpeciesCategory getCategory()    { return category; }
    public String          getDescription() { return description; }
    public float           getMinHeight()   { return minHeight; }
    public float           getMaxHeight()   { return maxHeight; }

    public float randomHeight(RandomSource random) {
        return minHeight + random.nextFloat() * (maxHeight - minHeight);
    }

    @Override public String toString() { return id.toString(); }
}