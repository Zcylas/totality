package zcylas.totality.api.item;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.client.tooltip.TooltipExtension;

import java.util.List;
import java.util.UUID;

/**
 * Shared interface for all Totality items (weapons, armor, trinkets, etc.).
 * Implements {@link TooltipExtension} so the attunement status line is
 * automatically added to every Totality item's custom tooltip.
 */
public interface TotalityItem extends TooltipExtension {

    // ── Attunement ────────────────────────────────────────────────────────────

    default boolean requiresAttunement() { return true; }

    default boolean isAttuned(ItemStack stack, Player player) {
        UUID attuned = stack.get(TotalityItemComponents.ATTUNED_TO);
        return attuned != null && attuned.equals(player.getUUID());
    }

    @Nullable
    default UUID getAttunedTo(ItemStack stack) {
        return stack.get(TotalityItemComponents.ATTUNED_TO);
    }

    default void attuneTo(ItemStack stack, Player player) {
        stack.set(TotalityItemComponents.ATTUNED_TO, player.getUUID());
    }

    default void unattune(ItemStack stack) {
        stack.remove(TotalityItemComponents.ATTUNED_TO);
    }

    // ── Identification ────────────────────────────────────────────────────────

    default boolean startsUnidentified() { return true; }

    default IdentificationStatus getIdentificationStatus(ItemStack stack) {
        return stack.getOrDefault(TotalityItemComponents.IDENTIFICATION_STATUS,
                IdentificationStatus.UNIDENTIFIED);
    }

    default void setIdentificationStatus(ItemStack stack, IdentificationStatus status) {
        stack.set(TotalityItemComponents.IDENTIFICATION_STATUS, status);
    }

    default boolean isIdentified(ItemStack stack) {
        return getIdentificationStatus(stack).isFullyIdentified();
    }

    // ── TooltipExtension ──────────────────────────────────────────────────────

    /**
     * Adds the ATTUNEMENT status line to the Totality tooltip.
     * Only called from the client-side tooltip renderer — safe to use
     * {@link net.minecraft.client.Minecraft#getInstance()} here.
     */
    @Override
    default void addTooltipLines(ItemStack stack, Font font, List<Component> lines) {
        if (!requiresAttunement()) {
            lines.add(Component.literal("ATTUNEMENT : ")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                    .append(Component.literal("FREE")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        UUID attunedTo = getAttunedTo(stack);
        boolean attuned = attunedTo != null
                && mc.player != null
                && attunedTo.equals(mc.player.getUUID());

        if (attuned) {
            lines.add(Component.literal("ATTUNEMENT : ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(Component.literal("ATTUNED")
                            .withStyle(net.minecraft.ChatFormatting.GOLD)));
        } else {
            lines.add(Component.literal("ATTUNEMENT : ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(Component.literal("NOT ATTUNED")
                            .withStyle(net.minecraft.ChatFormatting.RED)));
        }
    }
}