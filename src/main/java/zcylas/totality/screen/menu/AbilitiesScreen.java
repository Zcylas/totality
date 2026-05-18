package zcylas.totality.screen.menu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.EquipAbilityPayload;

import java.util.ArrayList;
import java.util.List;

public class AbilitiesScreen extends net.minecraft.client.gui.screens.Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COLOR_BG           = 0xCC001020;
    private static final int COLOR_BORDER       = 0xFF0A5070;
    private static final int COLOR_BORDER_GLOW  = 0x440A8FBF;
    private static final int COLOR_VALUE        = 0xFF00CCFF;
    private static final int COLOR_LABEL        = 0xFF5599BB;
    private static final int COLOR_PANEL_NORMAL = 0xBB001828;
    private static final int COLOR_PANEL_HOVER  = 0xCC002844;
    private static final int COLOR_PANEL_SEL    = 0xFF00CCFF;
    private static final int COLOR_EQUIPPED_BG  = 0xCC002030;
    private static final int COLOR_EQUIPPED_BDR = 0xFF00CCFF;
    private static final int COLOR_DESC         = 0xFF88AABB;
    private static final int COLOR_TYPE_ACTIVE  = 0xFF00CCFF;
    private static final int COLOR_TYPE_PASSIVE = 0xFF44BB88;
    private static final int COLOR_TYPE_CHANNEL = 0xFFCC8800;
    private static final int COLOR_CARD_EQUIP   = 0x330A8FBF;
    private static final int COLOR_TYPE_TOGGLE = 0xFF9944FF; // purple

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PANEL_W = 460;
    private static final int PANEL_H        = 260;
    private static final int TITLE_BAR_H    = 14; // "ABILITIES" + ✕
    private static final int EQUIPPED_BAR_H = 40; // equipped strip
    private static final int HEADER_H       = 16; // PASSIVE / ACTIVE / CHANNELED
    private static final int DETAIL_H       = 52;
    private static final int COL_GAP        = 6;
    private static final int CARD_H         = 36;
    private static final int CARD_GAP       = 3;
    private static final int CARD_ICON_SZ   = 32;
    private static final int SCROLL_SPEED   = 12;

    // ── State ─────────────────────────────────────────────────────────────────
    private float alpha        = 0f;
    private boolean fadingOut  = false;
    private Runnable onFadeOutDone = null;

    private final List<Ability> passives  = new ArrayList<>();
    private final List<Ability> actives   = new ArrayList<>();
    private final List<Ability> channeled = new ArrayList<>();
    private final List<Ability> toggles = new ArrayList<>();

    private int scrollPassive   = 0;
    private int scrollActive    = 0;
    private int scrollChanneled = 0;
    private int scrollToggle = 0;

    private Ability hoveredAbility  = null;
    private Ability selectedAbility = null;

    public AbilitiesScreen() {
        super(Component.literal("Abilities"));
    }

    @Override
    protected void init() {
        super.init();
        alpha = 0f;
        fadingOut = false;
        hoveredAbility = null;

        passives.clear();
        actives.clear();
        channeled.clear();
        toggles.clear();

        for (Identifier id : ClientAbilityManager.getUnlocked()) {
            Ability ability = AbilityRegistry.get(id);
            if (ability == null) continue;
            switch (ability.getType()) {
                case PASSIVE   -> passives.add(ability);
                case ACTIVE    -> actives.add(ability);
                case CHANNELED -> channeled.add(ability);
                case TOGGLE -> toggles.add(ability);
            }
        }

        Identifier equipped = ClientAbilityManager.getEquippedAbility();
        if (equipped != null) selectedAbility = AbilityRegistry.get(equipped);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private int panelX()      { return width  / 2 - PANEL_W / 2; }
    private int panelY()      { return height / 2 - PANEL_H / 2; }

    /** Y of the equipped bar — directly below title bar. */
    private int equippedBarY() { return panelY() + TITLE_BAR_H; }

    /** Y of the column headers — directly below equipped bar. */
    private int headersY()     { return equippedBarY() + EQUIPPED_BAR_H; }

    /** Y where cards start — directly below headers. */
    private int colsY()        { return headersY() + HEADER_H; }

    /** Available height for scrollable cards. */
    private int colScrollH()   { return PANEL_H - TITLE_BAR_H - EQUIPPED_BAR_H - HEADER_H - DETAIL_H - 8; }

    private int colW()        { return (PANEL_W - COL_GAP * 3) / 4; }
    private int colX(int col) { return panelX() + col * (colW() + COL_GAP); }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, (int)(alpha * 0x88) << 24);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        if (fadingOut) {
            alpha = Math.max(0f, alpha - 0.12f);
            if (alpha <= 0f && onFadeOutDone != null) { onFadeOutDone.run(); return; }
        } else {
            alpha = Math.min(1f, alpha + 0.12f);
        }
        int ba = (int)(alpha * 255);

        hoveredAbility = null;

        int px = panelX(), py = panelY();

        // ── Outer panel ──
        g.fill(px, py, px + PANEL_W, py + PANEL_H, withAlpha(COLOR_BG, ba));
        drawBorder(g, px, py, PANEL_W, PANEL_H, withAlpha(COLOR_BORDER, ba));
        g.fill(px - 2, py - 2, px + PANEL_W + 2, py + PANEL_H + 2,
                withAlpha(COLOR_BORDER_GLOW, ba / 2));

        // ── Row 1: Title bar — ABILITIES + ✕ ──
        g.fill(px, py, px + PANEL_W, py + TITLE_BAR_H, withAlpha(0xAA001828, ba));
        g.fill(px, py + TITLE_BAR_H - 1, px + PANEL_W, py + TITLE_BAR_H,
                withAlpha(COLOR_BORDER, ba));
        String title = "ABILITIES";
        g.text(font, Component.literal(title),
                px + PANEL_W / 2 - font.width(title) / 2, py + 3,
                withAlpha(COLOR_VALUE, ba), false);
        String closeStr = "✕";
        g.text(font, Component.literal(closeStr),
                px + PANEL_W - font.width(closeStr) - 5, py + 3,
                withAlpha(COLOR_LABEL, ba), false);

        // ── Row 2: Equipped bar ──
        drawEquippedBar(g, ba, px, py);

        // ── Row 3: Column headers ──
        int hy = headersY();
        drawColumnHeader(g, ba, 0, hy, "PASSIVE",   COLOR_TYPE_PASSIVE);
        drawColumnHeader(g, ba, 1, hy, "ACTIVE",    COLOR_TYPE_ACTIVE);
        drawColumnHeader(g, ba, 2, hy, "CHANNELED", COLOR_TYPE_CHANNEL);
        drawColumnHeader(g, ba, 3, hy, "TOGGLE",    COLOR_TYPE_TOGGLE);

        // ── Rows 4+: Scrollable card columns ──
        int clampY = colsY();
        int clampH = colScrollH();
        drawColumn(g, mx, my, ba, 0, passives,  scrollPassive,   clampY, clampH);
        drawColumn(g, mx, my, ba, 1, actives,   scrollActive,    clampY, clampH);
        drawColumn(g, mx, my, ba, 2, channeled, scrollChanneled, clampY, clampH);
        drawColumn(g, mx, my, ba, 3, toggles,   scrollToggle,    clampY, clampH);

        // ── Detail panel ──
        drawDetailPanel(g, ba, px, py);
    }

    /**
     * Full-width bar directly below the title.
     * Contains: "EQUIPPED" label + icon + ability name, all centered as one group.
     * "[X] Unequip" hint on the right when something is equipped.
     */
    private void drawEquippedBar(GuiGraphicsExtractor g, int ba, int px, int py) {
        int barY = equippedBarY();
        int barH = EQUIPPED_BAR_H;

        g.fill(px, barY, px + PANEL_W, barY + barH, withAlpha(COLOR_EQUIPPED_BG, ba));
        g.fill(px, barY + barH - 1, px + PANEL_W, barY + barH, withAlpha(COLOR_BORDER, ba));

        Identifier equippedId = ClientAbilityManager.getEquippedAbility();
        int textY = barY + barH / 2 - font.lineHeight / 2;

        if (equippedId != null) {
            Ability eq = AbilityRegistry.get(equippedId);
            if (eq != null) {
                // Measure the whole group: "EQUIPPED  [icon] Name"
                String equippedLabel = "EQUIPPED";
                int gap1  = 6;  // gap between "EQUIPPED" and icon
                int gap2  = 4;  // gap between icon and name
                String name = eq.getDisplayName();

                int groupW = font.width(equippedLabel) + gap1 + CARD_ICON_SZ + gap2 + font.width(name);
                int groupX = px + PANEL_W / 2 - groupW / 2;

                // Draw label
                g.text(font, Component.literal(equippedLabel),
                        groupX, textY,
                        withAlpha(COLOR_LABEL, ba), false);

                // Draw icon
                int iconX = groupX + font.width(equippedLabel) + gap1;
                int iconY = barY + barH / 2 - CARD_ICON_SZ / 2;
                drawAbilityIcon(g, eq, iconX, iconY, ba);

                // Draw name
                g.text(font, Component.literal(name),
                        iconX + CARD_ICON_SZ + gap2, textY,
                        withAlpha(COLOR_VALUE, ba), false);

                // Unequip hint on the right
                String hint = "[X] Unequip";
                g.text(font, Component.literal(hint),
                        px + PANEL_W - font.width(hint) - 6, textY,
                        withAlpha(COLOR_LABEL, ba), false);
            }
        } else {
            // Centered "EQUIPPED  None"
            String equippedLabel = "EQUIPPED";
            String none = "None";
            int groupW = font.width(equippedLabel) + 6 + font.width(none);
            int groupX = px + PANEL_W / 2 - groupW / 2;

            g.text(font, Component.literal(equippedLabel),
                    groupX, textY,
                    withAlpha(COLOR_LABEL, ba), false);
            g.text(font, Component.literal(none),
                    groupX + font.width(equippedLabel) + 6, textY,
                    withAlpha(COLOR_LABEL, ba), false);
        }
    }

    private void drawColumnHeader(GuiGraphicsExtractor g, int ba,
                                  int col, int headerY, String label, int color) {
        int cx = colX(col);
        int cw = colW();
        g.text(font, Component.literal(label),
                cx + cw / 2 - font.width(label) / 2, headerY + 3,
                withAlpha(color, ba), false);
        g.fill(cx, headerY + HEADER_H - 2, cx + cw, headerY + HEADER_H - 1,
                withAlpha(color, ba / 2));
    }

    private void drawColumn(GuiGraphicsExtractor g, int mx, int my, int ba,
                            int col, List<Ability> abilities,
                            int scroll, int clampY, int clampH) {
        int cx = colX(col);
        int cw = colW();
        Identifier equippedId = ClientAbilityManager.getEquippedAbility();

        int cardY = clampY - scroll;
        for (Ability ability : abilities) {
            int cardBottom = cardY + CARD_H;
            if (cardBottom > clampY && cardY < clampY + clampH) {
                boolean hovered = mx >= cx && mx < cx + cw
                        && my >= Math.max(cardY, clampY)
                        && my < Math.min(cardBottom, clampY + clampH);
                boolean isEquipped = ability.getId().equals(equippedId);

                if (hovered) hoveredAbility = ability;

                int bgColor = hovered    ? withAlpha(COLOR_PANEL_HOVER, ba)
                        : isEquipped     ? withAlpha(COLOR_CARD_EQUIP,  ba)
                        :                  withAlpha(COLOR_PANEL_NORMAL, ba);
                g.fill(cx, cardY, cx + cw, cardBottom, bgColor);
                drawBorder(g, cx, cardY, cw, CARD_H,
                        withAlpha(hovered || isEquipped ? COLOR_PANEL_SEL : COLOR_BORDER, ba));

                drawAbilityIcon(g, ability, cx + 4, cardY + (CARD_H - CARD_ICON_SZ) / 2, ba);

                String name = ability.getDisplayName();
                int nameX = cx + 4 + CARD_ICON_SZ + 4;
                int nameY = cardY + CARD_H / 2 - font.lineHeight / 2;
                g.text(font, Component.literal(name), nameX, nameY,
                        withAlpha(hovered ? COLOR_VALUE : COLOR_LABEL, ba), false);

                if (isEquipped) {
                    g.fill(cx + cw - 8, cardY + CARD_H / 2 - 2,
                            cx + cw - 4, cardY + CARD_H / 2 + 2,
                            withAlpha(COLOR_VALUE, ba));
                }
            }
            cardY += CARD_H + CARD_GAP;
        }
    }

    private void drawDetailPanel(GuiGraphicsExtractor g, int ba, int px, int py) {
        int detailY = py + PANEL_H - DETAIL_H - 4;
        int detailX = px + 4;
        int detailW = PANEL_W - 8;

        Ability show = hoveredAbility != null ? hoveredAbility : selectedAbility;

        g.fill(detailX, detailY, detailX + detailW, detailY + DETAIL_H,
                withAlpha(COLOR_PANEL_NORMAL, ba));
        drawBorder(g, detailX, detailY, detailW, DETAIL_H,
                withAlpha(COLOR_BORDER, ba));

        if (show == null) {
            String hint = "Hover or select an ability to see details.";
            g.text(font, Component.literal(hint),
                    detailX + detailW / 2 - font.width(hint) / 2,
                    detailY + DETAIL_H / 2 - font.lineHeight / 2,
                    withAlpha(COLOR_LABEL, ba), false);
            return;
        }

        drawAbilityIcon(g, show, detailX + 6, detailY + DETAIL_H / 2 - CARD_ICON_SZ / 2, ba);

        int textX = detailX + 6 + CARD_ICON_SZ + 8;

        g.text(font, Component.literal(show.getDisplayName()),
                textX, detailY + 6, withAlpha(COLOR_VALUE, ba), true);

        String typeName = switch (show.getType()) {
            case PASSIVE   -> "Passive";
            case ACTIVE    -> "Active";
            case CHANNELED -> "Channeled";
            case TOGGLE -> "Toggle";
        };
        int typeColor = switch (show.getType()) {
            case PASSIVE   -> COLOR_TYPE_PASSIVE;
            case ACTIVE    -> COLOR_TYPE_ACTIVE;
            case CHANNELED -> COLOR_TYPE_CHANNEL;
            case TOGGLE -> withAlpha(COLOR_TYPE_TOGGLE, ba);
        };
        g.text(font, Component.literal("· " + typeName),
                textX + font.width(show.getDisplayName()) + 6, detailY + 6,
                withAlpha(typeColor, ba), false);

        if (show.getCooldownTicks() > 0) {
            String cd = "Cooldown: " + (show.getCooldownTicks() / 20) + "s";
            g.text(font, Component.literal(cd),
                    textX, detailY + 6 + font.lineHeight + 2,
                    withAlpha(COLOR_LABEL, ba), false);
        }

        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(show.getDescription()), detailW - (textX - detailX) - 8);
        int lineY = detailY + 6 + font.lineHeight * 2 + 4;
        for (var line : lines) {
            if (lineY + font.lineHeight > detailY + DETAIL_H - 4) break;
            g.text(font, line, textX, lineY, withAlpha(COLOR_DESC, ba), false);
            lineY += font.lineHeight + 1;
        }

        if (show.getType() != Ability.Type.PASSIVE) {
            String kb = switch (show.getType()) {
                case TOGGLE    -> "[Z] Toggle";
                case CHANNELED -> "[Z] Hold";
                default        -> "[Z] to use";
            };
            g.text(font, Component.literal(kb),
                    detailX + detailW - font.width(kb) - 6,
                    detailY + DETAIL_H - font.lineHeight - 4,
                    withAlpha(COLOR_LABEL, ba), false);
        }
    }

    private void drawAbilityIcon(GuiGraphicsExtractor g, Ability ability, int x, int y, int ba) {
        g.blit(RenderPipelines.GUI_TEXTURED, ability.getIcon(),
                x, y, 0, 0, CARD_ICON_SZ, CARD_ICON_SZ, 32, 32);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x();
        int my = (int) mouse.y();

        int px = panelX(), py = panelY();

        // Close ✕
        int closeX = px + PANEL_W - font.width("✕") - 5;
        if (mx >= closeX && mx < closeX + font.width("✕") + 4
                && my >= py + 1 && my < py + TITLE_BAR_H) {
            closeScreen();
            return true;
        }

        // Click equipped bar — unequip
        int barY = equippedBarY();
        if (my >= barY && my < barY + EQUIPPED_BAR_H
                && mx >= px && mx < px + PANEL_W
                && ClientAbilityManager.getEquippedAbility() != null) {
            ClientPlayNetworking.send(new EquipAbilityPayload(null));
            playClick();
            return true;
        }

        // Card click — equip or select
        if (hoveredAbility != null) {
            selectedAbility = hoveredAbility;
            playClick();
            if (hoveredAbility.getType() != Ability.Type.PASSIVE) {
                ClientPlayNetworking.send(new EquipAbilityPayload(hoveredAbility.getId()));
            }
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int delta = (int) (-scrollY * SCROLL_SPEED);
        int clampY = colsY();
        int clampH = colScrollH();

        if (my < clampY || my > clampY + clampH) return false;

        for (int col = 0; col < 3; col++) {
            int cx = colX(col);
            if (mx >= cx && mx < cx + colW()) {
                switch (col) {
                    case 0 -> scrollPassive   = clampScroll(scrollPassive   + delta, passives);
                    case 1 -> scrollActive    = clampScroll(scrollActive    + delta, actives);
                    case 2 -> scrollChanneled = clampScroll(scrollChanneled + delta, channeled);
                    case 3 -> scrollToggle = clampScroll(scrollToggle + delta, toggles);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            closeScreen();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_X) {
            ClientPlayNetworking.send(new EquipAbilityPayload(null));
            playClick();
            return true;
        }
        return super.keyPressed(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int clampScroll(int scroll, List<Ability> list) {
        int maxScroll = Math.max(0, list.size() * (CARD_H + CARD_GAP) - colScrollH());
        return Math.max(0, Math.min(scroll, maxScroll));
    }

    private void closeScreen() {
        fadeOutTo(() -> Minecraft.getInstance().setScreen(null));
    }

    private void fadeOutTo(Runnable onDone) {
        fadingOut = true;
        onFadeOutDone = onDone;
    }

    private void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w, y + 1,     color);
        g.fill(x,         y + h - 1, x + w, y + h,     color);
        g.fill(x,         y,         x + 1, y + h,     color);
        g.fill(x + w - 1, y,         x + w, y + h,     color);
    }

    private int withAlpha(int color, int alpha) {
        return ((((color >> 24) & 0xFF) * alpha / 255) << 24) | (color & 0x00FFFFFF);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean isInGameUi()    { return false; }
    @Override public boolean isPauseScreen() { return false; }
}