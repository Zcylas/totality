package zcylas.totality.mixin.client.chat;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import zcylas.totality.client.renderer.hud.TotalityChatLayout;

/**
 * Final player-HUD and vanilla-chat compatibility correction: {@code CommandSuggestions}'s
 * bottom-anchored suggestion popup is positioned at {@code y = screen.height - 12} inside
 * {@code showSuggestions(boolean)} (the {@code anchorToBottom} branch — true for
 * {@code ChatScreen}'s usage) — decompiled and confirmed against MC 26.2's
 * {@code minecraft-merged-deobf-26.2.jar} ({@code net/minecraft/client/gui/components/CommandSuggestions.class}).
 * The literal {@code 12} appears exactly once in this method's bytecode, so no ordinal is needed.
 * This popup is rebuilt fresh from {@code screen.height} on every suggestion update (each
 * keystroke), so it never needs a separate reposition call — it simply reads the shifted value the
 * next time it recomputes. Attached to the same shared origin the input box itself uses —
 * {@link TotalityChatLayout#inputBottomMargin()} — rather than independently re-deriving it.
 */
@Mixin(CommandSuggestions.class)
public class CommandSuggestionsListPositionMixin {

    @ModifyConstant(
            method = "showSuggestions",
            constant = @Constant(intValue = 12)
    )
    private int totality$raiseSuggestionsList(int original) {
        return TotalityChatLayout.inputBottomMargin();
    }
}
