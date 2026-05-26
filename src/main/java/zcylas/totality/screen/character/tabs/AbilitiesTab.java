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
import zcylas.totality.networking.ability.FavoriteAbilityPayload;
import zcylas.totality.screen.character.BaseCharacterScreen;
import zcylas.totality.screen.character.CharacterScreen;

import java.util.ArrayList;
import java.util.List;

public class AbilitiesTab extends CharacterScreenTab {

    // ── Filter ────────────────────────────────────────────────────────────────
    private enum Filter {
        ALL      ("All",            "≡"),
        PASSIVE  ("Passive Traits", "❋"),
        ACTIVE   ("Active Powers",  "⚔"),
        CHANNELED("Channeled",      "◆"),
        TOGGLE   ("Toggle",         "⊙"),
        FAVORITES("Favorites",      "★");

        final String label, icon;
        Filter(String label, String icon) { this.label = label; this.icon = icon; }
    }

    // ── Type colors ───────────────────────────────────────────────────────────
    private static final int COLOR_PASSIVE   = 0xFF44AA44;
    private static final int COLOR_ACTIVE    = 0xFFCC4444;
    private static final int COLOR_CHANNELED = 0xFFAA44CC;
    private static final int COLOR_TOGGLE    = 0xFF44CCAA;
    private static final int COLOR_FAV       = 0xFFFFCC00;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int FILTER_W       = 120;
    private static final int FAV_STRIP_H    = 58;
    private static final int DETAIL_PCT     = 35;
    private static final int CARD_H         = 70;
    private static final int CARD_GAP       = 4;
    private static final int ICON_SIZE      = 20;
    private static final int STAR_SZ        = 12;
    private static final int FILTER_ROW_H   = 26;
    private static final int FAV_ICON_SZ    = 20;
    private static final int FAV_CENTER_SZ  = 24;
    private static final int FAV_GAP        = 8;
    private static final int MAX_FAV_VIS    = 5;
    public  static final int MAX_FAVORITES  = 8;
    private static final int CARD_ICON_SZ   = 24;
    // ── State ─────────────────────────────────────────────────────────────────
    private Filter            activeFilter   = Filter.ALL;
    private @Nullable Ability selectedAbility = null;
    private int               gridScrollY   = 0;
    private int               detailScrollY = 0;
    private int               filterScrollY = 0;
    private int               favWindowStart = 0;

    // Stable bounds written in draw(), read in mouseClicked()
    private int bottomBtnX, bottomBtnY, bottomBtnW, bottomBtnH;
    private int favBtnX,    favBtnY,    favBtnW,    favBtnH;
    private int dStarX,     dStarY; // star in detail panel
    private boolean bottomBtnHovered = false;

    private record StarBound(Ability ability, int x, int y) {}
    private final List<StarBound> starBounds = new ArrayList<>();

