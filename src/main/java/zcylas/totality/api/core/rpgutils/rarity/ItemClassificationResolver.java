package zcylas.totality.api.core.rpgutils.rarity;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

import java.util.List;
import java.util.Map;

/**
 * Presentation-side classification resolution (Tooltip V2 Pass 1 hybrid decision), in priority order:
 * <ol>
 *   <li>the stack's explicitly authored classification ({@link ItemComponents#classificationEntriesOf}) —
 *       always wins, including per-stack overrides;</li>
 *   <li>an exact item-specific vanilla mapping ({@link #VANILLA});</li>
 *   <li>reliable inference from vanilla/convention tags, data components and item type — never from
 *       registry-name substrings;</li>
 *   <li>otherwise nothing: an unknown item shows no classification rather than a placeholder.</li>
 * </ol>
 * Read-only and presentation-only: it never writes components and never feeds gameplay. Gameplay code that
 * asks "is this authored as X" keeps using {@link ItemComponents#classificationsOf}.
 */
public final class ItemClassificationResolver {

    /** Exact vanilla items no tag describes precisely. */
    static final Map<Item, Classification> VANILLA = Map.of(
            Items.BOW, Classification.of(ItemType.WEAPON, ClassificationTypes.BOW),
            Items.CROSSBOW, Classification.of(ItemType.WEAPON, ClassificationTypes.CROSSBOW),
            Items.TRIDENT, Classification.of(ItemType.WEAPON, ClassificationTypes.TRIDENT),
            Items.MACE, Classification.of(ItemType.WEAPON, ClassificationTypes.MACE),
            Items.SHEARS, Classification.of(ItemType.TOOL),
            Items.FISHING_ROD, Classification.of(ItemType.TOOL),
            Items.BRUSH, Classification.of(ItemType.TOOL));

    /** Tag-based inference, first match wins; tag membership is the item's own declared identity. */
    private record TagRule(TagKey<Item> tag, ItemType category, Identifier type) {}

    private static final List<TagRule> TAG_RULES = List.of(
            new TagRule(ItemTags.SWORDS, ItemType.WEAPON, ClassificationTypes.SWORD),
            new TagRule(ItemTags.SPEARS, ItemType.WEAPON, ClassificationTypes.SPEAR),
            new TagRule(ConventionalItemTags.BOW_TOOLS, ItemType.WEAPON, ClassificationTypes.BOW),
            new TagRule(ConventionalItemTags.CROSSBOW_TOOLS, ItemType.WEAPON, ClassificationTypes.CROSSBOW),
            new TagRule(ConventionalItemTags.MACE_TOOLS, ItemType.WEAPON, ClassificationTypes.MACE),
            new TagRule(ItemTags.PICKAXES, ItemType.TOOL, ClassificationTypes.PICKAXE),
            new TagRule(ItemTags.AXES, ItemType.TOOL, ClassificationTypes.AXE),
            new TagRule(ItemTags.SHOVELS, ItemType.TOOL, ClassificationTypes.SHOVEL),
            new TagRule(ItemTags.HOES, ItemType.TOOL, ClassificationTypes.HOE),
            new TagRule(ItemTags.HEAD_ARMOR, ItemType.ARMOR, ClassificationTypes.HELMET),
            new TagRule(ItemTags.CHEST_ARMOR, ItemType.ARMOR, ClassificationTypes.CHESTPLATE),
            new TagRule(ItemTags.LEG_ARMOR, ItemType.ARMOR, ClassificationTypes.LEGGINGS),
            new TagRule(ItemTags.FOOT_ARMOR, ItemType.ARMOR, ClassificationTypes.BOOTS));

    private static final List<TagRule> MATERIAL_RULES = List.of(
            new TagRule(ConventionalItemTags.GEMS, ItemType.MATERIAL, ClassificationTypes.GEM),
            new TagRule(ConventionalItemTags.INGOTS, ItemType.MATERIAL, ClassificationTypes.INGOT));

    public static List<Classification> resolve(ItemStack stack) {
        if (stack.isEmpty()) return List.of();

        List<Classification> authored = ItemComponents.classificationEntriesOf(stack);
        if (!authored.isEmpty()) return authored;

        Classification exact = VANILLA.get(stack.getItem());
        if (exact != null) return List.of(exact);

        for (TagRule rule : TAG_RULES) {
            if (stack.is(rule.tag())) return List.of(Classification.of(rule.category(), rule.type()));
        }
        if (stack.getItem() instanceof PotionItem) return List.of(Classification.of(ItemType.POTION));
        if (stack.has(DataComponents.FOOD)) return List.of(Classification.of(ItemType.FOOD));
        if (isPlaceableBlock(stack.getItem())) return List.of(Classification.of(ItemType.BLOCK));
        for (TagRule rule : MATERIAL_RULES) {
            if (stack.is(rule.tag())) return List.of(Classification.of(rule.category(), rule.type()));
        }
        return List.of();
    }

    /**
     * A BlockItem that is named as its block ({@code block.*} description id). Seeds, redstone dust, string and
     * similar place a block too but are named and used as items ({@code item.*}), so they are not called BLOCK.
     */
    static boolean isPlaceableBlock(Item item) {
        return item instanceof BlockItem && item.getDescriptionId().startsWith("block.");
    }

    private ItemClassificationResolver() {}
}
