package zcylas.totality.api.combat.bow;

/**
 * Defines the stamina behavior type for bow items.
 *
 * BOW      → drains stamina per second while held drawn, cancelled at 0 stamina
 * CROSSBOW → one-time stamina cost to load, no drain while held
 */
public enum BowType {
    BOW(5, 0),       // 5 stamina/second drain while drawn, no load cost
    CROSSBOW(0, 15); // no drain while held, 15 stamina to load

    private final int drawCostPerSecond;
    private final int loadCost;

    BowType(int drawCostPerSecond, int loadCost) {
        this.drawCostPerSecond = drawCostPerSecond;
        this.loadCost = loadCost;
    }

    public int drawCostPerSecond() { return drawCostPerSecond; }
    public int loadCost()          { return loadCost; }
}