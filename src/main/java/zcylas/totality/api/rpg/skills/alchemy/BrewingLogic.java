package zcylas.totality.api.rpg.skills.alchemy;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Core brewing logic for the Apothecary Table.
 *
 * Rules (Skyrim-faithful):
 *   1. 2–3 ingredients required.
 *   2. An effect appears in the result only if it is shared by 2+ of the selected ingredients.
 *   3. Only effects that actually contributed to the result are revealed on the players knowledge component.
 *   4. If no effects are shared, brewing fails and no discovery occurs.
 */
public final class BrewingLogic {

    private BrewingLogic() {}

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Attempts to brew from the given ingredient stacks.
     * Handles player knowledge updates as a side effect on success.
     *
     * @param stacks  2–3 non-empty ItemStacks of alchemy ingredients
     * @param player  the brewing player (for knowledge updates); may be null for simulation
     * @return a BrewResult describing success or failure
     */
    public static BrewResult brew(List<ItemStack> stacks, ServerPlayer player) {
        if (stacks.size() < 2 || stacks.size() > 3) {
            return BrewResult.failure("Need 2–3 ingredients.");
        }

        List<ResolvedIngredient> resolved = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ResolvedIngredient ri = resolve(stack.getItem());
            if (ri == null) return BrewResult.failure(stack.getItem().toString() + " is not an alchemy ingredient.");
            resolved.add(ri);
        }

        if (resolved.size() < 2) {
            return BrewResult.failure("Need at least 2 alchemy ingredients.");
        }

        List<AlchemyEffectInstance> sharedEffects = findSharedEffects(resolved);

        if (sharedEffects.isEmpty()) {
            return BrewResult.failure("No shared effects — the mixture fizzles.");
        }

        // Track XP context before revealing effects
        int newEffectCount = 0;
        boolean isNewRecipe = false;

        if (player != null) {
            newEffectCount = revealContributingEffectsAndCount(resolved, sharedEffects, player);

            AlchemyKnowledgeComponent knowledge = AlchemyComponents.KNOWLEDGE.get(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
            isNewRecipe = knowledge.learnPotion(sharedEffects);
            if (isNewRecipe) {
                knowledge.sync();
            }

            // Award Alchemy XP
            AlchemySkillEvents.onPotionBrewed(player, sharedEffects, newEffectCount, isNewRecipe);
        }

        return BrewResult.success(sharedEffects);
    }


    // -------------------------------------------------------------------------
    // Simulation (no player — for GUI preview)
    // -------------------------------------------------------------------------

    /**
     * Simulates a brew for the GUI preview without touching player knowledge.
     * Only returns effects the player has already discovered on ALL contributing ingredients.
     * If shared effects exist but none are known, returns success with empty list
     * so the GUI can show "Potion of Unknown Effect" — matching Skyrim behaviour.
     */
    /**
     * Simulates a brew for the GUI preview — client side only.
     * Returns:
     *   - failure if no shared effects exist (fizzle)
     *   - success with empty list if the potion exists but hasn't been brewed before
     *   - success with full effect list if the player has brewed this exact potion before
     *
     * This matches Skyrim: even knowing the ingredients, the result is unknown
     * until you've actually created the potion at least once.
     */
    public static BrewResult simulate(List<ItemStack> stacks) {
        List<ResolvedIngredient> resolved = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ResolvedIngredient ri = resolve(stack.getItem());
            if (ri == null) return BrewResult.failure("Not an alchemy ingredient.");
            resolved.add(ri);
        }
        if (resolved.size() < 2) return BrewResult.failure("Need at least 2 ingredients.");

        List<AlchemyEffectInstance> allShared = findSharedEffects(resolved);
        if (allShared.isEmpty()) return BrewResult.failure("No shared effects.");

        // Check known effects:
        // Show an effect in the preview if the player has brewed a potion containing it before.
        // This means brewing Rock Warbler Egg + Blue Mountain Flower (Restore Health)
        // will also reveal Restore Health in Wheat + Blue Mountain Flower preview.
        List<AlchemyEffectInstance> knownShared = new ArrayList<>();
        for (AlchemyEffectInstance inst : allShared) {
            String singleSig = zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager
                    .buildSignature(List.of(inst));
            if (zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager.knowsPotion(singleSig)) {
                knownShared.add(inst);
            }
        }

