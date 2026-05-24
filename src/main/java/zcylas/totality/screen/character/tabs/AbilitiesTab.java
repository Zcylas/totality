package zcylas.totality.screen.character.tabs;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.EquipAbilityPayload;
import zcylas.totality.screen.character.BaseCharacterScreen;
import zcylas.totality.screen.character.CharacterScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AbilitiesTab extends CharacterScreenTab {

    // ── Type colors ───────────────────────────────────────────────────────────
    private static final int COLOR_PASSIVE   = 0xFF44AA44;
    private static final int COLOR_ACTIVE    = 0xFFCC4444;
    private static final int COLOR_CHANNELED = 0xFFAA44CC;
    private static final int COLOR_TOGGLE    = 0xFF44CCAA;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CARD_H     = 76;
    private static final int CARD_PAD   = 5;
    private static final int COL_HDR_H  = 24;
    private static final int ICON_SIZE  = 22;
    private static final int DETAIL_PCT = 33; // wider detail panel vs old 28%

    // ── State ─────────────────────────────────────────────────────────────────
    private @Nullable Ability selectedAbility = null;
    private boolean           bottomBtnHovered = false;
    private final int[]       colScroll = new int[4];
    private int               detailScrollY = 0;

    // Bottom button bounds — set each frame in draw(), read in mouseClicked()
    // Outside the scroll region so coordinates are always stable
    private int bottomBtnX, bottomBtnY, bottomBtnW, bottomBtnH;

    public AbilitiesTab(CharacterScreen screen) { super(screen); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onOpen() {
        Arrays.fill(colScroll, 0);
        detailScrollY = 0;
        Identifier eq = ClientAbilityManager.getEquippedAbility();
        selectedAbility = eq != null ? AbilityRegistry.get(eq) : null;
        if (selectedAbility == null) {
            for (Ability a : AbilityRegistry.all()) {
                if (a.isDefault() || ClientAbilityManager.hasAbility(a.getId())) {
                    selectedAbility = a;
                    break;
                }
            }
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    @Override
    public void mouseScrolled(int mx, int my, double delta) {
        int x       = screen.contentX;
        int w       = screen.contentW;
        // FIX: was "w - w * DETAIL_PCT / 100 - PAD" — the extra PAD caused
        // the detail-panel scroll zone to start in the wrong place
        int colsW   = w - w * DETAIL_PCT / 100;
        int colW    = colsW / 4;
        int detailX = x + colsW;

        if (mx >= detailX) {
            detailScrollY = Math.max(0, detailScrollY - (int)(delta * 12));
        } else {
            for (int i = 0; i < 4; i++) {
                if (screen.inB(mx, my, x + i * colW, screen.contentY, colW, screen.contentH)) {
                    colScroll[i] = Math.max(0, colScroll[i] - (int)(delta * 12));
                    break;
                }
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    @Override
    public void draw(GuiGraphicsExtractor g, Font font,
                     int mx, int my, int ba,
                     int x, int y, int w, int h) {
        bottomBtnHovered = false;

        int detailW = w * DETAIL_PCT / 100;
        int colsW   = w - detailW;
        int detailX = x + colsW;

        drawColumnsPanel(g, font, mx, my, x,       y, colsW,   h);
        drawDetailPanel (g, font, mx, my, detailX, y, detailW, h);
    }

    // ── Columns panel ─────────────────────────────────────────────────────────

    private void drawColumnsPanel(GuiGraphicsExtractor g, Font font,
                                  int mx, int my,
                                  int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);

        Ability.Type[] types = Ability.Type.values();
        int colW = w / 4;

        for (int i = 0; i < types.length; i++) {
            drawColumn(g, font, mx, my, types[i], x + i * colW, y, colW, h, i);
            if (i < 3)
                g.fill(x + (i + 1) * colW, y + PAD,
                        x + (i + 1) * colW + 1, y + h - PAD,
                        COLOR_SEPARATOR);
        }
    }

    private void drawColumn(GuiGraphicsExtractor g, Font font,
                            int mx, int my,
                            Ability.Type type, int x, int y, int w, int h, int idx) {
        int color = getTypeColor(type);

        // ── Header — all SMALL scale for consistent sizing ────────────────────
        int hdrY   = y + PAD;
        String ico = getTypeIcon(type);
        String lbl = type.name();
        // FIX: was font.width(icon) at full scale — now SMALL-scaled to match label
        int icoW   = Math.round(font.width(ico) * SMALL) + 3;
        int lblW   = Math.round(font.width(lbl) * SMALL);
        int startX = x + w / 2 - (icoW + lblW) / 2;

        screen.drawSmallAt(g, ico, startX,        hdrY + (COL_HDR_H - SLH) / 2, color);
        screen.drawSmallAt(g, lbl, startX + icoW, hdrY + (COL_HDR_H - SLH) / 2, color);
        g.fill(x + PAD * 2, hdrY + COL_HDR_H - 1, x + w - PAD * 2, hdrY + COL_HDR_H, color);

        // ── Cards ─────────────────────────────────────────────────────────────
        int cardsTop = hdrY + COL_HDR_H + 3;
        int cardsH   = h - PAD - COL_HDR_H - 3;
        int cardW    = w - PAD * 2 - 2;
        int cardX    = x + PAD + 1;

        List<Ability> abilities = getUnlockedOfType(type);
        screen.sc(g, cardX, cardsTop, cardW, cardsH);

        int cy = cardsTop - colScroll[idx];
        for (Ability ab : abilities) {
            drawCard(g, font, mx, my, ab, cardX, cy, cardW);
            cy += CARD_H + CARD_PAD;
        }
        for (int i = 0; i < 2; i++) {
            drawEmptyCard(g, cardX, cy, cardW);
            cy += CARD_H + CARD_PAD;
        }

        // Clamp scroll to content
        int totalContent = (abilities.size() + 2) * (CARD_H + CARD_PAD);
        colScroll[idx] = Math.min(colScroll[idx], Math.max(0, totalContent - cardsH));

        screen.esc(g);
    }

    private void drawCard(GuiGraphicsExtractor g, Font font,
                          int mx, int my,
                          Ability ab, int x, int y, int w) {
        boolean sel      = ab == selectedAbility;
        boolean equipped = ab.getId().equals(ClientAbilityManager.getEquippedAbility());
        boolean hov      = screen.inB(mx, my, x, y, w, CARD_H);
        int typeColor    = getTypeColor(ab.getType());

        g.fill(x, y, x + w, y + CARD_H,
                sel ? 0xCC001828 : hov ? 0x44001828 : COLOR_PANEL_BG_ALT);
        screen.drawBorder(g, x, y, w, CARD_H, sel ? COLOR_ACCENT : typeColor);

        int cs = 5;
        g.fill(x,          y,              x + cs,    y + 1,       typeColor);
        g.fill(x,          y,              x + 1,     y + cs,      typeColor);
        g.fill(x + w - cs, y,              x + w,     y + 1,       typeColor);
        g.fill(x + w - 1,  y,              x + w,     y + cs,      typeColor);
        g.fill(x,          y + CARD_H - 1, x + cs,    y + CARD_H,  typeColor);
        g.fill(x,          y + CARD_H - cs, x + 1,   y + CARD_H,  typeColor);
        g.fill(x + w - cs, y + CARD_H - 1, x + w,    y + CARD_H,  typeColor);
        g.fill(x + w - 1,  y + CARD_H - cs, x + w,   y + CARD_H,  typeColor);

        // Icon box
        int iconPad = 4;
        int iconX   = x + iconPad;
        int iconY   = y + (CARD_H - ICON_SIZE) / 2;
        g.fill(iconX - 1, iconY - 1, iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1, 0xFF050810);
        screen.drawBorder(g, iconX - 1, iconY - 1, ICON_SIZE + 2, ICON_SIZE + 2, typeColor);
        drawIcon(g, ab.getIcon(), iconX, iconY, ICON_SIZE);

        // Text area
        int tx = iconX + ICON_SIZE + iconPad + 2;
        int tw = w - (tx - x) - 2; // available width, leaving 2px from right edge

        // Word-wrap name (SMALL, max 2 lines) and source (TINY, max 2 lines)
        List<String> nameLines = wrapCard(font, ab.getDisplayName(), tw, SMALL, 2);
        List<String> srcLines  = wrapCard(font, ab.getSourceDetail(), tw, TINY,  2);

        int nameLineH = SLH + 1;
        int srcLineH  = TLH + 1;
        int textH = nameLines.size() * nameLineH - 1
                + 4
                + srcLines.size() * srcLineH - 1;
        int cy = y + (CARD_H - textH) / 2; // vertically center the text block

        int nameColor = sel ? COLOR_ACCENT : COLOR_VALUE;
        for (String line : nameLines) {
            screen.drawSmallAt(g, line, tx, cy, nameColor);
            cy += nameLineH;
        }
        cy += 3;
        for (String line : srcLines) {
            screen.drawTinyAt(g, line, tx, cy, COLOR_LABEL);
            cy += srcLineH;
        }

        // Equipped checkmark — bottom-right
        if (equipped) {
            int ckW = font.width("✓");
            g.text(font, Component.literal("✓"),
                    x + w - ckW - 4, y + CARD_H - 10, COLOR_GREEN, false);
        }
    }

    private void drawEmptyCard(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + CARD_H, 0x220A1020);
        screen.drawBorder(g, x, y, w, CARD_H, COLOR_BORDER_INNER);
        int cx = x + w / 2, cy = y + CARD_H / 2;
        g.fill(cx - 5, cy - 1, cx + 6, cy + 1, 0x44AAAAAA);
        g.fill(cx - 1, cy - 5, cx + 1, cy + 6, 0x44AAAAAA);
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private void drawDetailPanel(GuiGraphicsExtractor g, Font font,
                                 int mx, int my,
                                 int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);

        if (selectedAbility == null) {
            String hint = "Select an ability";
            int hw = Math.round(font.width(hint) * SMALL);
            screen.drawSmallAt(g, hint, x + w / 2 - hw / 2, y + h / 2, COLOR_LABEL);
            return;
        }

        Ability ab       = selectedAbility;
        boolean equipped = ab.getId().equals(ClientAbilityManager.getEquippedAbility());
        boolean hasBtn   = ab.getType() != Ability.Type.PASSIVE;
        int typeColor    = getTypeColor(ab.getType());
        int ix  = x + PAD;
        int iw  = w - PAD * 2;
        int btnH = SLH + 8;

        // ── Fixed bottom button (outside scroll — coords are always stable) ────
        // FIX: old inline button was inside the scissor/scroll region,
        // so stored coords drifted when detailScrollY > 0.
        if (hasBtn) {
            int bby = y + h - PAD - btnH;
            bottomBtnX = ix;  bottomBtnY = bby;
            bottomBtnW = iw;  bottomBtnH = btnH;
            bottomBtnHovered = screen.inB(mx, my, ix, bby, iw, btnH);

            if (equipped) {
                g.fill(ix, bby, ix + iw, bby + btnH,
                        bottomBtnHovered ? 0xFF2A0808 : 0xFF1A0808);
                screen.drawBorder(g, ix, bby, iw, btnH, COLOR_RED);
                String s  = "[X] Unequip Ability";
                int    sw = Math.round(font.width(s) * SMALL);
                screen.drawSmallAt(g, s, ix + iw / 2 - sw / 2, bby + (btnH - SLH) / 2, COLOR_RED);
            } else {
                g.fill(ix, bby, ix + iw, bby + btnH,
                        bottomBtnHovered ? 0xFF002A10 : 0xFF001A08);
                screen.drawBorder(g, ix, bby, iw, btnH, COLOR_GREEN);
                String s  = "[E] Equip Ability";
                int    sw = Math.round(font.width(s) * SMALL);
                screen.drawSmallAt(g, s, ix + iw / 2 - sw / 2, bby + (btnH - SLH) / 2, COLOR_GREEN);
            }
        }

        // ── Scrollable content ────────────────────────────────────────────────
        int scrollAreaH = h - PAD * 2 - (hasBtn ? btnH + PAD : 0);
        screen.sc(g, x + 1, y + PAD, w - 2, scrollAreaH);
        int cy = y + PAD + 2 - detailScrollY;

        // Large icon with copper corners
        int bigSz = 52;
        int iconX = x + w / 2 - bigSz / 2;
        g.fill(iconX - 2, cy - 2, iconX + bigSz + 2, cy + bigSz + 2, COLOR_PANEL_BG);
        screen.drawBorder(g, iconX - 2, cy - 2, bigSz + 4, bigSz + 4, COLOR_COPPER);
        int cs = 7;
        // top-left
        g.fill(iconX - 2,             cy - 2,              iconX - 2 + cs,    cy - 1,            COLOR_COPPER);
        g.fill(iconX - 2,             cy - 2,              iconX - 1,         cy - 2 + cs,       COLOR_COPPER);
        // top-right
        g.fill(iconX + bigSz - cs + 2, cy - 2,             iconX + bigSz + 2, cy - 1,            COLOR_COPPER);
        g.fill(iconX + bigSz + 1,     cy - 2,              iconX + bigSz + 2, cy - 2 + cs,       COLOR_COPPER);
        // bottom-left
        g.fill(iconX - 2,             cy + bigSz + 1,      iconX - 2 + cs,    cy + bigSz + 2,    COLOR_COPPER);
        g.fill(iconX - 2,             cy + bigSz - cs + 2, iconX - 1,         cy + bigSz + 2,    COLOR_COPPER);
        // bottom-right
        g.fill(iconX + bigSz - cs + 2, cy + bigSz + 1,    iconX + bigSz + 2, cy + bigSz + 2,    COLOR_COPPER);
        g.fill(iconX + bigSz + 1,     cy + bigSz - cs + 2, iconX + bigSz + 2, cy + bigSz + 2,   COLOR_COPPER);
        drawIcon(g, ab.getIcon(), iconX, cy, bigSz);
        cy += bigSz + PAD;

        // Name — 1.5× scale, centered
        g.pose().pushMatrix();
        g.pose().scale(1.5f, 1.5f);
        String name   = ab.getDisplayName();
        int    nameScX = (int)((x + w / 2f) / 1.5f - font.width(name) / 2f);
        g.text(font, Component.literal(name), nameScX, (int)(cy / 1.5f), COLOR_VALUE, true);
        g.pose().popMatrix();
        cy += 14;

        // Type badge — centered
        int    badgeH  = SLH + 4;
        String typeLbl = ab.getType().name();
        int    badgeW  = Math.round(font.width(typeLbl) * SMALL) + 10;
        int    badgeX  = x + w / 2 - badgeW / 2;
        g.fill(badgeX, cy, badgeX + badgeW, cy + badgeH, 0x44000000);
        screen.drawBorder(g, badgeX, cy, badgeW, badgeH, typeColor);
        screen.drawSmallAt(g, typeLbl, badgeX + 5, cy + 2, typeColor);
        cy += badgeH + PAD;

        // Equipped indicator — text only; the button is at the bottom
        // FIX: old code tried to put "✓ Equipped" + inline button on one line
        // in a narrow panel, causing them to overlap.
        if (equipped) {
            int ckW = font.width("✓");
            g.text(font, Component.literal("✓"), ix, cy, COLOR_GREEN, false);
            screen.drawSmallAt(g, "Equipped", ix + ckW + 2, cy + 1, COLOR_GREEN);
        }
        cy += SLH + PAD;

        // Separator
        g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR);
        cy += PAD;

        // Description
        cy = screen.drawWrappedSmall(g, ab.getDescription(), ix, cy, iw, COLOR_LABEL);
        cy += PAD;

        // Diamond separator
        drawDiamondSep(g, font, ix, iw, x + w / 2, cy);
        cy += PAD + 2;

        // Stat rows — FIX: old code used g.text() (full scale) for icons and
        // drawSmallAt for label/value, causing vertical misalignment.
        // Now everything in the row uses SMALL scale.
        String cdStr = ab.getCooldownTicks() > 0
                ? (ab.getCooldownTicks() / 20) + "s" : "None";
        cy = drawStatRow(g, font, ix, cy, iw, "⏱", "Cooldown:", cdStr);
        cy = drawStatRow(g, font, ix, cy, iw, "◈", "Source:",   ab.getSourceDetail());
        cy = drawStatRow(g, font, ix, cy, iw, "✦", "Keybind:",  "[Z]");

        // Flavour text
        if (ab.getFlavourText() != null && !ab.getFlavourText().isEmpty()) {
            g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR);
            cy += PAD;
            drawDiamondSep(g, font, ix, iw, x + w / 2, cy);
            cy += PAD + 2;
            screen.drawWrappedSmall(g,
                    "\"" + ab.getFlavourText() + "\"", ix, cy, iw, COLOR_LABEL);
        }

        screen.esc(g);
    }

    private void drawDiamondSep(GuiGraphicsExtractor g, Font font,
                                int ix, int iw, int mid, int cy) {
        g.fill(ix,      cy, mid - 5,   cy + 1, COLOR_SEPARATOR);
        g.fill(mid + 5, cy, ix + iw,   cy + 1, COLOR_SEPARATOR);
        // FIX: was g.text() at full scale — now SMALL so it matches row height
        screen.drawSmallAt(g, "◆", mid - 3, cy - SLH / 2, COLOR_COPPER);
    }

    private int drawStatRow(GuiGraphicsExtractor g, Font font,
                            int ix, int y, int iw,
                            String icon, String label, String value) {
        int icoW = Math.round(font.width(icon)  * SMALL) + 2;
        screen.drawSmallAt(g, icon,  ix,        y, COLOR_COPPER);
        screen.drawSmallAt(g, label, ix + icoW, y, COLOR_LABEL);

        // All values start at a fixed x so rows stay visually aligned
        int valueX = ix + 55;
        int valueW = ix + iw - valueX;

        // Word-wrap the value within its column
        String[] words = value.split(" ");
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (Math.round(font.width(test) * SMALL) > valueW && !line.isEmpty()) {
                screen.drawSmallAt(g, line.toString(), valueX, cy, COLOR_VALUE);
                cy += SLH + 1;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            screen.drawSmallAt(g, line.toString(), valueX, cy, COLOR_VALUE);
            cy += SLH + 1;
        }
        return cy + 4; // padding after row
    }

    // ── Icon rendering ────────────────────────────────────────────────────────

    private void drawIcon(GuiGraphicsExtractor g, Identifier icon, int x, int y, int size) {
        if (icon != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, size, size, size, size);
        } else {
            g.fill(x, y, x + size, y + size, 0x44AAAAAA);
        }
    }

    // ── Input — mouse ─────────────────────────────────────────────────────────

    @Override
    public void mouseClicked(int mx, int my) {
        int x     = screen.contentX;
        int w     = screen.contentW;
        int y     = screen.contentY;
        // FIX: colsW matches draw() exactly — no stray - PAD
        int colsW = w - w * DETAIL_PCT / 100;
        int colW  = colsW / 4;

        // Card selection
        Ability.Type[] types = Ability.Type.values();
        for (int i = 0; i < types.length; i++) {
            int colX     = x + i * colW;
            int cardsTop = y + PAD + COL_HDR_H + 3;
            int cardW    = colW - PAD * 2 - 2;
            int cardX    = colX + PAD + 1;
            int cy       = cardsTop - colScroll[i];

            for (Ability ab : getUnlockedOfType(types[i])) {
                if (screen.inB(mx, my, cardX, cy, cardW, CARD_H)) {
                    selectedAbility = ab;
                    detailScrollY   = 0; // reset scroll when switching ability
                    screen.click();
                    return;
                }
                cy += CARD_H + CARD_PAD;
            }
        }

        // Bottom equip / unequip button
        if (selectedAbility != null && selectedAbility.getType() != Ability.Type.PASSIVE) {
            if (screen.inB(mx, my, bottomBtnX, bottomBtnY, bottomBtnW, bottomBtnH)) {
                toggleEquip();
                return;
            }
        }
    }

    // ── Input — keyboard ─────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key) {
        // X key (GLFW 88) = equip / unequip the selected ability
        if (key == 88
                && selectedAbility != null
                && selectedAbility.getType() != Ability.Type.PASSIVE) {
            toggleEquip();
            return true;
        }
        return false;
    }


    // ── Shared equip/unequip logic ────────────────────────────────────────────

    private void toggleEquip() {
        boolean eq = selectedAbility.getId()
                .equals(ClientAbilityManager.getEquippedAbility());
        // null payload = unequip; ability id = equip
        ClientPlayNetworking.send(
                new EquipAbilityPayload(eq ? null : selectedAbility.getId()));
        screen.click();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Ability> getUnlockedOfType(Ability.Type type) {
        return AbilityRegistry.all().stream()
                .filter(a -> a.getType() == type)
                .filter(a -> a.isDefault() || ClientAbilityManager.hasAbility(a.getId()))
                .toList();
    }

    private List<String> wrapCard(Font font, String text,
                                  int maxW, float scale, int maxLines) {
        List<String> lines   = new ArrayList<>();
        String[]     words   = text.split(" ");
        StringBuilder cur    = new StringBuilder();
        int           wIdx   = 0;

        while (wIdx < words.length) {
            String test = cur.isEmpty() ? words[wIdx] : cur + " " + words[wIdx];
            if (Math.round(font.width(test) * scale) > maxW && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur.setLength(0);
                if (lines.size() >= maxLines - 1) {
                    // Last line — join all remaining words
                    StringBuilder rest = new StringBuilder();
                    while (wIdx < words.length) {
                        if (rest.length() > 0) rest.append(" ");
                        rest.append(words[wIdx++]);
                    }
                    cur = rest;
                    break;
                }
            } else {
                cur = new StringBuilder(test);
                wIdx++;
            }
        }
        if (!cur.isEmpty()) {
            // Truncate if it doesn't fit
            String last = cur.toString();
            while (Math.round(font.width(last) * scale) > maxW && last.length() > 1)
                last = last.substring(0, last.length() - 1);
            if (!last.equals(cur.toString())) last += "..";
            lines.add(last);
        }
        if (lines.isEmpty()) lines.add(text.isEmpty() ? "" : text);
        return lines;
    }

    private int getTypeColor(Ability.Type type) {
        return switch (type) {
            case PASSIVE   -> COLOR_PASSIVE;
            case ACTIVE    -> COLOR_ACTIVE;
            case CHANNELED -> COLOR_CHANNELED;
            case TOGGLE    -> COLOR_TOGGLE;
        };
    }

    private String getTypeIcon(Ability.Type type) {
        return switch (type) {
            case PASSIVE   -> "◈";
            case ACTIVE    -> "⚔";
            case CHANNELED -> "◆";
            case TOGGLE    -> "◎";
        };
    }

    // ── Constants forwarded from BaseCharacterScreen ───────────────────────────
    private static final int   COLOR_SEPARATOR    = BaseCharacterScreen.COLOR_SEPARATOR;
    private static final int   COLOR_LABEL        = BaseCharacterScreen.COLOR_LABEL;
    private static final int   COLOR_VALUE        = BaseCharacterScreen.COLOR_VALUE;
    private static final int   COLOR_ACCENT       = BaseCharacterScreen.COLOR_ACCENT;
    private static final int   COLOR_BORDER_INNER = BaseCharacterScreen.COLOR_BORDER_INNER;
    private static final int   COLOR_PANEL_BG     = BaseCharacterScreen.COLOR_PANEL_BG;
    private static final int   COLOR_PANEL_BG_ALT = BaseCharacterScreen.COLOR_PANEL_BG_ALT;
    private static final int   COLOR_COPPER       = BaseCharacterScreen.COLOR_COPPER;
    private static final int   COLOR_GREEN        = BaseCharacterScreen.COLOR_GREEN;
    private static final int   COLOR_RED          = BaseCharacterScreen.COLOR_RED;
    private static final int   PAD                = BaseCharacterScreen.PAD;
    private static final int   SLH                = BaseCharacterScreen.SLH;
    private static final float SMALL              = BaseCharacterScreen.SMALL;
    private static final float TINY               = BaseCharacterScreen.TINY;
    private static final int   TLH                = BaseCharacterScreen.TLH;
}