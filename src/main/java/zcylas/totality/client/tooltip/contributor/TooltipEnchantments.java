package zcylas.totality.client.tooltip.contributor;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The enchantments a stack's tooltip shows, read from Minecraft's own authoritative data — never a Tooltip V2
 * enchantment table. Mirrors exactly what vanilla 26.2 prints ({@code ItemEnchantments#addToTooltip}): the
 * {@code STORED_ENCHANTMENTS} (enchanted books) and {@code ENCHANTMENTS} components, each only when the stack's
 * {@link TooltipDisplay} shows it, ordered by the {@code #minecraft:tooltip_order} enchantment tag first and then the
 * remaining entries. Works for any registered enchantment, vanilla or Totality's own.
 *
 * <p>{@link Entry#vanillaLine} is the exact component vanilla emits for that entry
 * ({@link Enchantment#getFullname}), so the raw line it supersedes can be recognised by component equality — not by
 * rendered text — and dropped from the preserved external lines.
 */
final class TooltipEnchantments {

    /** One enchantment on the stack: its registry holder, actual level, curse flag and vanilla's own line. */
    record Entry(Holder<Enchantment> enchantment, int level, boolean curse, Component vanillaLine) {
        int maxLevel() {
            return enchantment.value().getMaxLevel();
        }
    }

    private static final List<DataComponentType<ItemEnchantments>> TYPES =
            List.of(DataComponents.STORED_ENCHANTMENTS, DataComponents.ENCHANTMENTS);

    static List<Entry> of(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        List<Entry> entries = new ArrayList<>();
        for (DataComponentType<ItemEnchantments> type : TYPES) {
            ItemEnchantments enchantments = stack.get(type);
            if (enchantments == null || enchantments.isEmpty() || !display.shows(type)) continue;
            Optional<HolderSet.Named<Enchantment>> order = registries == null ? Optional.empty()
                    : registries.lookup(Registries.ENCHANTMENT).flatMap(lookup -> lookup.get(EnchantmentTags.TOOLTIP_ORDER));
            if (order.isPresent()) {
                for (Holder<Enchantment> holder : order.get()) {
                    int level = enchantments.getLevel(holder);
                    if (level > 0) entries.add(entry(holder, level));
                }
            }
            for (Object2IntMap.Entry<Holder<Enchantment>> e : enchantments.entrySet()) {
                if (order.isPresent() && order.get().contains(e.getKey())) continue;
                entries.add(entry(e.getKey(), e.getIntValue()));
            }
        }
        return entries;
    }

    private static Entry entry(Holder<Enchantment> holder, int level) {
        return new Entry(holder, level, holder.is(EnchantmentTags.CURSE), Enchantment.getFullname(holder, level));
    }

    /** Localized roman level, exactly the key vanilla uses ({@code enchantment.level.N}); plain digits past its table. */
    static String levelText(int level) {
        return Component.translatableWithFallback("enchantment.level." + level, String.valueOf(level)).getString();
    }

    private TooltipEnchantments() {}
}
