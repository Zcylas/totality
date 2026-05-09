package zcylas.totality.api.rpg.skills.alchemy;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager;

import java.util.*;

/**
 * Player component that tracks which alchemy effect slots have been discovered
 * for each ingredient.
 *
 * Storage: Map<ingredientId, Set<slotIndex (0-3)>>
 *
 * Discovery happens two ways:
 *   1. Eating an ingredient - always reveals slot 0
 *   2. Successful brew - reveals only the slots whose effects contributed to the result
 *
 * Serialization: flat strings compatible with ValueInput/ValueOutput.
 *   "ak_keys"       -> pipe-separated list of all ingredient IDs
 *   "ak_<id>"       -> comma-separated revealed slot indices for that ingredient
 *   (colons in IDs are replaced with underscores to produce valid NBT keys)
 */
public class AlchemyKnowledgeComponent implements SyncedComponent, CopyableComponent<AlchemyKnowledgeComponent> {

    private final Map<Identifier, Set<Integer>> knownEffects = new HashMap<>();
    private final Set<String> knownPotions = new HashSet<>();
    private final ServerPlayer player;

    public AlchemyKnowledgeComponent(ServerPlayer player) {
        this.player = player;
    }

    // -------------------------------------------------------------------------
    // Discovery API
    // -------------------------------------------------------------------------

    /**
     * Reveals a specific effect slot for an ingredient.
     * Returns true if this was a new discovery.
     */
    public boolean revealEffect(Identifier ingredientId, int slot) {
        Set<Integer> revealed = knownEffects.computeIfAbsent(ingredientId, k -> new HashSet<>());
        return revealed.add(slot);
    }

    public boolean isRevealed(Identifier ingredientId, int slot) {
        Set<Integer> revealed = knownEffects.get(ingredientId);
        return revealed != null && revealed.contains(slot);
    }

    public Set<Integer> getRevealedSlots(Identifier ingredientId) {
        return knownEffects.getOrDefault(ingredientId, Set.of());
    }

    /**
     * Records that the player has brewed a potion with these effects.
     * The signature is the sorted effect IDs joined by commas.
     * Returns true if this was the first time.
     */
    public boolean learnPotion(List<AlchemyEffectInstance> effects) {
        String signature = buildSignature(effects);
        return knownPotions.add(signature);
    }

    public boolean knowsPotion(List<AlchemyEffectInstance> effects) {
        return knownPotions.contains(buildSignature(effects));
    }

    private static String buildSignature(List<AlchemyEffectInstance> effects) {
        return effects.stream()
                .map(e -> e.effect().getId().toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    // -------------------------------------------------------------------------
    // Sync
    // -------------------------------------------------------------------------

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            AlchemyComponents.KNOWLEDGE.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(knownEffects.size());
        for (var entry : knownEffects.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeCollection(entry.getValue(), (b, slot) -> b.writeInt(slot));
        }
        buf.writeInt(knownPotions.size());
        for (String sig : knownPotions) {
            buf.writeUtf(sig);
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        knownEffects.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            Identifier id = buf.readIdentifier();
            List<Integer> slots = buf.readList(b -> b.readInt());
            knownEffects.put(id, new HashSet<>(slots));
        }
        knownPotions.clear();
        int potionCount = buf.readInt();
        for (int i = 0; i < potionCount; i++) {
            knownPotions.add(buf.readUtf());
        }
        ClientAlchemyKnowledgeManager.apply(knownEffects, knownPotions);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /** "totality:wheat" -> "ak_totality_wheat" */
    private static String nbtKey(Identifier id) {
        return "ak_" + id.toString().replace(':', '_');
    }

    @Override
    public void writeData(ValueOutput output) {
        String keyList = knownEffects.keySet().stream()
                .map(Identifier::toString)
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        output.putString("ak_keys", keyList);

        for (var entry : knownEffects.entrySet()) {
            String slots = entry.getValue().stream()
                    .sorted()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            output.putString(nbtKey(entry.getKey()), slots);
        }
        // Persist known potions as pipe-separated signatures
        output.putString("ak_potions", String.join("|", knownPotions));
    }

    @Override
    public void readData(ValueInput input) {
        knownEffects.clear();

        String keyList = input.getStringOr("ak_keys", "");
        if (keyList.isEmpty()) return;

        for (String idStr : keyList.split("\\|")) {
            if (idStr.isBlank()) continue;
            Identifier id = Identifier.parse(idStr);
            String slotsStr = input.getStringOr(nbtKey(id), "");

            Set<Integer> slots = new HashSet<>();
            if (!slotsStr.isEmpty()) {
                for (String s : slotsStr.split(",")) {
                    try {
                        int slot = Integer.parseInt(s.trim());
                        if (slot >= 0 && slot <= 3) slots.add(slot);
                    } catch (NumberFormatException ignored) {}
                }
            }
            knownEffects.put(id, slots);
        }
        // Read known potions
        knownPotions.clear();
        String potionData = input.getStringOr("ak_potions", "");
        if (!potionData.isEmpty()) {
            for (String sig : potionData.split("\\|")) {
                if (!sig.isBlank()) knownPotions.add(sig);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Respawn copy
    // -------------------------------------------------------------------------

    @Override
    public void copyFrom(AlchemyKnowledgeComponent other, HolderLookup.Provider registries) {
        this.knownEffects.clear();
        for (var entry : other.knownEffects.entrySet()) {
            this.knownEffects.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        this.knownPotions.clear();
        this.knownPotions.addAll(other.knownPotions);
    }
}