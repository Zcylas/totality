package zcylas.totality.screen.equipment;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.client.equipment.ClientEquipmentManager;
import zcylas.totality.item.energy.PhoneItem;
import zcylas.totality.mixin.client.AbstractContainerScreenAccessor;
import zcylas.totality.networking.equipment.OpenAccessoryInventoryPayload;
import zcylas.totality.networking.equipment.OpenInventoryPayload;

public class AccessoryInventoryButton extends AbstractButton {

    private static final int W = 20;
    private static final int H = 18;

    private final AbstractContainerScreen<?> screen;
    private final boolean isAccessoryScreen;

    public AccessoryInventoryButton(AbstractContainerScreen<?> screen) {
        super(buttonX(screen), buttonY(screen), W, H, Component.literal("R"));
        this.screen = screen;
        this.isAccessoryScreen = screen instanceof AccessoryInventoryScreen;
    }

    /** True when the player is carrying an unequipped phone — used to draw attention to
     *  this button, since equipping the phone requires knowing this button exists first. */
    private boolean hasUnequippedPhone() {
        if (isAccessoryScreen) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (!ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_PHONE).isEmpty()) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof PhoneItem) return true;
        }
        return false;
    }

    private static int buttonX(AbstractContainerScreen<?> screen) {
        return ((AbstractContainerScreenAccessor)(Object) screen).totality$getLeftPos() + 136;
    }

    private static int buttonY(AbstractContainerScreen<?> screen) {
        return ((AbstractContainerScreenAccessor)(Object) screen).totality$getTopPos() + 62;
    }

    private void fixPos() {
        setX(buttonX(screen));
        setY(buttonY(screen));
    }

    /** False when the vanilla recipe book is open — leftPos is shifted, entity center jumps. */
    private boolean isButtonVisible() {
        if (isAccessoryScreen) return true;
        if (!(screen instanceof InventoryScreen)) return true;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int expectedLeftPos = (screenW - 176) / 2;
        return ((AbstractContainerScreenAccessor)(Object) screen).totality$getLeftPos() == expectedLeftPos;
    }

    @Override
    protected boolean isValidClickButton(@NonNull MouseButtonInfo info) {
        return isButtonVisible() && super.isValidClickButton(info);
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        if (!isButtonVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (isAccessoryScreen) {
            player.containerMenu = player.inventoryMenu;
            mc.setScreen(new InventoryScreen(player));
            ClientPlayNetworking.send(new OpenInventoryPayload());
        } else {
            ClientPlayNetworking.send(new OpenAccessoryInventoryPayload());
        }
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor gui, int mx, int my, float partialTicks) {
        if (!isButtonVisible()) return;
        fixPos();
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHoveredOrFocused();

        if (hasUnequippedPhone()) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 150.0) * 0.5 + 0.5);
            int alpha = (int) (0x60 + pulse * 0x9F);
            int glow = (alpha << 24) | 0x00D4A030;
            gui.fill(getX() - 2, getY() - 2, getX() + W + 2, getY() + H + 2, glow);
        }

        int face = hovered ? 0xFFAAAAAA : 0xFF888888;
        gui.fill(getX(),     getY(),     getX() + W,     getY() + H,     0xFF3F3F3F);
        gui.fill(getX() + 1, getY() + 1, getX() + W - 1, getY() + H - 1, face);
        int textColor = active ? 0xFFFFFF : 0xA0A0A0;
        gui.text(mc.font, getMessage(),
                getX() + (W - mc.font.width(getMessage())) / 2,
                getY() + (H - mc.font.lineHeight) / 2,
                textColor, true);

        if (hovered) {
            gui.setTooltipForNextFrame(mc.font, Component.literal("Equipment (Rings, Phone, Pouch...)"), mx, my);
        }
    }

    @Override
    public void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}