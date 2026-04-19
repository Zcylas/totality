package zcylas.totality.client.gui;

import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

public class TotalityGuiSprites {
    // Backgrounds
    public static final Identifier CONFIG_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "background");
    public static final Identifier GUI_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "gui_background");
    // Energy
    public static final Identifier ENERGY_BAR_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "energy_bar_background");
    public static final Identifier ENERGY_BAR_FILL =
            Identifier.fromNamespaceAndPath("totality", "energy_bar_fill");

    // Generator
    public static final Identifier BOLT_LIT =
            Identifier.fromNamespaceAndPath("totality", "bolt_lit");
    public static final Identifier BOLT_UNLIT =
            Identifier.fromNamespaceAndPath("totality", "bolt_unlit");
    //Buttons
    public static final Identifier CLOSE_BUTTON = Identifier.fromNamespaceAndPath("totality", "close_button");
    public static final Identifier CLOSE_BUTTON_HOVERED = Identifier.fromNamespaceAndPath("totality", "close_button_hovered");
    // Vanilla slots
    public static final Identifier SLOT =
            Identifier.withDefaultNamespace("container/slot");
    public static final Identifier SLOT_OUTPUT =
            Identifier.withDefaultNamespace("container/furnace/result");
    //Tabs
    public static final Identifier LEFT_TAB =
            Identifier.fromNamespaceAndPath("totality", "left_tab");
    public static final Identifier SLOT_TAB =
            Identifier.fromNamespaceAndPath("totality", "slot_tab");
    //Icons
    public static final Identifier ICON_CONFIG =
            Identifier.fromNamespaceAndPath("totality", "icon_config");
    public static final Identifier ICON_TUTORIAL =
            Identifier.fromNamespaceAndPath("totality", "icon_tutorial");

    private TotalityGuiSprites() {}
}