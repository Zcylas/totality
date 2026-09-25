package zcylas.totality.client.tooltip.footer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.rpgutils.WeightComponent;
import zcylas.totality.api.core.rpgutils.rarity.ContentOriginComponent;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.economy.value.ItemValueRegistry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The tooltip footer: {@code Weight} on the left, the item's content {@code Origin} centred, its {@code Price} in
 * Credits on the right. Every field comes from an existing authoritative source and is simply omitted when unknown —
 * nothing is invented:
 * <ul>
 *   <li>Weight — the item's {@link WeightComponent} (no longer also a body row, so it is never shown twice), drawn
 *       as the existing Weight icon followed by the value;</li>
 *   <li>Origin — the item's authored {@link ContentOriginComponent}, localized; never inferred from the namespace;</li>
 *   <li>Price — the item's base value from {@link ItemValueRegistry}, the same value retail pricing charges. A real
 *       authored value of 0 shows as {@code 0 ₵}; an item with no value shows no price. The registry is loaded by the
 *       server from datapacks and is not synced, so the price shows in single-player (integrated server) and is
 *       omitted on a dedicated server until item values are synced to clients.</li>
 * </ul>
 */
public final class TooltipFooter {

    public static final String CREDITS_SYMBOL = "₵";
    /** Minimum clear space between neighbouring footer fields. */
    static final int FIELD_GAP = 6;

    /** The footer's texts ({@code weight} is the bare value, drawn after the Weight icon); {@code null} = unknown, omitted. */
    public record Info(@Nullable String weight, @Nullable String origin, @Nullable String price) {
        public boolean isEmpty() {
            return weight == null && origin == null && price == null;
        }
    }

    public static Info resolve(ItemStack stack) {
        return new Info(weight(stack), origin(stack), price(stack));
    }

    static @Nullable String weight(ItemStack stack) {
        var type = ItemComponents.getWeight();
        WeightComponent weight = type == null ? null : stack.get(type);
        return weight == null ? null : formatWeight(weight.weight());
    }

    static @Nullable String origin(ItemStack stack) {
        var type = ItemComponents.getContentOrigin();
        ContentOriginComponent origin = type == null ? null : stack.get(type);
        if (origin == null) return null;
        String fallback = origin.origin().getPath().replace('_', ' ');
        return Component.translatableWithFallback(origin.translationKey(), fallback).getString();
    }

    static @Nullable String price(ItemStack stack) {
        Optional<Long> value = ItemValueRegistry.INSTANCE.resolveBaseValue(stack);
        return value.map(TooltipFooter::formatPrice).orElse(null);
    }

    /**
     * The weight value with insignificant trailing zeroes trimmed: {@code 1.0 -> "1"}, {@code 1.5 -> "1.5"}. Uses the
     * float's shortest exact decimal form, so meaningful precision ({@code 0.25 -> "0.25"}) is never rounded away.
     */
    public static String formatWeight(float weight) {
        return new BigDecimal(Float.toString(weight)).stripTrailingZeros().toPlainString();
    }

    /** {@code "450 ₵"}. */
    public static String formatPrice(long credits) {
        return credits + " " + CREDITS_SYMBOL;
    }

    /** Where one field is drawn: x offset within the footer's inner width, and its row. */
    public record Placement(Slot slot, int x, int row) {}

    public enum Slot { LEFT, CENTER, RIGHT }

    /**
     * Places the fields across {@code innerW}: left-aligned, truly centred (independent of whether the other two
     * exist), right-aligned. If the centred origin would collide with a neighbour, it moves to its own row above
     * the weight/price row; if weight and price still do not fit side by side, price takes a row of its own. Absent
     * fields (width &lt; 0) are skipped. Pure — unit-tested.
     */
    public static List<Placement> layout(int innerW, int leftW, int centerW, int rightW) {
        List<Placement> out = new ArrayList<>();
        boolean left = leftW >= 0, center = centerW >= 0, right = rightW >= 0;
        int centerX = (innerW - centerW) / 2;
        boolean centerFits = !center
                || ((!left || leftW + FIELD_GAP <= centerX) && (!right || centerX + centerW + FIELD_GAP <= innerW - rightW));
        int row = 0;
        if (center && !centerFits) {
            out.add(new Placement(Slot.CENTER, Math.max(0, centerX), row++));
        }
        boolean sideBySide = !left || !right || leftW + FIELD_GAP + rightW <= innerW;
        if (left) out.add(new Placement(Slot.LEFT, 0, row));
        if (center && centerFits) out.add(new Placement(Slot.CENTER, centerX, row));
        if (right) out.add(new Placement(Slot.RIGHT, Math.max(0, innerW - rightW), sideBySide ? row : row + 1));
        return out;
    }

    /** Number of rows a layout uses (0 when every field is absent). */
    public static int rows(List<Placement> placements) {
        int max = -1;
        for (Placement p : placements) max = Math.max(max, p.row());
        return max + 1;
    }

    private TooltipFooter() {}
}