        // Also check full signature
        if (knownShared.isEmpty()) {
            String fullSig = zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager
                    .buildSignature(allShared);
            if (zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager.knowsPotion(fullSig)) {
                return BrewResult.success(allShared);
            }
            // Never brewed — unknown potion
            return BrewResult.success(List.of());
        }

        return BrewResult.success(knownShared);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Finds all effects that appear in 2+ of the resolved ingredients.
     * Effect identity is by AlchemyEffect reference (registry identity).
     */
    private static List<AlchemyEffectInstance> findSharedEffects(List<ResolvedIngredient> ingredients) {
        // Count how many ingredients carry each effect
        Map<AlchemyEffect, AlchemyEffectInstance> effectMap = new LinkedHashMap<>();
        Map<AlchemyEffect, Integer> effectCount = new LinkedHashMap<>();

        for (ResolvedIngredient ri : ingredients) {
            // Use a set to avoid double-counting the same effect twice on one ingredient
            Set<AlchemyEffect> seen = new HashSet<>();
            for (AlchemyEffectInstance instance : ri.effects()) {
                AlchemyEffect effect = instance.effect();
                if (seen.add(effect)) {
                    effectCount.merge(effect, 1, Integer::sum);
                    effectMap.putIfAbsent(effect, instance);
                }
            }
        }

        List<AlchemyEffectInstance> shared = new ArrayList<>();
        for (var entry : effectCount.entrySet()) {
            if (entry.getValue() >= 2) {
                shared.add(effectMap.get(entry.getKey()));
            }
        }
        return shared;
    }

    /**
     * For each shared effect, find the ingredients that contributed it and reveal
     * only those specific slots on the player's knowledge component.
     */
    private static int revealContributingEffectsAndCount(
            List<ResolvedIngredient> ingredients,
            List<AlchemyEffectInstance> sharedEffects,
            ServerPlayer player
    ) {
        Set<AlchemyEffect> sharedEffectSet = new HashSet<>();
        for (AlchemyEffectInstance instance : sharedEffects) {
            sharedEffectSet.add(instance.effect());
        }

        AlchemyKnowledgeComponent knowledge = AlchemyComponents.KNOWLEDGE.get(
                (zcylas.totality.api.core.component.ComponentProvider) player
        );

        int newDiscoveries = 0;
        for (ResolvedIngredient ri : ingredients) {
            for (AlchemyEffectInstance instance : ri.effects()) {
                if (sharedEffectSet.contains(instance.effect())) {
                    boolean isNew = knowledge.revealEffect(ri.ingredientId(), instance.slot());
                    if (isNew) newDiscoveries++;
                }
            }
        }

        if (newDiscoveries > 0) {
            knowledge.sync();
        }

        return newDiscoveries;
    }


    /**
     * Resolves an Item to its ingredient data.
     * All ingredients are custom items implementing AlchemyIngredient directly.
     */
    private static ResolvedIngredient resolve(Item item) {
        if (item instanceof AlchemyIngredient ai) {
            return new ResolvedIngredient(ai.getIngredientId(), ai.getAlchemyEffects());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Internal record
    // -------------------------------------------------------------------------

    private record ResolvedIngredient(Identifier ingredientId, List<AlchemyEffectInstance> effects) {}

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    public sealed interface BrewResult permits BrewResult.Success, BrewResult.Failure {

        boolean isSuccess();

        record Success(List<AlchemyEffectInstance> effects) implements BrewResult {
            @Override public boolean isSuccess() { return true; }
        }

        record Failure(String reason) implements BrewResult {
            @Override public boolean isSuccess() { return false; }
        }

        static BrewResult success(List<AlchemyEffectInstance> effects) {
            return new Success(List.copyOf(effects));
        }

        static BrewResult failure(String reason) {
            return new Failure(reason);
        }
    }
}