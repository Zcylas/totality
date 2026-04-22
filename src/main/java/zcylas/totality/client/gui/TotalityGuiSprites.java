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

    // Grimoire
    public static final Identifier GRIMOIRE_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "background");
    public static final Identifier GRIMOIRE_DARK_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "dark_background");
    public static final Identifier GRIMOIRE_SLOT =
            Identifier.fromNamespaceAndPath("totality", "grimoire_slot");
    public static final Identifier GRIMOIRE_TEXT_FIELD =
            Identifier.fromNamespaceAndPath("totality", "text_field");
    public static final Identifier SPELL_TAB =
            Identifier.fromNamespaceAndPath("totality", "spell_tab");
    public static final Identifier SPELL_TAB_SELECTED =
            Identifier.fromNamespaceAndPath("totality", "spell_tab_selected");

    // Rune icons
    public static final Identifier RUNE_TOUCH =
            Identifier.fromNamespaceAndPath("totality", "rune/touch");
    public static final Identifier RUNE_PROJECTILE =
            Identifier.fromNamespaceAndPath("totality", "rune/projectile");
    public static final Identifier RUNE_BREAK =
            Identifier.fromNamespaceAndPath("totality", "rune/break");
    public static final Identifier RUNE_AMPLIFY =
            Identifier.fromNamespaceAndPath("totality", "rune/amplify");
    public static final Identifier RUNE_SELF =
            Identifier.fromNamespaceAndPath("totality", "rune/self");
    public static final Identifier RUNE_AOE =
            Identifier.fromNamespaceAndPath("totality", "rune/aoe");
    public static final Identifier RUNE_PICKUP =
            Identifier.fromNamespaceAndPath("totality", "rune/pickup");
    public static final Identifier RUNE_LAUNCH =
            Identifier.fromNamespaceAndPath("totality", "rune/launch");
    public static final Identifier RUNE_IGNITE =
            Identifier.fromNamespaceAndPath("totality", "rune/ignite");
    public static final Identifier RUNE_EXPLOSION =
            Identifier.fromNamespaceAndPath("totality", "rune/explosion");
    public static final Identifier RUNE_GLIDE =
            Identifier.fromNamespaceAndPath("totality", "rune/glide");
    public static final Identifier RUNE_EXTEND_TIME =
            Identifier.fromNamespaceAndPath("totality", "rune/extend_time");
    public static final Identifier RUNE_REDUCE_TIME =
            Identifier.fromNamespaceAndPath("totality", "rune/reduce_time");
    public static final Identifier RUNE_DAMPEN =
            Identifier.fromNamespaceAndPath("totality", "rune/dampen");
    public static final Identifier RUNE_SENSITIVE =
            Identifier.fromNamespaceAndPath("totality", "rune/sensitive");
    public static final Identifier RUNE_PIERCE =
            Identifier.fromNamespaceAndPath("totality", "rune/pierce");
    public static final Identifier RUNE_SMELT =
            Identifier.fromNamespaceAndPath("totality", "rune/smelt");
    public static final Identifier RUNE_ORBIT =
            Identifier.fromNamespaceAndPath("totality", "rune/orbit");

    private TotalityGuiSprites() {}
}