    public AbilitiesTab(CharacterScreen screen) { super(screen); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onOpen() {
        gridScrollY = detailScrollY = filterScrollY = favWindowStart = 0;
        starBounds.clear();
        Identifier eq = ClientAbilityManager.getEquippedAbility();
        selectedAbility = eq != null ? AbilityRegistry.get(eq) : null;
        if (selectedAbility == null) {
            List<Ability> all = getFiltered(Filter.ALL);
            if (!all.isEmpty()) selectedAbility = all.get(0);
        }
        // Center favorites window on equipped ability
        List<Identifier> favList = ClientAbilityManager.getFavorites();
        if (eq != null) {
            int idx = favList.indexOf(eq);
            if (idx >= 0) favWindowStart = Math.max(0, idx - MAX_FAV_VIS / 2);
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────
    @Override
    public void mouseScrolled(int mx, int my, double delta) {
        int x = screen.contentX, y = screen.contentY;
        int w = screen.contentW, h = screen.contentH;
        int mainH   = h - FAV_STRIP_H;
        int gdW     = w - FILTER_W;
        int detailX = x + w - gdW * DETAIL_PCT / 100;

        if (my >= y + mainH) return; // strip handled by arrows
        if (mx < x + FILTER_W) {
            int maxFS = Math.max(0, Filter.values().length * (FILTER_ROW_H + 2) - (mainH - HDR_H - PAD));
            filterScrollY = Math.max(0, Math.min(maxFS, filterScrollY - (int)(delta * 12)));
        } else if (mx >= detailX) {
            detailScrollY = Math.max(0, detailScrollY - (int)(delta * 12));
        } else {
            gridScrollY = Math.max(0, gridScrollY - (int)(delta * 12));
        }
    }

    // ── Main draw ─────────────────────────────────────────────────────────────
    @Override
    public void draw(GuiGraphicsExtractor g, Font font,
                     int mx, int my, int ba,
                     int x, int y, int w, int h) {
        starBounds.clear();
        bottomBtnHovered = false;

        int mainH   = h - FAV_STRIP_H;
        int gdW     = w - FILTER_W;
        int detailW = gdW * DETAIL_PCT / 100;
        int gridW   = gdW - detailW;
        int gridX   = x + FILTER_W;
        int detailX = gridX + gridW;

        drawFilterSidebar (g, font, mx, my, x,       y,        FILTER_W, mainH);
        drawGrid          (g, font, mx, my, gridX,   y,        gridW,    mainH);
        drawDetailPanel   (g, font, mx, my, detailX, y,        detailW,  mainH);
        drawFavoritesStrip(g, font, mx, my, x,       y + mainH, w,       FAV_STRIP_H);
    }

    // ── Filter sidebar ────────────────────────────────────────────────────────
    private void drawFilterSidebar(GuiGraphicsExtractor g, Font font,
                                   int mx, int my, int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        int hdrH = HDR_H + PAD;
        screen.drawPanelHdr(g, x, y + PAD, w, "FILTER");

        screen.sc(g, x + 1, y + hdrH, w - 2, h - hdrH - 1);

        int fy = y + hdrH - filterScrollY;
        for (Filter f : Filter.values()) {
            boolean active = f == activeFilter;
            boolean hov    = screen.inB(mx, my, x + PAD, fy, w - PAD * 2, FILTER_ROW_H);
            int accent     = f == Filter.FAVORITES ? COLOR_FAV : getFilterColor(f);
            int col        = active ? COLOR_ACCENT : hov ? COLOR_VALUE : COLOR_LABEL;

            g.fill(x + PAD, fy, x + w - PAD, fy + FILTER_ROW_H,
                    active ? 0xCC001828 : hov ? 0x44001828 : 0);
            if (active) {
                g.fill(x + PAD, fy, x + PAD + 2, fy + FILTER_ROW_H, accent);
                screen.drawBorder(g, x + PAD, fy, w - PAD * 2, FILTER_ROW_H, accent);
            }

            int iy   = fy + (FILTER_ROW_H - SLH) / 2;
            int icoW = Math.round(font.width(f.icon) * SMALL) + 4;
            screen.drawSmallAt(g, f.icon, x + PAD * 2 + 4, iy, active ? accent : col);

            // Label SMALL
            screen.drawSmallAt(g, f.label, x + PAD * 2 + 4 + icoW, iy, col);

            fy += FILTER_ROW_H + 2;
        }
        screen.esc(g);
    }

    // ── Grid ──────────────────────────────────────────────────────────────────
    private void drawGrid(GuiGraphicsExtractor g, Font font,
                          int mx, int my, int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        List<Ability> abilities = getFiltered(activeFilter);
        int cols = 2, padX = PAD + 2;
        int colW = (w - padX * 2 - PAD) / cols;

        screen.sc(g, x + 1, y + 1, w - 2, h - 2);

        int cy = y + PAD - gridScrollY;
        for (int i = 0; i < abilities.size(); i += cols) {
            for (int col = 0; col < cols && i + col < abilities.size(); col++) {
                drawCard(g, font, mx, my, abilities.get(i + col),
                        x + padX + col * (colW + PAD), cy, colW);
            }
            cy += CARD_H + CARD_GAP;
        }

        if (abilities.isEmpty()) {
            String msg = activeFilter == Filter.FAVORITES
                    ? "No favorites — press [F] to star an ability"
                    : "No abilities of this type";
            int mw = Math.round(font.width(msg) * SMALL);
            screen.drawSmallAt(g, msg, x + w / 2 - mw / 2, y + h / 2 - SLH / 2, COLOR_LABEL);
        }

        int rows = (abilities.size() + cols - 1) / cols;
        gridScrollY = Math.min(gridScrollY, Math.max(0, rows * (CARD_H + CARD_GAP) - h + PAD * 2));
        screen.esc(g);
    }

    private void drawCard(GuiGraphicsExtractor g, Font font, int mx, int my,
                          Ability ab, int x, int y, int w) {
        boolean sel      = ab == selectedAbility;
        boolean equipped = ab.getId().equals(ClientAbilityManager.getEquippedAbility());
        boolean hov      = screen.inB(mx, my, x, y, w, CARD_H);
        boolean fav      = ClientAbilityManager.isFavorite(ab.getId());
        int typeColor    = getTypeColor(ab.getType());
        int borderColor  = sel ? COLOR_ACCENT : typeColor;

        g.fill(x, y, x + w, y + CARD_H,
                sel ? 0xCC001828 : hov ? 0x44001828 : COLOR_PANEL_BG_ALT);
        screen.drawBorder(g, x, y, w, CARD_H, borderColor);
        drawCorners(g, x, y, w, CARD_H, borderColor);

        // Icon box — full card height, square
        int iX = x + PAD;
        int iY = y + (CARD_H - CARD_ICON_SZ) / 2;
        drawIcon(g, ab.getIcon(), iX, iY, CARD_ICON_SZ);
        int tx = iX + CARD_ICON_SZ + PAD;
        int tw = w - (tx - x) - STAR_SZ - PAD;

        // Name — top
        int nameCy = y + PAD + 2;
        for (String line : wrapCard(font, ab.getDisplayName(), tw, SMALL, 2)) {
            screen.drawSmallAt(g, line, tx, nameCy, sel ? COLOR_ACCENT : COLOR_VALUE);
            nameCy += SLH + 1;
        }

        // Source — middle
        List<String> srcLines = wrapCard(font, ab.getSourceDetail(), tw, TINY, 2);
        int srcCy = y + CARD_H / 2 - (srcLines.size() * (TLH + 1)) / 2;
        for (String line : srcLines) {
            screen.drawTinyAt(g, line, tx, srcCy, COLOR_LABEL);
            srcCy += TLH + 1;
        }

        // Type badge — bottom of text area
        String typeLbl = ab.getType().name();
        int badgeH = TLH + 3;
        int badgeW = Math.round(font.width(typeLbl) * TINY) + 6;
        int by     = y + CARD_H - PAD - badgeH;
        g.fill(tx, by, tx + badgeW, by + badgeH, 0x55000000);
        screen.drawBorder(g, tx, by, badgeW, badgeH, typeColor);
        screen.drawTinyAt(g, typeLbl, tx + 3, by + 2, typeColor);

        // Rank placeholder — bottom-left
        int bottomRowY = y + CARD_H - PAD - TLH;
        screen.drawTinyAt(g, "I", x + PAD, bottomRowY, COLOR_ACCENT);

        // Star — top-right corner
        int rightEdge = x + w - PAD;
        int starW     = Math.round(font.width(fav ? "★" : "☆") * SMALL);
        int starX     = rightEdge - starW;
        int starY     = y + PAD;
        boolean starHov = screen.inB(mx, my, starX, starY, STAR_SZ, STAR_SZ);
        screen.drawSmallAt(g, fav ? "★" : "☆", starX, starY,
                fav ? COLOR_FAV : starHov ? 0xFFCCCC44 : COLOR_LABEL);
        starBounds.add(new StarBound(ab, starX, starY));

        // Equipped check — bottom-right corner
        if (equipped) {
            int checkW = Math.round(font.width("✓") * TINY);
            screen.drawTinyAt(g, "✓", rightEdge - checkW, bottomRowY, COLOR_GREEN);
        }
    }

    // ── Detail panel ──────────────────────────────────────────────────────────
    private void drawDetailPanel(GuiGraphicsExtractor g, Font font,
                                 int mx, int my, int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);

        if (selectedAbility == null) {
            String hint = "Select an ability";
            screen.drawSmallAt(g, hint,
                    x + w / 2 - Math.round(font.width(hint) * SMALL) / 2,
                    y + h / 2, COLOR_LABEL);
            return;
        }

        Ability ab       = selectedAbility;
        boolean equipped = ab.getId().equals(ClientAbilityManager.getEquippedAbility());
        boolean fav      = ClientAbilityManager.isFavorite(ab.getId());
        boolean hasEquip = ab.getType() != Ability.Type.PASSIVE;
        int typeColor    = getTypeColor(ab.getType());
        int ix  = x + PAD;
        int iw  = w - PAD * 2;
        int btnH = SLH + 8;

        // ── Fixed bottom buttons ──────────────────────────────────────────────
        if (hasEquip) {
            int bby = y + h - PAD - btnH;
            bottomBtnX = ix; bottomBtnY = bby; bottomBtnW = iw; bottomBtnH = btnH;
            bottomBtnHovered = screen.inB(mx, my, ix, bby, iw, btnH);
            if (equipped) {
                g.fill(ix, bby, ix + iw, bby + btnH, bottomBtnHovered ? 0xFF2A0808 : 0xFF1A0808);
                screen.drawBorder(g, ix, bby, iw, btnH, COLOR_RED);
                String s = "[X] Unequip";
                screen.drawSmallAt(g, s,
                        ix + iw / 2 - Math.round(font.width(s) * SMALL) / 2,
                        bby + (btnH - SLH) / 2, COLOR_RED);
            } else {
                g.fill(ix, bby, ix + iw, bby + btnH, bottomBtnHovered ? 0xFF002A10 : 0xFF001A08);
                screen.drawBorder(g, ix, bby, iw, btnH, COLOR_GREEN);
                String s = "[E] Equip";
                screen.drawSmallAt(g, s,
                        ix + iw / 2 - Math.round(font.width(s) * SMALL) / 2,
                        bby + (btnH - SLH) / 2, COLOR_GREEN);
            }
        }

        // Favorite button
        int fbY = y + h - PAD - btnH - PAD - btnH;
        favBtnX = ix; favBtnY = fbY; favBtnW = iw; favBtnH = btnH;
        boolean favHov = screen.inB(mx, my, ix, fbY, iw, btnH);
        g.fill(ix, fbY, ix + iw, fbY + btnH, favHov ? 0xFF1A1800 : 0xFF0A0800);
        screen.drawBorder(g, ix, fbY, iw, btnH, fav ? COLOR_FAV : COLOR_BORDER_INNER);
        String favStr = fav ? "★ Remove Favorite [F]" : "☆ Add to Favorites [F]";
        screen.drawSmallAt(g, favStr,
                ix + iw / 2 - Math.round(font.width(favStr) * SMALL) / 2,
                fbY + (btnH - SLH) / 2, fav ? COLOR_FAV : COLOR_LABEL);

        // ── Scrollable content ────────────────────────────────────────────────
        int btnArea = PAD + btnH + PAD + btnH + PAD;
        int scrollH = h - PAD - btnArea;
        screen.sc(g, x + 1, y + PAD, w - 2, scrollH);
        int cy = y + PAD + 2 - detailScrollY;

        // Icon
        int bigSz = 44;
        int iconX = x + w / 2 - bigSz / 2;
        g.fill(iconX - 2, cy - 2, iconX + bigSz + 2, cy + bigSz + 2, COLOR_PANEL_BG);
        screen.drawBorder(g, iconX - 2, cy - 2, bigSz + 4, bigSz + 4, COLOR_COPPER);
        // Copper corner dots
        g.fill(iconX - 2,      cy - 2,          iconX,          cy,          COLOR_COPPER_BRIGHT);
        g.fill(iconX + bigSz,  cy - 2,          iconX + bigSz + 2, cy,       COLOR_COPPER_BRIGHT);
        g.fill(iconX - 2,      cy + bigSz,      iconX,          cy + bigSz + 2, COLOR_COPPER_BRIGHT);
        g.fill(iconX + bigSz,  cy + bigSz,      iconX + bigSz + 2, cy + bigSz + 2, COLOR_COPPER_BRIGHT);
        drawIcon(g, ab.getIcon(), iconX, cy, bigSz);

        // Star (top-right, inside scroll area but near top so always visible)
        dStarX = x + w - PAD - STAR_SZ;
        dStarY = cy;
        boolean dStarHov = screen.inB(mx, my, dStarX, dStarY + detailScrollY, STAR_SZ, STAR_SZ);
        screen.drawSmallAt(g, fav ? "★" : "☆", dStarX, dStarY,
                fav ? COLOR_FAV : dStarHov ? 0xFFCCCC44 : COLOR_LABEL);
        cy += bigSz + PAD;

        // Name 1.5×
        g.pose().pushMatrix();
        g.pose().scale(1.5f, 1.5f);
        String name = ab.getDisplayName();
        List<String> nameLines = screen.wrapText(name, Math.round((iw - PAD) / 1.5f));
        int nameCy = cy;
        for (String line : nameLines) {
            g.text(font, Component.literal(line),
                    (int)((x + w / 2f) / 1.5f - font.width(line) / 2f),
                    (int)(nameCy / 1.5f), COLOR_VALUE, true);
            nameCy += Math.round(NLH * 1.5f);
        }
        g.pose().popMatrix();
        cy = nameCy;

        // Source + type badges
        int badgeH  = SLH + 4;
        String srcLbl  = ab.getSourceDetail();
        String typeLbl = ab.getType().name();
        int typW  = Math.round(font.width(typeLbl) * SMALL) + 10;
        int maxSrc = iw - typW - PAD * 3;
        String srcDisplay = screen.truncate(srcLbl, Math.round(maxSrc / SMALL) - 10);
        int srcW  = Math.round(font.width(srcDisplay) * SMALL) + 10;
        int bStart = x + w / 2 - (srcW + PAD + typW) / 2;
        g.fill(bStart,          cy, bStart + srcW,          cy + badgeH, 0x44000000);
        screen.drawBorder(g,  bStart, cy, srcW, badgeH, COLOR_COPPER);
        screen.drawSmallAt(g, srcDisplay, bStart + 5,        cy + 2, COLOR_COPPER);
        int typX = bStart + srcW + PAD;
        g.fill(typX,            cy, typX + typW,             cy + badgeH, 0x44000000);
        screen.drawBorder(g,  typX, cy, typW, badgeH, typeColor);
        screen.drawSmallAt(g, typeLbl, typX + 5,             cy + 2, typeColor);
        cy += badgeH + PAD;

        // Equipped + Favorite status line
        if (equipped || fav) {
            if (equipped) {
                g.text(font, Component.literal("✓ Equipped to Z"), ix, cy, COLOR_GREEN, false);
                cy += NLH;
            }
            if (fav) {
                g.text(font, Component.literal("★ Favorite"), ix, cy, COLOR_FAV, false);
                cy += NLH;
            }
        }

        // Description
        g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR); cy += PAD;
        cy = screen.drawWrappedSmall(g, ab.getDescription(), ix, cy, iw, COLOR_LABEL);
        cy += PAD;

        // Stat rows
        g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR); cy += PAD;
        String cdStr = ab.getCooldownTicks() > 0 ? (ab.getCooldownTicks() / 20) + "s" : "None";
        cy = drawStatRow(g, font, ix, cy, iw, "⏱", "Cooldown:", cdStr);
        cy = drawStatRow(g, font, ix, cy, iw, "◈", "Source:",   ab.getSourceDetail());
        if (hasEquip)
            cy = drawStatRow(g, font, ix, cy, iw, "Z", "Keybind:",
                    equipped ? "[Z] (Equipped)" : "[Z]");
        cy += PAD;

        // Evolution Path
        g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR); cy += PAD;
        screen.drawPanelHdr(g, ix, cy, iw, "EVOLUTION PATH");
        cy += HDR_H + 4;

