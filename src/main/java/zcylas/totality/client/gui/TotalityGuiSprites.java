package zcylas.totality.client.gui;

import net.minecraft.resources.Identifier;

public class TotalityGuiSprites {
    // Backgrounds
    public static final Identifier CONFIG_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "background");
    public static final Identifier GUI_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "gui_background");
    public static final Identifier SKYRIM_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "skyrim_background");
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

    // Buttons
    public static final Identifier CLOSE_BUTTON =
            Identifier.fromNamespaceAndPath("totality", "close_button");
    public static final Identifier CLOSE_BUTTON_HOVERED =
            Identifier.fromNamespaceAndPath("totality", "close_button_hovered");
    public static final Identifier SKYRIM_BUTTON =
            Identifier.fromNamespaceAndPath("totality", "skyrim_button");

    // Vanilla slots
    public static final Identifier SLOT =
            Identifier.withDefaultNamespace("container/slot");
    public static final Identifier SLOT_OUTPUT =
            Identifier.withDefaultNamespace("container/furnace/result");

    // Tabs
    public static final Identifier LEFT_TAB =
            Identifier.fromNamespaceAndPath("totality", "left_tab");
    public static final Identifier SLOT_TAB =
            Identifier.fromNamespaceAndPath("totality", "slot_tab");

    // Icons
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

    // ── HUD Bars ──────────────────────────────────────────────────────────────
    // Shared background (165x16) — same sprite for all bars
    public static final Identifier HUD_BAR_BACKGROUND =
            Identifier.fromNamespaceAndPath("totality", "hud/bar_background");

    // Fill sprites (144x8) — one per stat, different color gradient
    public static final Identifier HUD_HEALTH_FILL =
            Identifier.fromNamespaceAndPath("totality", "hud/health_filled");
    public static final Identifier HUD_MANA_FILL =
            Identifier.fromNamespaceAndPath("totality", "hud/mana_filled");
    public static final Identifier HUD_STAMINA_FILL =
            Identifier.fromNamespaceAndPath("totality", "hud/stamina_filled");
    public static final Identifier HUD_HUNGER_FILL =
            Identifier.fromNamespaceAndPath("totality", "hud/hunger_filled");

    // Rune icons
    public static final Identifier RUNE_TOUCH =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/touch");
    public static final Identifier RUNE_PROJECTILE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/projectile");
    public static final Identifier RUNE_BREAK =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/break");
    public static final Identifier RUNE_AMPLIFY =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/amplify");
    public static final Identifier RUNE_SELF =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/self");
    public static final Identifier RUNE_AOE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/aoe");
    public static final Identifier RUNE_PICKUP =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/pickup");
    public static final Identifier RUNE_LAUNCH =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/launch");
    public static final Identifier RUNE_IGNITE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/ignite");
    public static final Identifier RUNE_EXPLOSION =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/explosion");
    public static final Identifier RUNE_GLIDE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/glide");
    public static final Identifier RUNE_EXTEND_TIME =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/extend_time");
    public static final Identifier RUNE_REDUCE_TIME =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/reduce_time");
    public static final Identifier RUNE_DAMPEN =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/dampen");
    public static final Identifier RUNE_SENSITIVE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/sensitive");
    public static final Identifier RUNE_PIERCE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/pierce");
    public static final Identifier RUNE_SMELT =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/smelt");
    public static final Identifier RUNE_ORBIT =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/orbit");
    public static final Identifier RUNE_FORTUNE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/fortune");
    public static final Identifier RUNE_RANDOMIZE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/randomize");
    public static final Identifier RUNE_HARM =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/harm");
    public static final Identifier RUNE_HEAL =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/heal");
    public static final Identifier RUNE_HEX =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/hex");
    public static final Identifier RUNE_LIGHTNING =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/lightning");
    public static final Identifier RUNE_CHAINING =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/chaining");
    public static final Identifier RUNE_EXTRACT =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/extract");
    public static final Identifier RUNE_GROW =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/grow");
    public static final Identifier RUNE_ACCELERATE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/accelerate");
    public static final Identifier RUNE_DECELERATE =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/decelerate");
    public static final Identifier RUNE_LINGER =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/linger");
    public static final Identifier RUNE_HARVEST =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/harvest");
    public static final Identifier RUNE_SPLIT =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/split");
    public static final Identifier RUNE_BURST =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/burst");
    public static final Identifier RUNE_SUMMON_UNDEAD =
            Identifier.fromNamespaceAndPath("totality", "gui/rune/summon_undead");

    private TotalityGuiSprites() {}
}