package zcylas.totality.screen.phone;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.item.energy.PhoneItem;

/**
 * Visual frame/material for a phone tier — mirrors the battery-tier naming already
 * established in {@code init/items/EnergyItems.java}. Only {@link #COPPER} is used by
 * a real item today (Basic Copper Phone); the rest exist so future phone tiers only
 * need to pick a constant, not touch any screen-rendering code.
 */
public enum PhoneFrame {
    COPPER(0xFFB87A1A, 0xFFD4A030, 0xFF7A5010),
    IRON(0xFFAAAAAA, 0xFFE0E0E0, 0xFF707070),
    GOLD(0xFFCCAA33, 0xFFFFDD55, 0xFF886611),
    DIAMOND(0xFF44CCEE, 0xFF88EEFF, 0xFF227788),
    NETHERITE(0xFF3A3A3A, 0xFF5A5A5A, 0xFF1A1A1A);

    /** Base frame color. */
    public final int color;
    /** Highlight/edge color — used for corner accents and hovered/active states. */
    public final int colorBright;
    /** Shadow/recess color — used for inner bevel lines. */
    public final int colorDim;

    PhoneFrame(int color, int colorBright, int colorDim) {
        this.color = color;
        this.colorBright = colorBright;
        this.colorDim = colorDim;
    }

    /** Resolves the frame for a stack, falling back to {@link #COPPER} if it isn't a phone. */
    public static PhoneFrame forStack(ItemStack stack) {
        return stack.getItem() instanceof PhoneItem phone ? phone.getFrame() : COPPER;
    }
}