        int nodeW   = 28;
        int nodeH   = 20;
        int nodeGap = (iw - nodeW * 3) / 2;
        String[] tiers = {"I", "II", "III"};
        for (int t = 0; t < 3; t++) {
            int nx = ix + t * (nodeW + nodeGap);
            boolean unlocked = (t == 0);
            int nc = unlocked ? COLOR_ACCENT : COLOR_BORDER_INNER;
            g.fill(nx, cy, nx + nodeW, cy + nodeH, unlocked ? 0xFF001828 : 0xFF050810);
            screen.drawBorder(g, nx, cy, nodeW, nodeH, nc);
            screen.drawSmallAt(g, tiers[t],
                    nx + nodeW / 2 - Math.round(font.width(tiers[t]) * SMALL) / 2,
                    cy + (nodeH - SLH) / 2, nc);
            if (t < 2)
                screen.drawTinyAt(g, "→", nx + nodeW + 2, cy + (nodeH - TLH) / 2, COLOR_BORDER_INNER);
        }
        cy += nodeH + 2;
        // Tier name labels
        screen.drawTinyAt(g, ab.getDisplayName(), ix, cy, COLOR_ACCENT);
        screen.drawTinyAt(g, "—", ix + nodeW + nodeGap, cy, COLOR_BORDER_INNER);
        screen.drawTinyAt(g, "—", ix + 2 * (nodeW + nodeGap), cy, COLOR_BORDER_INNER);
        cy += TLH + PAD;

