package zcylas.totality.client.tooltip;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import zcylas.totality.Totality;

/**
 * Which tooltip path a hovered stack takes — the single routing authority behind the renderer entry point
 * ({@code AbstractContainerScreenMixin}), {@link TotalityTooltipRenderer#isEligible} and tooltip wheel scrolling
 * ({@link TotalityTooltipScrollHandler}). Universal Tooltip V2 covers the {@code minecraft} and {@code totality}
 * item namespaces only; within them, having no specialized semantic content does not disqualify an item. Everything
 * else keeps its original path:
 * <ul>
 *   <li>{@link #EXTERNAL_ITEM}: a third-party mod's item keeps its own vanilla/mod-provided tooltip — Totality is
 *       self-contained and does not restyle, classify or rate other mods' content.</li>
 *   <li>{@link #VANILLA_HIDDEN}: the stack's {@link TooltipDisplay} hides its tooltip entirely; vanilla then shows
 *       nothing, and V2 must not resurrect it.</li>
 *   <li>{@link #VANILLA_TOOLTIP_COMPONENT}: the stack supplies a structured tooltip image (bundle contents and any
 *       other {@code TooltipComponent}). Those are functional — the bundle's image is interactive (slot selection,
 *       wheel scrolling) — and the V2 panel cannot yet host them, so they are not replaced by a V2 panel that would
 *       lose them.</li>
 * </ul>
 * Depends only on the stack — never on SHIFT/CTRL/ALT, player or disclosure state — so modifiers change what a
 * tooltip discloses, never which renderer draws it.
 */
public enum TooltipRouting {
    TOTALITY,
    EXTERNAL_ITEM,
    VANILLA_TOOLTIP_COMPONENT,
    VANILLA_HIDDEN,
    NONE;

    public static TooltipRouting of(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        if (!isUniversalNamespace(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) return EXTERNAL_ITEM;
        TooltipDisplay display = stack.get(DataComponents.TOOLTIP_DISPLAY);
        if (display != null && display.hideTooltip()) return VANILLA_HIDDEN;
        if (stack.getTooltipImage().isPresent()) return VANILLA_TOOLTIP_COMPONENT;
        return TOTALITY;
    }

    /** The item namespaces Universal Tooltip V2 presents: Minecraft's and Totality's own. */
    static boolean isUniversalNamespace(String namespace) {
        return "minecraft".equals(namespace) || Totality.MOD_ID.equals(namespace);
    }
}
