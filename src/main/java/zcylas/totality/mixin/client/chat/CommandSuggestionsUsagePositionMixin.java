package zcylas.totality.mixin.client.chat;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import zcylas.totality.client.renderer.hud.TotalityChatLayout;

/**
 * Final player-HUD and vanilla-chat compatibility correction: {@code CommandSuggestions}'s
 * command-usage hint box (shown instead of the suggestion popup when there is no matching
 * suggestion) is positioned at {@code y = screen.height - 27 - 12*i} inside
 * {@code extractUsage(GuiGraphicsExtractor)} (the {@code anchorToBottom} branch) — decompiled and
 * confirmed against MC 26.2's {@code minecraft-merged-deobf-26.2.jar}
 * ({@code net/minecraft/client/gui/components/CommandSuggestions.class}). The literal {@code 27}
 * appears exactly once in this method's bytecode; the separate {@code 12}s in the same method are
 * the per-line height stride and box padding, not the bottom anchor, and are intentionally left
 * untouched (they are computed from the already-shifted base, so they inherit the shift
 * automatically without needing their own adjustment). Uses
 * {@link TotalityChatLayout#usageHintBottomMargin()} — this element's own vanilla spacing above
 * input (27-12=15px) was not reported broken by live testing, so unlike the message stack it
 * keeps its own vanilla base margin, only gaining the shared reservation.
 */
@Mixin(CommandSuggestions.class)
public class CommandSuggestionsUsagePositionMixin {

    @ModifyConstant(
            method = "extractUsage",
            constant = @Constant(intValue = 27)
    )
    private int totality$raiseUsageHint(int original) {
        return TotalityChatLayout.usageHintBottomMargin();
    }
}
