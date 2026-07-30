package zcylas.totality.api.core.rpgutils.rarity;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.WeightComponent;

import java.util.List;


public class ItemComponents {

    public static final Identifier RARITY_ID =
            Identifier.fromNamespaceAndPath("totality", "rarity");
    public static final Identifier ITEM_TYPE_ID =
            Identifier.fromNamespaceAndPath("totality", "item_type");
    public static final Identifier LORE_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "lore");
    public static final Identifier WEIGHT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "weight");
    public static final Identifier CLASSIFICATIONS_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "classifications");
    public static final Identifier TOOLTIP_PROFILE_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "tooltip_profile");

    public static DataComponentType<WeightComponent> WEIGHT;
    public static DataComponentType<RarityComponent> RARITY;
    public static DataComponentType<ItemTypeComponent> ITEM_TYPE;
    public static DataComponentType<LoreComponent> LORE;
    public static DataComponentType<ClassificationsComponent> CLASSIFICATIONS;
    public static DataComponentType<TooltipProfileComponent> TOOLTIP_PROFILE;

    public static void register() {
        RARITY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                RARITY_ID,
                DataComponentType.<RarityComponent>builder()
                        .persistent(RarityComponent.CODEC)
                        .networkSynchronized(RarityComponent.STREAM_CODEC)
                        .build());

        ITEM_TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                ITEM_TYPE_ID,
                DataComponentType.<ItemTypeComponent>builder()
                        .persistent(ItemTypeComponent.CODEC)
                        .networkSynchronized(ItemTypeComponent.STREAM_CODEC)
                        .build());
// In register():
        LORE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                LORE_ID,
                DataComponentType.<LoreComponent>builder()
                        .persistent(LoreComponent.CODEC)
                        .networkSynchronized(LoreComponent.STREAM_CODEC)
                        .build());
        WEIGHT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                WEIGHT_ID,
                DataComponentType.<WeightComponent>builder()
                        .persistent(WeightComponent.CODEC)
                        .networkSynchronized(WeightComponent.STREAM_CODEC)
                        .build());
        CLASSIFICATIONS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                CLASSIFICATIONS_ID,
                DataComponentType.<ClassificationsComponent>builder()
                        .persistent(ClassificationsComponent.CODEC)
                        .networkSynchronized(ClassificationsComponent.STREAM_CODEC)
                        .build());
        TOOLTIP_PROFILE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                TOOLTIP_PROFILE_ID,
                DataComponentType.<TooltipProfileComponent>builder()
                        .persistent(TooltipProfileComponent.CODEC)
                        .networkSynchronized(TooltipProfileComponent.STREAM_CODEC)
                        .build());
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<RarityComponent> getRarity() {
        var result = BuiltInRegistries.DATA_COMPONENT_TYPE.get(RARITY_ID);
        return (DataComponentType<RarityComponent>) result
                .map(Holder.Reference::value)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<ItemTypeComponent> getItemType() {
        return (DataComponentType<ItemTypeComponent>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(ITEM_TYPE_ID)
                .map(Holder.Reference::value)
                .orElse(null);
    }
    @SuppressWarnings("unchecked")
    public static DataComponentType<LoreComponent> getLore() {
        return (DataComponentType<LoreComponent>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(LORE_ID)
                .map(Holder.Reference::value)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<WeightComponent> getWeight() {
        return (DataComponentType<WeightComponent>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(WEIGHT_ID)
                .map(Holder.Reference::value)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<ClassificationsComponent> getClassifications() {
        return (DataComponentType<ClassificationsComponent>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(CLASSIFICATIONS_ID)
                .map(Holder.Reference::value)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<TooltipProfileComponent> getTooltipProfile() {
        return (DataComponentType<TooltipProfileComponent>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(TOOLTIP_PROFILE_ID)
                .map(Holder.Reference::value)
                .orElse(null);
    }

    /**
     * Reads the item's ordered classifications, preferring the new {@link ClassificationsComponent}
     * and falling back to the legacy singleton {@link ItemTypeComponent} (wrapped as a one-element
     * list) so pre-existing stacks that only ever set the old component keep showing their badge.
     * Returns an empty list if neither component is present — callers must not invent a
     * classification for an item that was never authored with one.
     */
    public static List<ItemType> classificationsOf(ItemStack stack) {
        var classificationsType = getClassifications();
        if (classificationsType != null && stack.has(classificationsType)) {
            return stack.get(classificationsType).ordered();
        }
        var legacyType = getItemType();
        if (legacyType != null && stack.has(legacyType)) {
            ItemTypeComponent legacy = stack.get(legacyType);
            if (legacy != null) return List.of(legacy.type());
        }
        return List.of();
    }

    /**
     * Explicit opt-in check for the Totality custom tooltip renderer. Prefers the new
     * {@link TooltipProfileComponent} marker. Also honors a temporary, documented compatibility
     * fallback for items registered before the opt-in system existed: an item that already
     * carries {@link #RARITY} but no explicit profile is still treated as opted-in, so no
     * previously-supported item silently loses its Totality tooltip. New registrations should
     * set {@link #TOOLTIP_PROFILE} explicitly rather than relying on this fallback — see
     * Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_IMPLEMENTATION_REPORT.md for the full
     * migration inventory of items still relying on it.
     */
    public static boolean hasTooltipPresentation(ItemStack stack) {
        var profileType = getTooltipProfile();
        if (profileType != null && stack.has(profileType)) return true;

        var rarityType = getRarity();
        return rarityType != null && stack.has(rarityType);
    }

    private ItemComponents() {}
}