package zcylas.totality.client.tooltip.presentation;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.rpgutils.rarity.Classification;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Text of the V2 identity block's secondary content (under the item name): the rarity plaque label and
 * the classification lines. Pure formatting over the item's existing semantic data — the UI never owns or
 * invents classification. An item without a rarity gets no plaque; an item without classifications gets
 * no classification line, and nothing is fabricated to fill a missing half of a pair.
 */
public final class TooltipIdentityLines {

    public static final String SEPARATOR = " • ";

    /** Rarity plaque text, e.g. {@code "EPIC"}; empty when the item has no authored rarity. */
    public static String rarityLine(ItemRarity rarity) {
        return rarity == null ? "" : label(rarity.getSerializedName());
    }

    /** Category-only classifications on one line, in authored order with duplicates removed, e.g. {@code "BATTERY • ENERGY"}; empty if none. */
    public static String categoryTypeLine(List<ItemType> classifications) {
        return new LinkedHashSet<>(classifications).stream()
                .map(type -> label(type.getSerializedName()))
                .collect(Collectors.joining(SEPARATOR));
    }

    /**
     * Header classification lines, one centred line each:
     * <ul>
     *   <li>every category/type pair is its own line, {@code "TOOL • AXE"}, in authored order;</li>
     *   <li>category-only entries (every entry of the original flat format) share one line joined exactly as
     *       before, {@code "BATTERY • ENERGY"}, placed where the first of them was authored — so existing data
     *       looks unchanged.</li>
     * </ul>
     * Duplicate entries are shown once.
     *
     * @param typeName resolves a type identifier to its (localized) display name
     */
    public static List<String> classificationLines(List<Classification> entries, Function<Identifier, String> typeName) {
        List<String> lines = new ArrayList<>();
        List<ItemType> categoryOnly = new ArrayList<>();
        int categoryOnlyIndex = -1;
        for (Classification entry : new LinkedHashSet<>(entries)) {
            if (entry.type().isPresent()) {
                lines.add(label(entry.category().getSerializedName()) + SEPARATOR
                        + typeName.apply(entry.type().get()).toUpperCase(Locale.ROOT));
            } else {
                if (categoryOnlyIndex < 0) categoryOnlyIndex = lines.size();
                categoryOnly.add(entry.category());
            }
        }
        if (categoryOnlyIndex >= 0) lines.add(categoryOnlyIndex, categoryTypeLine(categoryOnly));
        return lines;
    }

    private static String label(String serializedName) {
        return serializedName.replace('_', ' ').toUpperCase();
    }

    private TooltipIdentityLines() {}
}
