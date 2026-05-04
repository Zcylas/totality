package zcylas.totality.networking.alchemy;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.alchemy.AlchemyIngredient;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.alchemy.*;
import zcylas.totality.api.alchemy.BrewingLogic;
import zcylas.totality.api.potions.EffectEntry;
import zcylas.totality.api.potions.PotionData;
import zcylas.totality.api.potions.PotionDataComponent;
import zcylas.totality.item.potion.AlchemyPotionItem;
import zcylas.totality.networking.alchemy.BrewResultPayload;

import java.util.ArrayList;
import java.util.List;

public final class BrewServerHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                BrewPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handleBrew(player, payload));
                }
        );
    }

    private static void handleBrew(ServerPlayer player, BrewPayload payload) {
        List<Identifier> ids = payload.ingredientIds();

        if (ids.size() < 2 || ids.size() > 3) {
            player.sendSystemMessage(
                    Component.literal("Invalid ingredient count.")
                            .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        // Resolve item stacks from player inventory
        List<ItemStack> resolvedStacks = new ArrayList<>();
        List<ItemStack> inventoryMatches = new ArrayList<>();

        for (Identifier id : ids) {
            Item item = BuiltInRegistries.ITEM.get(id)
                    .map(net.minecraft.core.Holder.Reference::value)
                    .orElse(null);
            if (item == null || !(item instanceof AlchemyIngredient)) {
                player.sendSystemMessage(
                        Component.literal("Unknown ingredient: " + id)
                                .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            // Find one of this item in the player's inventory
            ItemStack found = ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getItem() == item
                        && !inventoryMatches.contains(stack)) {
                    found = stack;
                    inventoryMatches.add(stack);
                    break;
                }
            }

            if (found.isEmpty()) {
                player.sendSystemMessage(
                        Component.literal("You don't have that ingredient.")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }
            resolvedStacks.add(found);
        }

        // Run brewing logic server-side (also handles discovery)
        BrewingLogic.BrewResult result = BrewingLogic.brew(resolvedStacks, player);

        // Always consume ingredients — even on failed brews, like Skyrim
        for (ItemStack stack : inventoryMatches) {
            stack.shrink(1);
        }

        if (!result.isSuccess()) {
            // No shared effects — send failure popup to client
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new BrewResultPayload("", java.util.List.of(
                            new BrewResultPayload.DiscoveredEffect("FAILED", ""))));
            return;
        }

        AlchemyKnowledgeComponent knowledge = zcylas.totality.api.alchemy.AlchemyComponents.KNOWLEDGE.get(
                (zcylas.totality.api.component.ComponentProvider) player
        );
        List<AlchemyEffectInstance> allEffects = ((BrewingLogic.BrewResult.Success) result).effects();

        // Determine potion name using same priority as client
        String potionName = buildPotionName(allEffects);
        boolean alreadyKnown = knowledge.knowsPotion(allEffects);

        // Record full signature + individual effect signatures
        knowledge.learnPotion(allEffects);
        for (AlchemyEffectInstance inst : allEffects) {
            knowledge.learnPotion(java.util.List.of(inst));
        }
        knowledge.sync();

        // Build discovered effects list for popup (only newly revealed effects)
        List<BrewResultPayload.DiscoveredEffect> discovered = new ArrayList<>();
        if (!alreadyKnown) {
            for (AlchemyEffectInstance inst : allEffects) {
                // Find which ingredients contributed this effect
                for (ItemStack stack : resolvedStacks) {
                    if (stack.getItem() instanceof AlchemyIngredient ai) {
                        for (AlchemyEffectInstance aiInst : ai.getAlchemyEffects()) {
                            if (aiInst.effect() == inst.effect()) {
                                String ingName = toDisplayName(ai.getIngredientId().getPath());
                                discovered.add(new BrewResultPayload.DiscoveredEffect(
                                        inst.effect().getDisplayName(), ingName));
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Send result to client — empty discovered list = just show toast
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new BrewResultPayload(potionName, discovered));

        // Build and give the potion item
        boolean isPoison = allEffects.stream().anyMatch(e -> e.effect().isHarmful())
                && allEffects.stream().noneMatch(e -> e.effect().isBeneficial());

        // Build EffectEntry list from brewed effects using base magnitude/duration
        List<EffectEntry> entries = new ArrayList<>();
        for (AlchemyEffectInstance inst : allEffects) {
            entries.add(EffectEntry.of(
                    inst.effect(),
                    inst.effect().getBaseMagnitude(),
                    inst.effect().getBaseDurationTicks()
            ));
        }

        // Determine color from primary effect type
        int color = isPoison ? PotionData.COLOR_PURPLE : getPotionColor(allEffects);

        PotionData potionData = PotionData.of(potionName, color, entries, isPoison);

        // Create stack from the registered BREWED_POTION base item
        // and store PotionData as a component so it survives save/load
        ItemStack potionStack = new ItemStack(zcylas.totality.init.items.PotionItems.BREWED_POTION);
        potionStack.set(zcylas.totality.api.potions.PotionDataComponent.POTION_DATA, potionData);

        // Give to player
        if (!player.getInventory().add(potionStack)) {
            player.drop(potionStack, false);
        }
    }

    private static int getPotionColor(List<AlchemyEffectInstance> effects) {
        // Use the primary effect (highest magnitude * duration) to determine color
        AlchemyEffectInstance primary = effects.stream()
                .max(java.util.Comparator.comparingDouble(e ->
                        (double) e.effect().getBaseMagnitude() *
                                Math.max(1, e.effect().getBaseDurationTicks())))
                .orElse(effects.get(0));

        String effectId = primary.effect().getId().getPath();
        if (effectId.contains("health")) return PotionData.COLOR_RED;
        if (effectId.contains("mana"))   return PotionData.COLOR_BLUE;
        if (effectId.contains("stamina")) return PotionData.COLOR_GREEN;
        if (effectId.contains("water") || effectId.contains("invisible")
                || effectId.contains("waterbreathing")) return PotionData.COLOR_WHITE;
        // Default — gold for unusual/mixed effects
        return PotionData.COLOR_GOLD;
    }

    private static String buildPotionName(List<AlchemyEffectInstance> effects) {
        if (effects.isEmpty()) return "Potion of Unknown Effect";
        boolean anyHarmful = effects.stream().anyMatch(e -> e.effect().isHarmful());
        boolean anyBeneficial = effects.stream().anyMatch(e -> e.effect().isBeneficial());
        String prefix = (anyHarmful && !anyBeneficial) ? "Poison of " : "Potion of ";
        AlchemyEffectInstance primary = effects.stream()
                .max(java.util.Comparator.comparingDouble(e ->
                        (double) e.effect().getBaseMagnitude() *
                                Math.max(1, e.effect().getBaseDurationTicks())))
                .orElse(effects.get(0));
        return prefix + primary.effect().getDisplayName();
    }

    private static String toDisplayName(String path) {
        return java.util.Arrays.stream(path.split("_"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private BrewServerHandler() {}
}