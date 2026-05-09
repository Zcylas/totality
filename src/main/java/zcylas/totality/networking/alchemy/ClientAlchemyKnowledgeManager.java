package zcylas.totality.networking.alchemy;

import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Client-side cache of the player's alchemy knowledge.
 * Populated when a sync packet is received from the server.
 */
public final class ClientAlchemyKnowledgeManager {

    private static final Map<Identifier, Set<Integer>> knownEffects = new HashMap<>();
    private static final Set<String> knownPotions = new HashSet<>();

    private ClientAlchemyKnowledgeManager() {}

    public static void apply(Map<Identifier, Set<Integer>> effects, Set<String> potions) {
        knownEffects.clear();
        for (var entry : effects.entrySet()) {
            knownEffects.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        knownPotions.clear();
        knownPotions.addAll(potions);
    }

    /** Legacy overload for backward compatibility */
    public static void apply(Map<Identifier, Set<Integer>> effects) {
        apply(effects, knownPotions);
    }

    public static boolean isRevealed(Identifier ingredientId, int slot) {
        Set<Integer> slots = knownEffects.get(ingredientId);
        return slots != null && slots.contains(slot);
    }

    public static Set<Integer> getRevealedSlots(Identifier ingredientId) {
        return knownEffects.getOrDefault(ingredientId, Set.of());
    }

    public static boolean knowsPotion(String signature) {
        return knownPotions.contains(signature);
    }

    /** Build a potion signature from effect IDs — sorted for consistency */
    public static String buildSignature(List<zcylas.totality.api.rpg.skills.alchemy.AlchemyEffectInstance> effects) {
        return effects.stream()
                .map(e -> e.effect().getId().toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }
}