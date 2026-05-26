package zcylas.totality.api.rpg.ancestry;

/**
 * Identifies the source universe/game a Species or Origin comes from.
 * Used for display purposes in the ancestry selection screen.
 */
public enum SourceTag {

    DND_5E("D&D 5e", 0xFF4466FF),
    ELDER_SCROLLS("Elder Scrolls", 0xFF884400),
    BALDURS_GATE_3("Baldur's Gate 3", 0xFF662299),
    DOCTOR_WHO("Doctor Who", 0xFF005B8E),
    INVINCIBLE("Invincible", 0xFFFFCC00),
    DC_COMICS("DC Comics", 0xFF0057B8),
    WORLD_OF_WARCRAFT("World of Warcraft", 0xFF0044AA),
    FINAL_FANTASY("Final Fantasy", 0xFF6600CC),
    TOTALITY("Totality", 0xFF00CCFF),
    OTHER("Other", 0xFF888888);

    private final String displayName;
    private final int color;

    SourceTag(String displayName, int color) {
        this.displayName = displayName;
        this.color       = color;
    }

    public String getDisplayName() { return displayName; }
    public int getColor()          { return color; }
}