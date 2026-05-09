package zcylas.totality.api.rpg.combat.bow;

/**
 * Interface for items that should be treated as bows for stamina purposes.
 * Implement this on any custom bow item to hook into the bow stamina system.
 *
 * Vanilla bows and crossbows are handled separately via instanceof checks
 * in BowStaminaHandler.
 */
public interface TotalityBowItem {

    /**
     * Returns the type of this bow for stamina calculation purposes.
     */
    BowType getBowType();

    /**
     * Returns the stamina cost per second while holding this bow fully drawn.
     * Default is 5, matching Nolvus values.
     * Override to customize per bow.
     */
    default int getDrawStaminaCostPerSecond() {
        return BowType.BOW.drawCostPerSecond();
    }

    /**
     * Returns the one-time stamina cost to load this bow (for crossbow types).
     * Default is 15.
     * Override to customize per crossbow.
     */
    default int getLoadStaminaCost() {
        return BowType.CROSSBOW.loadCost();
    }
}