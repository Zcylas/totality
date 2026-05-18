package zcylas.totality.api.rpg.skills.alchemy;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;

import java.util.List;

/**
 * Wires Alchemy XP into the existing alchemy systems.
 *
 * This class is NOT a Fabric event listener — it provides static methods
 * that are called directly from the existing alchemy logic:
 *
 *   1. AlchemyIngredient.onAlchemyEat()  → call onIngredientEaten()
 *   2. BrewingLogic.brew()               → call onPotionBrewed()
 *
 * XP sources:
 *   Eat ingredient, new effect discovered  → EAT_DISCOVERY_XP  (10)
 *   Brew potion, per effect in result      → effect.getXpValue()
 *   Brew potion, new effect discovered     → BREW_DISCOVERY_XP (15) bonus per new effect
 *   Brew potion, first time this recipe    → RECIPE_DISCOVERY_XP (25) bonus
 */
public final class AlchemySkillEvents {

    /** XP when eating an ingredient reveals a new effect for the first time. */
    private static final int EAT_DISCOVERY_XP = 10;

    /** Bonus XP when brewing reveals a new effect slot on any ingredient. */
    private static final int BREW_DISCOVERY_XP = 15;

    /** Bonus XP the first time a specific potion recipe is successfully brewed. */
    private static final int RECIPE_DISCOVERY_XP = 25;

    // ── Hook 1: Ingredient eaten ──────────────────────────────────────────────

    /**
     * Call this from AlchemyIngredient.onAlchemyEat() AFTER revealEffect() runs.
     *
     * @param player     the player who ate the ingredient
     * @param isNewEffect true if revealEffect() returned true (first discovery)
     */
    public static void onIngredientEaten(ServerPlayer player, boolean isNewEffect) {
        if (!isNewEffect) return; // no XP for re-eating known ingredients
        addXp(player, EAT_DISCOVERY_XP);
    }

    // ── Hook 2: Potion brewed ─────────────────────────────────────────────────

    /**
     * Call this from BrewingLogic.brew() after a successful brew.
     *
     * @param player         the brewing player
     * @param effects        the effects that appeared in the result
     * @param newEffectCount how many effect slots were newly discovered this brew
     *                       (count of revealEffect() calls that returned true)
     * @param isNewRecipe    true if knowledge.learnPotion() returned true
     */
    public static void onPotionBrewed(ServerPlayer player,
                                      List<AlchemyEffectInstance> effects,
                                      int newEffectCount,
                                      boolean isNewRecipe) {
        int totalXp = 0;

        // Base XP: sum of each effect's individual value
        for (AlchemyEffectInstance inst : effects) {
            totalXp += inst.effect().getXpValue();
        }

        // Bonus for newly discovered effect slots
        totalXp += newEffectCount * BREW_DISCOVERY_XP;

        // Bonus for first-time recipe
        if (isNewRecipe) {
            totalXp += RECIPE_DISCOVERY_XP;
        }

        if (totalXp > 0) {
            addXp(player, totalXp);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static void addXp(ServerPlayer player, int amount) {
        SkillsComponents.get(player).addSkillXp(Skill.ALCHEMY, amount);
    }

    private AlchemySkillEvents() {}
}