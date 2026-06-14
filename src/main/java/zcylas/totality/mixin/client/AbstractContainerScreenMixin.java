package zcylas.totality.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.client.item.AttunementClientManager;
import zcylas.totality.client.item.AttunementHud;
import zcylas.totality.client.tooltip.TotalityTooltipRenderer;

import java.util.List;
import java.util.Optional;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Unique
    private ItemStack totality$lastHoveredStack = ItemStack.EMPTY;

    @Redirect(
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"),
            method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"
    )
    private void onSetTooltip(GuiGraphicsExtractor graphics, Font font, List<Component> text,
                              Optional<TooltipComponent> data, int x, int y, Identifier backgroundTexture) {

        ItemStack stack = this.hoveredSlot == null ? ItemStack.EMPTY : this.hoveredSlot.getItem();

        if (!ItemStack.isSameItemSameComponents(stack, totality$lastHoveredStack)) {
            totality$lastHoveredStack = stack.copy();
        }

        var rarityType = ItemComponents.getRarity();
        if (rarityType != null && !stack.isEmpty() && stack.has(rarityType)) {
            TotalityTooltipRenderer.render(graphics, font, stack, x, y);
            return;
        }

        graphics.setTooltipForNextFrame(font, text, data, x, y, backgroundTexture);
    }

    /** Draw the attunement ring after tooltip extraction — renders over inventory content. */
    @Inject(at = @At("TAIL"),
            method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V")
    private void afterExtractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     CallbackInfo ci) {
        if (AttunementClientManager.isActive()) {
            AttunementHud.renderOnScreen(graphics, mouseX, mouseY,
                    Minecraft.getInstance().getWindow().getGuiScaledWidth());
        }
    }
}