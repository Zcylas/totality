package zcylas.totality.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Everything a {@link zcylas.totality.client.tooltip.contributor.TooltipContributor} may read to
 * decide what to contribute. Deliberately narrow — no {@code Font}/{@code GuiGraphicsExtractor}
 * access here; measurement and drawing belong to the renderer, not contributors. Gameplay
 * capability interfaces such as {@code UEItem}/{@code TotalityWeaponItem} never need to
 * reference this class — only client-side contributors do, keeping server-safe gameplay code
 * free of any dependency on client tooltip classes.
 */
public record TooltipContext(
        ItemStack stack,
        TooltipTarget target,
        TooltipDisclosureLevel disclosure,
        TooltipKnowledgeView knowledge,
        List<Component> originalLines,
        Optional<TooltipComponent> originalComponent,
        @Nullable Player player,
        @Nullable Level level
) {

    public TooltipContext {
        originalLines = List.copyOf(originalLines);
    }

    /** Minimal context for pure unit tests: HOVER_TOOLTIP target, DEFAULT disclosure, identified, no original lines. */
    public static TooltipContext of(ItemStack stack) {
        return of(stack, TooltipDisclosureLevel.DEFAULT, TooltipKnowledgeView.identified());
    }

    public static TooltipContext of(ItemStack stack, TooltipDisclosureLevel disclosure) {
        return of(stack, disclosure, TooltipKnowledgeView.identified());
    }

    public static TooltipContext of(ItemStack stack, TooltipDisclosureLevel disclosure, TooltipKnowledgeView knowledge) {
        return new TooltipContext(stack, TooltipTarget.HOVER_TOOLTIP, disclosure,
                knowledge, List.of(), Optional.empty(), null, null);
    }

    public static TooltipContext hover(ItemStack stack, TooltipDisclosureLevel disclosure,
                                       List<Component> originalLines, Optional<TooltipComponent> originalComponent,
                                       @Nullable Player player, @Nullable Level level) {
        return new TooltipContext(stack, TooltipTarget.HOVER_TOOLTIP, disclosure,
                TooltipKnowledgeView.of(stack), originalLines, originalComponent, player, level);
    }
}
