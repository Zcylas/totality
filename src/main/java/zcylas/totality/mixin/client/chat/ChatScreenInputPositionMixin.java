package zcylas.totality.mixin.client.chat;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import zcylas.totality.client.renderer.hud.TotalityChatLayout;

/**
 * Final player-HUD and vanilla-chat compatibility correction: {@code ChatScreen.init()} builds
 * its input {@code EditBox} at {@code y = this.height - 12} — decompiled and confirmed against MC
 * 26.2's {@code minecraft-merged-deobf-26.2.jar} ({@code net/minecraft/client/gui/screens/ChatScreen.class}).
 * The literal {@code 12} appears twice in {@code init()}'s bytecode: first as this Y position,
 * then again immediately after as the {@code EditBox}'s own fixed height parameter — only the
 * first (ordinal 0) is the position and must move; the second must not (moving it would resize
 * the input box, not reposition it).
 *
 * <p>This is the single shared bottom origin every other chat element derives from — see
 * {@link TotalityChatLayout#inputBottomMargin()} (also used by the suggestion popup) and
 * {@link TotalityChatLayout#messageBottomMargin()} (the message stack, offset above this origin
 * by vanilla's own {@code MESSAGE_TO_INPUT_GAP}).
 */
@Mixin(ChatScreen.class)
public class ChatScreenInputPositionMixin {

    @ModifyConstant(
            method = "init",
            constant = @Constant(intValue = 12, ordinal = 0)
    )
    private int totality$raiseInputBox(int original) {
        return TotalityChatLayout.inputBottomMargin();
    }
}
