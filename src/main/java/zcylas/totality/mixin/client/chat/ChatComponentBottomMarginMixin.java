package zcylas.totality.mixin.client.chat;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import zcylas.totality.client.renderer.hud.TotalityChatLayout;

/**
 * Final player-HUD and vanilla-chat compatibility correction: vanilla's chat still overlapped
 * Totality's Health/Mana bars at live GUI Scale 4. {@code ChatComponent}'s private
 * {@code extractRenderState(ChatGraphicsAccess, int, int, DisplayMode)} is the single vanilla
 * method that computes chat's bottom Y anchor ({@code bottom = floor((guiHeight - C) / scale)})
 * for every purpose at once — rendered message lines, the restricted-chat-prompt background, the
 * queued-message indicator, AND (via {@code ChatComponent.captureClickableText}, which calls this
 * exact same private method) hover/click hit-testing — decompiled and confirmed against MC 26.2's
 * {@code minecraft-merged-deobf-26.2.jar} ({@code net/minecraft/client/gui/components/ChatComponent.class}):
 * the vanilla literal ({@code BOTTOM_MARGIN}, {@code 40}) appears exactly once in this method's
 * bytecode. Because the modified value sits inside the numerator of a {@code (guiHeight - C) /
 * scale} expression, the resulting screen-space position ({@code guiHeight - C}, since {@code
 * bottom_local * scale == guiHeight - C} by construction) is exactly {@code C} pixels from the
 * top regardless of the chat-scale option — scale-invariant, matching the other three (unscaled)
 * chat mixins' own screen-pixel arithmetic exactly.
 *
 * <p><b>Chat-origin correction (this pass):</b> the message-bottom anchor no longer adds the
 * reservation to vanilla's own unrelated {@code BOTTOM_MARGIN}(40) — it is fully replaced by
 * {@link TotalityChatLayout#messageBottomMargin()}, which derives from the exact same shared
 * origin the input box uses ({@link TotalityChatLayout#inputBottomMargin()}) plus vanilla's own
 * {@link TotalityChatLayout#MESSAGE_TO_INPUT_GAP} (8px) — see that class's javadoc for why this
 * fixes the large empty gap live testing found between the message stack and the input line.
 */
@Mixin(ChatComponent.class)
public class ChatComponentBottomMarginMixin {

    @ModifyConstant(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            constant = @Constant(intValue = 40)
    )
    private int totality$anchorBottomMarginToTheSharedInputOrigin(int original) {
        return TotalityChatLayout.messageBottomMargin();
    }
}
