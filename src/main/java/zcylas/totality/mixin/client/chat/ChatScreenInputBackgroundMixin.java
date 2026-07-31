package zcylas.totality.mixin.client.chat;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import zcylas.totality.client.renderer.hud.TotalityChatLayout;

/**
 * ChatScreen visual-correction pass: a full-width translucent black strip remained rendered at
 * the screen's true bottom edge even after {@link ChatScreenInputPositionMixin} moved the input
 * box itself upward. Decompiled and confirmed against MC 26.2's
 * {@code minecraft-merged-deobf-26.2.jar} ({@code net/minecraft/client/gui/screens/ChatScreen.class}):
 * {@code ChatScreen.extractRenderState(GuiGraphicsExtractor, int, int, float)} draws a
 * {@code graphics.fill(2, this.height - 14, this.width - 2, this.height - 2, <vanilla background
 * color>)} as its very first statement, <em>before</em>
 * calling {@code ChatComponent.extractRenderState} (messages) or delegating to
 * {@code Screen.extractRenderState} (which renders the actual {@code EditBox} widget). This fill
 * is a completely separate draw call from the box itself — {@code ChatScreen.extractBackground}
 * (a distinct method) is an empty no-op in this version, so this {@code fill} call inside
 * {@code extractRenderState} is the input's entire visual backing (the {@code EditBox} itself is
 * borderless — {@code setBordered(false)}, confirmed in {@code init()}'s bytecode).
 *
 * <p>The literal {@code 14} appears exactly once in this method's bytecode. The literal {@code 2}
 * appears three times: the fill's left X margin (ordinal 0), the {@code width - 2} right X margin
 * (ordinal 1), and the {@code height - 2} bottom Y margin (ordinal 2) — only the third (Y) may
 * move; the first two are horizontal margins the task explicitly requires stay untouched.
 *
 * <p>Both handlers return {@link TotalityChatLayout#inputBackgroundTopMargin()}/
 * {@link TotalityChatLayout#inputBackgroundBottomMargin()} — each derived from
 * {@link TotalityChatLayout#inputBottomMargin()}, the exact same shared origin
 * {@link ChatScreenInputPositionMixin} already uses for the {@code EditBox} itself — rather than
 * independently adding the reservation to these two raw literals a second time.
 */
@Mixin(ChatScreen.class)
public class ChatScreenInputBackgroundMixin {

    @ModifyConstant(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            constant = @Constant(intValue = 14)
    )
    private int totality$raiseInputBackgroundTop(int original) {
        return TotalityChatLayout.inputBackgroundTopMargin();
    }

    @ModifyConstant(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            constant = @Constant(intValue = 2, ordinal = 2)
    )
    private int totality$raiseInputBackgroundBottom(int original) {
        return TotalityChatLayout.inputBackgroundBottomMargin();
    }
}
