package zcylas.totality.screen.classes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.classes.covenant.CovenantCategory;
import zcylas.totality.api.rpg.classes.covenant.CovenantData;
import zcylas.totality.api.rpg.classes.covenant.CovenantRegistry;
import zcylas.totality.screen.ancestry.BaseAncestryScreen;

import java.util.List;

public class CovenantSelectionScreen extends BaseAncestryScreen {

    private final ClassData          cls;
    private final SubclassData       sub;
    private final CovenantCategory cat;
    private final List<CovenantData> covenants;
    private CovenantData             selCov = null;
    private CovenantData             hovCov = null;

    public CovenantSelectionScreen(ClassData cls, SubclassData sub, CovenantCategory cat) {
        super(Component.literal("Choose Your Patron"));
        this.cls       = cls;
        this.sub       = sub;
        this.cat       = cat;
        this.covenants = CovenantRegistry.getByCategory(cat.id());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        hovCov = null;
        String crumb = cls.displayName() + "  ▶  " + sub.displayName();
        drawHeader(g, "Choose Your Patron", "Step 3: Choose a Patron", crumb);
        drawSummaryPanel(g, mx, my);
        drawPatronListPanel(g, mx, my);
        drawDetailPanel(g, mx, my);
        drawBottomBar(g, mx, my, true, true, selCov != null, "NEXT");
    }

    private void drawSummaryPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 0, y = top, w = CAT_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "YOUR PACT");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int cy = y + HDR_H + 4 - scrollCat;
        drawSmallAt(g, "CLASS",    x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
        drawSmallAt(g, cls.displayName(), x + PAD + 3, cy, COLOR_ACCENT); cy += SLH + 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;
        drawSmallAt(g, "SUBCLASS", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
        drawSmallAt(g, sub.displayName(), x + PAD + 3, cy, COLOR_ACCENT); cy += SLH + 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;
        drawSmallAt(g, "TYPE",     x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
        drawSmallAt(g, cat.displayName(), x + PAD + 3, cy, COLOR_ACCENT);
        esc(g);
    }

    private void drawPatronListPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = listX, y = top, w = LIST_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "PATRONS");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ry = y + HDR_H + 1 - scrollList;
        for (CovenantData cov : covenants) {
            if (ry + ROW_H > y + HDR_H && ry < bot) {
                boolean sel = cov == selCov;
                boolean hov = inB(mx, my, x + 3, ry, w - 6, ROW_H);
                if (hov) hovCov = cov;
                drawRow(g, x, ry, w, cov.displayName(), null, sel, hov, false);
            }
            ry += ROW_H;
        }
        esc(g);
    }

    private void drawDetailPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = detX, y = top, w = detW, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "PATRON DETAILS");

        CovenantData cov = selCov != null ? selCov : hovCov;
        if (cov == null) {
            drawSmallAt(g, "Select a patron to view details",
                    x + w / 2 - Math.round(font.width("Select a patron to view details") * SMALL) / 2,
                    y + h / 2, COLOR_LABEL);
            return;
        }

        int splitX = x + w / 2, innerY = y + HDR_H + 2;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, x + 4, innerY, splitX - 2, y + h - 4, 40, 0f, mx, my, player);
        }

        sc(g, splitX, innerY, w / 2 - 4, h - HDR_H - 4);
        int ix = splitX + PAD, iw = w / 2 - PAD * 2;
        int cy = innerY + 4 - scrollDet;

        g.text(font, Component.literal(cov.displayName()), ix, cy, COLOR_ACCENT, false);
        cy += NLH + 6;
        cy = drawSection(g, ix, cy, iw, "DESCRIPTION", COLOR_COPPER);
        cy = drawSmallWrap(g, cov.description(), ix, cy, iw, COLOR_LABEL);
        esc(g);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        int ry = top + HDR_H + 1 - scrollList;
        for (CovenantData cov : covenants) {
            if (inB(mx, my, listX + 3, ry, LIST_W - 6, ROW_H)) {
                selCov = cov; scrollDet = 0; click(); return true;
            }
            ry += ROW_H;
        }

        if (isBack(mx, my)) {
            click();
            Minecraft.getInstance().gui.setScreen(new SubclassSelectionScreen(cls));
            return true;
        }
        if (selCov != null && isNext(mx, my)) {
            click();
            Minecraft.getInstance().gui.setScreen(new ConfirmClassScreen(cls, sub, selCov));
            return true;
        }
        return super.mouseClicked(mouse, dc);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int x = (int) mx, y = (int) my, ph = bot - top;
        if (inB(x, y, 0, top, CAT_W, ph))      { scrollCat  = Math.max(0, (int)(scrollCat  - v * 10)); return true; }
        if (inB(x, y, listX, top, LIST_W, ph)) { scrollList = Math.max(0, (int)(scrollList - v * 10)); return true; }
        if (inB(x, y, detX, top, detW, ph))    { scrollDet  = Math.max(0, (int)(scrollDet  - v * 10)); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }
}