        // Flavour
        if (ab.getFlavourText() != null && !ab.getFlavourText().isEmpty()) {
            g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR); cy += PAD;
            screen.drawWrappedSmall(g, "\"" + ab.getFlavourText() + "\"", ix, cy, iw, COLOR_LABEL);
        }

        screen.esc(g);
    }

    // ── Favorites strip ───────────────────────────────────────────────────────
    private void drawFavoritesStrip(GuiGraphicsExtractor g, Font font,
                                    int mx, int my, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COLOR_PANEL_BG);
        g.fill(x, y, x + w, y + 1, COLOR_BORDER);

        int hdrH = HDR_H + 4;
        screen.drawPanelHdr(g, x + PAD, y + 2, w - PAD * 2, "◈ FAVORITES RADIAL PREVIEW ◈");

        // Hints
        int hintY = y + h / 2 - SLH / 2 + 4;
        g.text(font, Component.literal("Z"), x + PAD * 2, hintY, COLOR_ACCENT, true);
        screen.drawSmallAt(g, "— Use Equipped",
                x + PAD * 2 + font.width("Z") + 3, hintY + 1, COLOR_LABEL);
        String altLbl = "ALT + Z — Open Favorites Radial";
        screen.drawSmallAt(g, altLbl,
                x + w - PAD * 2 - Math.round(font.width(altLbl) * SMALL), hintY + 1, COLOR_LABEL);

        List<Identifier> favIds  = ClientAbilityManager.getFavorites();
        Identifier       equipped = ClientAbilityManager.getEquippedAbility();

        if (favIds.isEmpty()) {
            String msg = "Star abilities with [F] to add them to your favorites";
            screen.drawSmallAt(g, msg,
                    x + w / 2 - Math.round(font.width(msg) * SMALL) / 2,
                    hintY, COLOR_LABEL);
            return;
        }

        // Clamp window
        int maxStart = Math.max(0, favIds.size() - MAX_FAV_VIS);
        favWindowStart = Math.max(0, Math.min(favWindowStart, maxStart));

        // Always center window on equipped ability
        if (equipped != null) {
            int eqIdx = favIds.indexOf(equipped);
            if (eqIdx >= 0)
                favWindowStart = Math.max(0, Math.min(eqIdx - MAX_FAV_VIS / 2, maxStart));
        }

        int end   = Math.min(favIds.size(), favWindowStart + MAX_FAV_VIS);
        int count = end - favWindowStart;

        // Total width of visible icons (center one bigger)
        int totalW = 0;
        for (int i = 0; i < count; i++) {
            boolean eqI = favIds.get(favWindowStart + i).equals(equipped);
            totalW += (eqI ? FAV_CENTER_SZ : FAV_ICON_SZ) + FAV_GAP;
        }
        totalW -= FAV_GAP;

        int iconAreaY = y + hdrH;
        int iconAreaH = h - hdrH;
        int clipX = x + 120;
        int clipW = w - 240;
        screen.sc(g, clipX, y, clipW, h);

        int ix = x + w / 2 - totalW / 2;
        for (int i = 0; i < count; i++) {
            int favIdx = favWindowStart + i;
            Identifier favId = favIds.get(favIdx);
            Ability ab = AbilityRegistry.get(favId);
            if (ab == null) { ix += FAV_ICON_SZ + FAV_GAP; continue; }

            boolean isEq     = favId.equals(equipped);
            boolean isCenter = isEq;
            int sz           = isCenter ? FAV_CENTER_SZ : FAV_ICON_SZ;
            boolean isSel    = ab == selectedAbility;
            boolean hov      = screen.inB(mx, my, ix, iconAreaY + (iconAreaH - sz) / 2, sz, sz);

            int iy = iconAreaY + (iconAreaH - sz) / 2;
            if (isEq)
                screen.drawBorder(g, ix - 2, iy - 2, sz + 4, sz + 4, COLOR_ACCENT);
            drawIcon(g, ab.getIcon(), ix, iy, sz);
            ix += sz + FAV_GAP;
        }
        screen.esc(g);

        // Arrows
        boolean hasPrev = favWindowStart > 0;
        boolean hasNext = favWindowStart + MAX_FAV_VIS < favIds.size();
        screen.drawSmallAt(g, "◄", clipX - 14, hintY,
                hasPrev ? COLOR_VALUE : COLOR_BORDER_INNER);
        screen.drawSmallAt(g, "►", clipX + clipW + 2, hintY,
                hasNext ? COLOR_VALUE : COLOR_BORDER_INNER);
    }

    // ── Mouse input ───────────────────────────────────────────────────────────
    @Override
    public void mouseClicked(int mx, int my) {
        int x = screen.contentX, y = screen.contentY;
        int w = screen.contentW, h = screen.contentH;
        int mainH   = h - FAV_STRIP_H;
        int gdW     = w - FILTER_W;
        int detailW = gdW * DETAIL_PCT / 100;
        int gridW   = gdW - detailW;
        int gridX   = x + FILTER_W;
        int detailX = gridX + gridW;

        // ── Favorites strip ───────────────────────────────────────────────────
        if (my >= y + mainH) {
            int clipX = x + 120, clipW = w - 240;
            int arrowY = y + mainH + FAV_STRIP_H / 2 - SLH / 2 + 4;

            if (screen.inB(mx, my, clipX - 18, arrowY - 2, 16, SLH + 4) && favWindowStart > 0) {
                favWindowStart--; screen.click(); return;
            }
            if (screen.inB(mx, my, clipX + clipW, arrowY - 2, 16, SLH + 4)
                    && favWindowStart + MAX_FAV_VIS < ClientAbilityManager.getFavorites().size()) {
                favWindowStart++; screen.click(); return;
            }

            List<Identifier> favIds = ClientAbilityManager.getFavorites();
            Identifier eqId = ClientAbilityManager.getEquippedAbility();
            int hdrH    = HDR_H + 4;
            int iaY     = y + mainH + hdrH;
            int iaH     = FAV_STRIP_H - hdrH;
            int end     = Math.min(favIds.size(), favWindowStart + MAX_FAV_VIS);
            int count   = end - favWindowStart;
            int totalW  = 0;
            for (int i = 0; i < count; i++) {
                boolean eqI = favIds.get(favWindowStart + i).equals(eqId);
                totalW += (eqI ? FAV_CENTER_SZ : FAV_ICON_SZ) + FAV_GAP;
            }
            totalW -= FAV_GAP;
            int ix = x + w / 2 - totalW / 2;
            for (int i = 0; i < count; i++) {
                Identifier favId = favIds.get(favWindowStart + i);
                boolean isEq = favId.equals(eqId);
                int sz = isEq ? FAV_CENTER_SZ : FAV_ICON_SZ;
                int iy = iaY + (iaH - sz) / 2;
                if (screen.inB(mx, my, ix, iy, sz, sz)) {
                    Ability ab = AbilityRegistry.get(favId);
                    if (ab != null) { selectedAbility = ab; screen.click(); return; }
                }
                ix += sz + FAV_GAP;
            }
            return;
        }

        // ── Filter sidebar ────────────────────────────────────────────────────
        if (screen.inB(mx, my, x, y, FILTER_W, mainH)) {
            int fy = y + HDR_H + PAD - filterScrollY;
            for (Filter f : Filter.values()) {
                if (screen.inB(mx, my, x + PAD, fy, FILTER_W - PAD * 2, FILTER_ROW_H)) {
                    if (activeFilter != f) {
                        activeFilter = f; gridScrollY = 0; selectedAbility = null;
                        screen.click();
                    }
                    return;
                }
                fy += FILTER_ROW_H + 2;
            }
            return;
        }

        // ── Grid ─────────────────────────────────────────────────────────────
        if (screen.inB(mx, my, gridX, y, gridW, mainH)) {
            for (StarBound sb : starBounds) {
                if (screen.inB(mx, my, sb.x(), sb.y(), STAR_SZ, STAR_SZ)) {
                    if (!ClientAbilityManager.isFavorite(sb.ability().getId())
                            && ClientAbilityManager.getFavorites().size() >= MAX_FAVORITES) return;
                    ClientPlayNetworking.send(new FavoriteAbilityPayload(sb.ability().getId()));
                    screen.click(); return;
                }
            }
            List<Ability> abilities = getFiltered(activeFilter);
            int cols = 2, padX = PAD + 2;
            int colW = (gridW - padX * 2 - PAD) / cols;
            int cy = y + PAD - gridScrollY;
            for (int i = 0; i < abilities.size(); i += cols) {
                for (int col = 0; col < cols && i + col < abilities.size(); col++) {
                    Ability ab = abilities.get(i + col);
                    int cx = gridX + padX + col * (colW + PAD);
                    if (screen.inB(mx, my, cx, cy, colW, CARD_H)) {
                        if (ab == selectedAbility && ab.getType() != Ability.Type.PASSIVE) toggleEquip();
                        else { selectedAbility = ab; detailScrollY = 0; screen.click(); }
                        return;
                    }
                }
                cy += CARD_H + CARD_GAP;
            }
            return;
        }

        // ── Detail panel ──────────────────────────────────────────────────────
        if (screen.inB(mx, my, detailX, y, detailW, mainH) && selectedAbility != null) {
            // Star click (adjust for scroll)
            if (screen.inB(mx, my, dStarX, dStarY + detailScrollY, STAR_SZ, STAR_SZ)) {
                ClientPlayNetworking.send(new FavoriteAbilityPayload(selectedAbility.getId()));
                screen.click(); return;
            }
            if (screen.inB(mx, my, favBtnX, favBtnY, favBtnW, favBtnH)) {
                if (!ClientAbilityManager.isFavorite(selectedAbility.getId())
                        && ClientAbilityManager.getFavorites().size() >= MAX_FAVORITES) return;
                ClientPlayNetworking.send(new FavoriteAbilityPayload(selectedAbility.getId()));
                screen.click(); return;
            }
            if (selectedAbility.getType() != Ability.Type.PASSIVE
                    && screen.inB(mx, my, bottomBtnX, bottomBtnY, bottomBtnW, bottomBtnH)) {
                toggleEquip(); return;
            }
        }
    }

    // ── Keyboard input ────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int key) {
        if (selectedAbility == null) return false;
        if (key == 70) { // F = favorite
            if (!ClientAbilityManager.isFavorite(selectedAbility.getId())
                    && ClientAbilityManager.getFavorites().size() >= MAX_FAVORITES) return true;
            ClientPlayNetworking.send(new FavoriteAbilityPayload(selectedAbility.getId()));
            screen.click(); return true;
        }
        if (selectedAbility.getType() == Ability.Type.PASSIVE) return false;
        if (key == 69) { // E = equip
            if (!selectedAbility.getId().equals(ClientAbilityManager.getEquippedAbility())) {
                ClientPlayNetworking.send(new EquipAbilityPayload(selectedAbility.getId()));
                screen.click();
            }
            return true;
        }
        if (key == 88) { // X = unequip
            if (selectedAbility.getId().equals(ClientAbilityManager.getEquippedAbility())) {
                ClientPlayNetworking.send(new EquipAbilityPayload(null));
                screen.click();
            }
            return true;
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void toggleEquip() {
        boolean eq = selectedAbility.getId().equals(ClientAbilityManager.getEquippedAbility());
        ClientPlayNetworking.send(new EquipAbilityPayload(eq ? null : selectedAbility.getId()));
        screen.click();
    }

    private List<Ability> getFiltered(Filter filter) {
        return AbilityRegistry.all().stream()
                .filter(a -> a.isDefault() || ClientAbilityManager.hasAbility(a.getId()))
                .filter(a -> switch (filter) {
                    case ALL       -> true;
                    case PASSIVE   -> a.getType() == Ability.Type.PASSIVE;
                    case ACTIVE    -> a.getType() == Ability.Type.ACTIVE;
                    case CHANNELED -> a.getType() == Ability.Type.CHANNELED;
                    case TOGGLE    -> a.getType() == Ability.Type.TOGGLE;
                    case FAVORITES -> ClientAbilityManager.isFavorite(a.getId());
                })
                .toList();
    }

    private int getFilterColor(Filter f) {
        return switch (f) {
            case PASSIVE   -> COLOR_PASSIVE;
            case ACTIVE    -> COLOR_ACTIVE;
            case CHANNELED -> COLOR_CHANNELED;
            case TOGGLE    -> COLOR_TOGGLE;
            default        -> COLOR_ACCENT;
        };
    }

    private int getTypeColor(Ability.Type type) {
        return switch (type) {
            case PASSIVE   -> COLOR_PASSIVE;
            case ACTIVE    -> COLOR_ACTIVE;
            case CHANNELED -> COLOR_CHANNELED;
            case TOGGLE    -> COLOR_TOGGLE;
        };
    }

    private void drawIcon(GuiGraphicsExtractor g, Identifier icon, int x, int y, int size) {
        if (icon != null)
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, size, size, size, size);
        else
            g.fill(x, y, x + size, y + size, 0x44AAAAAA);
    }

    private void drawCorners(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        int cs = 4;
        g.fill(x, y, x+cs, y+1, col); g.fill(x, y, x+1, y+cs, col);
        g.fill(x+w-cs, y, x+w, y+1, col); g.fill(x+w-1, y, x+w, y+cs, col);
        g.fill(x, y+h-1, x+cs, y+h, col); g.fill(x, y+h-cs, x+1, y+h, col);
        g.fill(x+w-cs, y+h-1, x+w, y+h, col); g.fill(x+w-1, y+h-cs, x+w, y+h, col);
    }

    private int drawStatRow(GuiGraphicsExtractor g, Font font,
                            int ix, int y, int iw, String icon, String label, String value) {
        int icoW = Math.round(font.width(icon) * SMALL) + 2;
        screen.drawSmallAt(g, icon,  ix,        y, COLOR_COPPER);
        screen.drawSmallAt(g, label, ix + icoW, y, COLOR_LABEL);
        int valueX = ix + 60, valueW = ix + iw - valueX;
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : value.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (Math.round(font.width(test) * SMALL) > valueW && !line.isEmpty()) {
                screen.drawSmallAt(g, line.toString(), valueX, cy, COLOR_VALUE);
                cy += SLH + 1; line = new StringBuilder(word);
            } else line = new StringBuilder(test);
        }
        if (!line.isEmpty()) {
            screen.drawSmallAt(g, line.toString(), valueX, cy, COLOR_VALUE); cy += SLH + 1;
        }
        return cy + 4;
    }

    private List<String> wrapCard(Font font, String text, int maxW, float scale, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        int wIdx = 0;
        while (wIdx < words.length) {
            String test = cur.isEmpty() ? words[wIdx] : cur + " " + words[wIdx];
            if (Math.round(font.width(test) * scale) > maxW && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur.setLength(0);
                if (lines.size() >= maxLines - 1) {
                    StringBuilder rest = new StringBuilder();
                    while (wIdx < words.length) {
                        if (!rest.isEmpty()) rest.append(" ");
                        rest.append(words[wIdx++]);
                    }
                    cur = rest; break;
                }
            } else { cur = new StringBuilder(test); wIdx++; }
        }
        if (!cur.isEmpty()) {
            String last = cur.toString();
            // Only truncate if the string itself doesn't fit
            if (Math.round(font.width(last) * scale) > maxW) {
                while (last.length() > 1 && Math.round(font.width(last + "..") * scale) > maxW)
                    last = last.substring(0, last.length() - 1);
                if (!last.equals(cur.toString())) last += "..";
            }
            lines.add(last);
        }
        if (lines.isEmpty()) lines.add(text.isEmpty() ? "" : text);
        return lines;
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int   COLOR_SEPARATOR    = BaseCharacterScreen.COLOR_SEPARATOR;
    private static final int   COLOR_LABEL        = BaseCharacterScreen.COLOR_LABEL;
    private static final int   COLOR_VALUE        = BaseCharacterScreen.COLOR_VALUE;
    private static final int   COLOR_ACCENT       = BaseCharacterScreen.COLOR_ACCENT;
    private static final int   COLOR_BORDER       = BaseCharacterScreen.COLOR_BORDER;
    private static final int   COLOR_BORDER_INNER = BaseCharacterScreen.COLOR_BORDER_INNER;
    private static final int   COLOR_PANEL_BG     = BaseCharacterScreen.COLOR_PANEL_BG;
    private static final int   COLOR_PANEL_BG_ALT = BaseCharacterScreen.COLOR_PANEL_BG_ALT;
    private static final int   COLOR_COPPER       = BaseCharacterScreen.COLOR_COPPER;
    private static final int   COLOR_COPPER_BRIGHT = BaseCharacterScreen.COLOR_COPPER_BRIGHT;
    private static final int   COLOR_GREEN        = BaseCharacterScreen.COLOR_GREEN;
    private static final int   COLOR_RED          = BaseCharacterScreen.COLOR_RED;
    private static final int   PAD                = BaseCharacterScreen.PAD;
    private static final int   SLH                = BaseCharacterScreen.SLH;
    private static final int   TLH                = BaseCharacterScreen.TLH;
    private static final int   NLH                = BaseCharacterScreen.NLH;
    private static final int   HDR_H              = BaseCharacterScreen.HDR_H;
    private static final float SMALL              = BaseCharacterScreen.SMALL;
    private static final float TINY               = BaseCharacterScreen.TINY;
}