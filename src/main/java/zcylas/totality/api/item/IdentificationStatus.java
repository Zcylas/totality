package zcylas.totality.api.item;

/**
 * Identification state of a Totality item.
 *
 * UNIDENTIFIED   — item shows a generic description; stats are hidden.
 *                  Requires Identify (1st level) or similar to reveal basic info.
 *
 * PARTIALLY      — basic stats are visible (attack, defense, basic enchants).
 *                  Revealed by standard Identify spell.
 *                  Powerful or cursed items stop here.
 *
 * IDENTIFIED     — all stats fully visible, including hidden properties,
 *                  legendary effects, and curses.
 *                  Requires Legend Lore or a higher-tier Identify.
 */
public enum IdentificationStatus {

    UNIDENTIFIED("Unidentified"),
    PARTIALLY("Partially Identified"),
    IDENTIFIED("Identified");

    private final String displayName;

    IdentificationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    public boolean isAtLeast(IdentificationStatus other) {
        return this.ordinal() >= other.ordinal();
    }

    public boolean isFullyIdentified() { return this == IDENTIFIED; }
    public boolean isUnidentified()    { return this == UNIDENTIFIED; }
}