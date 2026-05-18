package zcylas.totality.api.core.rpgutils.rarity;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.WeightComponent;


public class ItemComponents {

    public static final Identifier RARITY_ID =
            Identifier.fromNamespaceAndPath("totality", "rarity");
    public static final Identifier ITEM_TYPE_ID =
            Identifier.fromNamespaceAndPath("totality", "item_type");
    public static final Identifier LORE_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "lore");
    public static final Identifier WEIGHT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "weight");

    public static DataComponentType<WeightComponent> WEIGHT;
    public static DataComponentType<RarityComponent> RARITY;
    public static DataComponentType<ItemTypeComponent> ITEM_TYPE;
    public static DataComponentType<LoreComponent> LORE;

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

    private ItemComponents() {}
}