package zcylas.totality.screen.classes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.classes.covenant.CovenantRegistry;
import zcylas.totality.screen.ancestry.BaseAncestryScreen;

import java.util.List;

public class SubclassSelectionScreen extends BaseAncestryScreen {

    private final ClassData          cls;
    private final List<SubclassData> subclasses;
    private SubclassData             selSub = null;
    private SubclassData             hovSub = null;

    public SubclassSelectionScreen(ClassData cls) {
        super(Component.literal("Choose Your Subclass"));
        this.cls       = cls;
        this.subclasses = SubclassRegistry.getByClass(cls.id());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        hovSub = null;
        String crumb = cls.category().getDisplayName() + "  ▶  " + cls.displayName();
        drawHeader(g, "Choose Your Subclass", "Step 2: Choose a Subclass", crumb);
        drawClassSummaryPanel(g, mx, my);
        drawSubclassListPanel(g, mx, my);
        drawDetailPanel(g, mx, my);
        drawBottomBar(g, mx, my, true, true, selSub != null, "NEXT");
    }

    private void drawClassSummaryPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 0, y = top, w = CAT_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "SELECTED CLASS");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int cy = y + HDR_H + 4 - scrollCat;

        g.text(font, Component.literal(cls.displayName()), x + PAD, cy, COLOR_ACCENT, false);
        cy += NLH + 2;
        drawSmallAt(g, "[ " + cls.category().getDisplayName() + " ]", x + PAD, cy, COLOR_COPPER);
        cy += SLH + 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;
        cy = drawSmallWrap(g, cls.description(), x + PAD, cy, w - PAD * 2, COLOR_LABEL);
        cy += 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;
        drawSmallAt(g, "HP      " + cls.hpDie().name(),     x + PAD, cy, COLOR_LABEL); cy += SLH + 2;
        drawSmallAt(g, "Stamina " + cls.staminaDie().name(), x + PAD, cy, COLOR_LABEL); cy += SLH + 2;
        drawSmallAt(g, "Mana    " + cls.manaDie().name(),    x + PAD, cy, COLOR_LABEL);
        esc(g);
    }

    private void drawSubclassListPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = listX, y = top, w = LIST_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "SUBCLASSES");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ry = y + HDR_H + 1 - scrollList;
        for (SubclassData sub : subclasses) {
            if (ry + ROW_H > y + HDR_H && ry < bot) {
                boolean sel = sub == selSub;
                boolean hov = inB(mx, my, x + 3, ry, w - 6, ROW_H);
                if (hov) hovSub = sub;
                drawRow(g, x, ry, w, sub.displayName(), null, sel, hov, false);
            }
            ry += ROW_H;
        }
        esc(g);
    }

    private void drawDetailPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = detX, y = top, w = detW, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "SUBCLASS DETAILS");

        SubclassData sub = selSub != null ? selSub : hovSub;
        if (sub == null) {
            drawSmallAt(g, "Select a subclass to view details",
                    x + w / 2 - Math.round(font.width("Select a subclass to view details") * SMALL) / 2,
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

        g.text(font, Component.literal(sub.displayName()), ix, cy, COLOR_ACCENT, false);
        cy += NLH + 6;

        cy = drawSection(g, ix, cy, iw, "DESCRIPTION", COLOR_COPPER);
        cy = drawSmallWrap(g, sub.description(), ix, cy, iw, COLOR_LABEL);
        cy += 5;

        if (!sub.availableCovenantCategories().isEmpty()) {
            cy = drawSection(g, ix, cy, iw, "COVENANT", COLOR_COPPER);
            drawSmallAt(g, "Choose a Patron on the next step.", ix, cy, COLOR_LABEL);
            cy += SLH + 2;
            for (var catId : sub.availableCovenantCategories()) {
                CovenantRegistry.getCategory(catId).ifPresent(cat -> {
                    // cy won't work in lambda, use a temp
                });
            }
        }
        esc(g);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        int ry = top + HDR_H + 1 - scrollList;
        for (SubclassData sub : subclasses) {
            if (inB(mx, my, listX + 3, ry, LIST_W - 6, ROW_H)) {
                selSub = sub; scrollDet = 0; click(); return true;
            }
            ry += ROW_H;
        }

        if (isBack(mx, my)) {
            click();
            Minecraft.getInstance().gui.setScreen(new ClassSelectionScreen());
            return true;
        }
        if (selSub != null && isNext(mx, my)) {
            click();
            List<Identifier> catIds = selSub.availableCovenantCategories();
            if (catIds.isEmpty()) {
                Minecraft.getInstance().gui.setScreen(new ConfirmClassScreen(cls, selSub, null));
            } else {
                CovenantRegistry.getCategory(catIds.get(0)).ifPresent(cat ->
                        Minecraft.getInstance().gui.setScreen(new CovenantSelectionScreen(cls, selSub, cat)));
            }
            return true;
        }
        return super.mouseClicked(mouse, dc);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int x = (int) mx, y = (int) my, ph = bot - top;
        if (inB(x, y, 0, top, CAT_W, ph)) {
            scrollCat = Math.max(0, (int)(scrollCat - v * 10)); return true;
        }
        if (inB(x, y, listX, top, LIST_W, ph)) {
            int max = Math.max(0, subclasses.size() * ROW_H - (ph - HDR_H));
            scrollList = Math.clamp((int)(scrollList - v * 10), 0, max); return true;
        }
        if (inB(x, y, detX, top, detW, ph)) {
            scrollDet = Math.max(0, (int)(scrollDet - v * 10)); return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
}