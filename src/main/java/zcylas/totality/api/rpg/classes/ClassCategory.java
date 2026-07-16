package zcylas.totality.api.rpg.classes;

public enum ClassCategory {
    MARTIAL ("⚔", "Martial",  "Classes built on physical combat, endurance and raw power."),
    ARCANE  ("✦", "Arcane",   "Classes that harness magical power through study or pact."),
    DIVINE  ("☩", "Divine",   "Classes granted power through faith and divine connection."),
    CUNNING ("◈", "Cunning",  "Classes that rely on speed, deception and versatility.");

    private final String icon, displayName, description;

    ClassCategory(String icon, String displayName, String description) {
        this.icon = icon; this.displayName = displayName; this.description = description;
    }

    public String getIcon()        { return icon; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}