// screen/ancestry/SpeciesSelectionScreen.java
package zcylas.totality.screen.ancestry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.rpg.ancestry.*;

import java.util.List;

public class SpeciesSelectionScreen extends BaseAncestryScreen {

    private SpeciesCategory       selCat = SpeciesCategory.HUMANOID;
    private SpeciesData           selSp  = null;
    private SpeciesData           hovSp  = null;
    private List<SpeciesCategory> cats;
    private List<SpeciesData>     sps;

    public SpeciesSelectionScreen() {
        super(Component.literal("Choose Your Ancestry"));
    }

    @Override
    protected void init() {
        super.init();
        cats = SpeciesCategory.getUsedCategories();
        sps  = SpeciesRegistry.getForCategory(selCat);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        hovSp = null;

        drawHeader(g, "Choose Your Ancestry",
                "Step 1: Browse by Category and choose a Species", null);

        drawCatPanel(g, mx, my);
        drawListPanel(g, mx, my);
        drawDetailPanel(g, mx, my);
        drawBottomBar(g, mx, my, false, true, selSp != null, "NEXT");
    }

    // ── Category panel ────────────────────────────────────────────────────────

    private void drawCatPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 0, y = top, w = CAT_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "BROWSE CATEGORY");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ry = y + HDR_H + 1 - scrollCat;

        for (SpeciesCategory cat : cats) {
            boolean sel = cat == selCat;
            boolean hov = inB(mx, my, x + 3, ry, w - 6, ROW_H);
            if (sel)      g.fill(x + 3, ry, x + w - 3, ry + ROW_H, COLOR_ROW_SEL);
            else if (hov) g.fill(x + 3, ry, x + w - 3, ry + ROW_H, COLOR_ROW_HOV);
            if (sel) g.fill(x + 3, ry + 1, x + 5, ry + ROW_H - 1, COLOR_ACCENT);

            int tx = x + PAD + (sel ? 8 : 4);
            drawSmallAt(g, cat.getIcon() + "  " + cat.getDisplayName(),
                    tx, ry + (ROW_H - SLH) / 2,
                    sel ? COLOR_ACCENT : COLOR_VALUE);
            g.fill(x + PAD, ry + ROW_H - 1, x + w - PAD, ry + ROW_H, COLOR_SEPARATOR);
            ry += ROW_H;
        }
        esc(g);
    }

    // ── Species list panel ────────────────────────────────────────────────────

    private void drawListPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = listX, y = top, w = LIST_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "SPECIES");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ry = y + HDR_H + 1 - scrollList;

        for (SpeciesData sp : sps) {
            if (ry + ROW_H > y + HDR_H && ry < bot) {
                boolean sel = sp == selSp;
                boolean hov = inB(mx, my, x + 3, ry, w - 6, ROW_H);
                if (hov) hovSp = sp;
                drawRow(g, x, ry, w, sp.getDisplayName(), null, sel, hov, false);
            }
            ry += ROW_H;
        }
        esc(g);
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private void drawDetailPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = detX, y = top, w = detW, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "SPECIES DETAILS");

        SpeciesData sp = selSp != null ? selSp : hovSp;

        if (sp == null) {
            drawSmallAt(g, "Hover or select a species",
                    x + w / 2 - Math.round(font.width("Hover or select a species") * SMALL) / 2,
                    y + h / 2, COLOR_LABEL);
            return;
        }

        int splitX = x + w / 2;
        int innerY = y + HDR_H + 2;
        int innerH = h - HDR_H - 4;

        // Left: player preview
        var player = Minecraft.getInstance().player;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, x + 4, innerY, splitX - 2, y + h - 4,
                    40, 0.0f, mx, my, player);
        }

        // Right: info
        sc(g, splitX, innerY, w / 2 - 4, innerH);
        int ix = splitX + PAD, iw = w / 2 - PAD * 2;
        int cy = innerY + 4 - scrollDet;

        g.text(font, Component.literal(sp.getDisplayName()), ix, cy, COLOR_ACCENT, false);
        cy += NLH + 2;
        drawSmallAt(g, "[ " + sp.getCategory().getDisplayName() + " ]", ix, cy, COLOR_COPPER);
        cy += SLH + 6;

        cy = drawSection(g, ix, cy, iw, "DESCRIPTION", COLOR_COPPER);
        cy = drawSmallWrap(g, sp.getDescription(), ix, cy, iw, COLOR_LABEL);
        cy += 5;

        cy = drawSection(g, ix, cy, iw, "HEIGHT RANGE", COLOR_COPPER);
        drawSmallAt(g, String.format("%.2f - %.2f blocks",
                sp.getMinHeight(), sp.getMaxHeight()), ix, cy, COLOR_VALUE);
        cy += SLH + 6;

        List<OriginData> origins = OriginRegistry.getForSpecies(sp.getId());
        if (!origins.isEmpty()) {
            cy = drawSection(g, ix, cy, iw, "AVAILABLE ORIGINS", COLOR_COPPER);
            for (OriginData o : origins) {
                boolean locked = o.getUnlockState() == UnlockState.LOCKED;
                drawSmallAt(g, (locked ? "🔒 " : "◆ ") + o.getDisplayName(),
                        ix, cy, locked ? COLOR_LOCKED : COLOR_VALUE);
                cy += SLH + 1;
                drawTinyAt(g, "  " + o.getSourceTag().getDisplayName(),
                        ix, cy, o.getSourceTag().getColor());
                cy += TLH + 3;
            }
        }
        esc(g);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        // Category
        int ry = top + HDR_H + 1 - scrollCat;
        for (SpeciesCategory cat : cats) {
            if (inB(mx, my, 3, ry, CAT_W - 6, ROW_H)) {
                selCat = cat;
                sps = SpeciesRegistry.getForCategory(cat);
                selSp = null; scrollList = 0; scrollDet = 0; click();
                return true;
            }
            ry += ROW_H;
        }

        // Species
        ry = top + HDR_H + 1 - scrollList;
        for (SpeciesData sp : sps) {
            if (inB(mx, my, listX + 3, ry, LIST_W - 6, ROW_H)) {
                selSp = sp; scrollDet = 0; click(); return true;
            }
            ry += ROW_H;
        }

        // Next
        if (selSp != null && isNext(mx, my)) {
            click();
            var unlocked = OriginRegistry.getUnlockedForSpecies(selSp.getId());
            Minecraft.getInstance().setScreen(unlocked.isEmpty()
                    ? new ConfirmAncestryScreen(selSp, null)
                    : new OriginSelectionScreen(selSp));
            return true;
        }
        return super.mouseClicked(mouse, dc);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int x = (int) mx, y = (int) my;
        int ph = bot - top;
        if (inB(x, y, 0, top, CAT_W, ph)) {
            int max = Math.max(0, cats.size() * ROW_H - (ph - HDR_H));
            scrollCat = Math.clamp((int) (scrollCat - v * 10), 0, max);
            return true;
        }
        if (inB(x, y, listX, top, LIST_W, ph)) {
            int max = Math.max(0, sps.size() * ROW_H - (ph - HDR_H));
            scrollList = Math.clamp((int) (scrollList - v * 10), 0, max);
            return true;
        }
        if (inB(x, y, detX, top, detW, ph)) {
            scrollDet = Math.max(0, (int) (scrollDet - v * 10));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
}