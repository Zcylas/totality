package zcylas.totality.api.mob.stats;

public enum SpawnRarity {
    COMMON, UNCOMMON, RARE, EPIC, LEGENDARY;

    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static SpawnRarity fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (Exception e) { return COMMON; }
    }
}