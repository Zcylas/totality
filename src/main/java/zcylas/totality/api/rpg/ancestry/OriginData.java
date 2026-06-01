// api/rpg/ancestry/OriginData.java
package zcylas.totality.api.rpg.ancestry;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import zcylas.totality.api.rpg.stats.AbilityScoreBonus;

import java.util.List;

/**
 * Data-driven origin definition. Replaces the Origin enum.
 * Contains all stat bonuses, source tag, height range, unlock state,
 * and starting abilities for a species variant.
 *
 * Resistances are applied programmatically in DamageResistanceRecalculator
 * rather than stored here, since they require TotalityDamageType references.
 */
public final class OriginData {

    private final Identifier        id;
    private final String            displayName;
    private final Identifier        speciesId;
    private final SourceTag         sourceTag;
    private final String            description;
    private final AbilityScoreBonus abilityScoreBonus;
    private final float             minHeight;
    private final float             maxHeight;
    private final UnlockState       unlockState;
    private final List<Identifier>  startingAbilities;

    // ── Full constructor ──────────────────────────────────────────────────────

    public OriginData(Identifier id, String displayName, Identifier speciesId,
                      SourceTag sourceTag, String description,
                      AbilityScoreBonus abilityScoreBonus,
                      float minHeight, float maxHeight,
                      UnlockState unlockState, List<Identifier> startingAbilities) {
        this.id                = id;
        this.displayName       = displayName;
        this.speciesId         = speciesId;
        this.sourceTag         = sourceTag;
        this.description       = description;
        this.abilityScoreBonus = abilityScoreBonus;
        this.minHeight         = minHeight;
        this.maxHeight         = maxHeight;
        this.unlockState       = unlockState;
        this.startingAbilities = List.copyOf(startingAbilities);
    }

    // ── Convenience: no starting abilities ───────────────────────────────────

    public OriginData(Identifier id, String displayName, Identifier speciesId,
                      SourceTag sourceTag, String description,
                      AbilityScoreBonus abilityScoreBonus,
                      float minHeight, float maxHeight, UnlockState unlockState) {
        this(id, displayName, speciesId, sourceTag, description,
                abilityScoreBonus, minHeight, maxHeight, unlockState, List.of());
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Identifier        getId()                { return id; }
    public String            getDisplayName()       { return displayName; }
    public Identifier        getSpeciesId()         { return speciesId; }
    public SourceTag         getSourceTag()         { return sourceTag; }
    public String            getDescription()       { return description; }
    public AbilityScoreBonus getAbilityScoreBonus() { return abilityScoreBonus; }
    public float             getMinHeight()         { return minHeight; }
    public float             getMaxHeight()         { return maxHeight; }
    public UnlockState       getUnlockState()       { return unlockState; }
    public List<Identifier>  getStartingAbilities() { return startingAbilities; }

    public float randomHeight(RandomSource random) {
        return minHeight + random.nextFloat() * (maxHeight - minHeight);
    }

    @Override public String toString() { return id.toString(); }
}