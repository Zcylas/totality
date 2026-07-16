package zcylas.totality.screen.classes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.screen.ancestry.BaseAncestryScreen;

import java.util.List;

public class ClassSelectionScreen extends BaseAncestryScreen {

    private ClassCategory         selCat = ClassCategory.MARTIAL;
    private ClassData             selCls = null;
    private ClassData             hovCls = null;
    private List<ClassCategory>   cats;
    private List<ClassData>       classes;

    public ClassSelectionScreen() {
        super(Component.literal("Choose Your Class"));
    }

    @Override
    protected void init() {
        super.init();
        cats    = List.of(ClassCategory.values());
        classes = ClassRegistry.getByCategory(selCat);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        hovCls = null;
        drawHeader(g, "Choose Your Class",
                "Step 1: Browse by role and choose a Class", null);
        drawCatPanel(g, mx, my);
        drawListPanel(g, mx, my);
        drawDetailPanel(g, mx, my);
        drawBottomBar(g, mx, my, false, true, selCls != null, "NEXT");
    }

    private void drawCatPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 0, y = top, w = CAT_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "CLASS ROLE");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ry = y + HDR_H + 1 - scrollCat;
        for (ClassCategory cat : cats) {
            boolean sel = cat == selCat;
            boolean hov = inB(mx, my, x + 3, ry, w - 6, ROW_H);
            if (sel)       g.fill(x + 3, ry, x + w - 3, ry + ROW_H, COLOR_ROW_SEL);
            else if (hov)  g.fill(x + 3, ry, x + w - 3, ry + ROW_H, COLOR_ROW_HOV);
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

    private void drawListPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = listX, y = top, w = LIST_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "CLASSES");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ry = y + HDR_H + 1 - scrollList;
        for (ClassData cls : classes) {
            if (ry + ROW_H > y + HDR_H && ry < bot) {
                boolean sel = cls == selCls;
                boolean hov = inB(mx, my, x + 3, ry, w - 6, ROW_H);
                if (hov) hovCls = cls;
                drawRow(g, x, ry, w, cls.displayName(), null, sel, hov, false);
            }
            ry += ROW_H;
        }
        esc(g);
    }

    private void drawDetailPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = detX, y = top, w = detW, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "CLASS DETAILS");

        ClassData cls = selCls != null ? selCls : hovCls;
        if (cls == null) {
            drawSmallAt(g, "Hover or select a class",
                    x + w / 2 - Math.round(font.width("Hover or select a class") * SMALL) / 2,
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

        g.text(font, Component.literal(cls.displayName()), ix, cy, COLOR_ACCENT, false);
        cy += NLH + 2;
        drawSmallAt(g, "[ " + cls.category().getDisplayName() + " ]", ix, cy, COLOR_COPPER);
        cy += SLH + 6;

        cy = drawSection(g, ix, cy, iw, "DESCRIPTION", COLOR_COPPER);
        cy = drawSmallWrap(g, cls.description(), ix, cy, iw, COLOR_LABEL);
        cy += 5;

        cy = drawSection(g, ix, cy, iw, "RESOURCE DICE", COLOR_COPPER);
        drawSmallAt(g, "HP      " + cls.hpDie().name(),      ix, cy, COLOR_VALUE); cy += SLH + 2;
        drawSmallAt(g, "Stamina " + cls.staminaDie().name(),  ix, cy, COLOR_VALUE); cy += SLH + 2;
        drawSmallAt(g, "Mana    " + cls.manaDie().name(),     ix, cy, COLOR_VALUE); cy += SLH + 6;

        cy = drawSection(g, ix, cy, iw, "SAVING THROWS", COLOR_COPPER);
        String saves = cls.savingThrowProficiencies().stream()
                .map(s -> s.getDisplayName()).reduce((a, b) -> a + ", " + b).orElse("—");
        cy = drawSmallWrap(g, saves, ix, cy, iw, COLOR_VALUE);
        cy += 4;

        cy = drawSection(g, ix, cy, iw, "ARMOR", COLOR_COPPER);
        String armor = cls.armorProficiencies().isEmpty() ? "None"
                : cls.armorProficiencies().stream()
                .map(a -> a.name().charAt(0) + a.name().substring(1).toLowerCase())
                .reduce((a, b) -> a + ", " + b).orElse("—");
        cy = drawSmallWrap(g, armor, ix, cy, iw, COLOR_VALUE);
        cy += 4;

        if (cls.spellcastingAbility() != null) {
            cy = drawSection(g, ix, cy, iw, "SPELLCASTING", COLOR_COPPER);
            drawSmallAt(g, cls.spellcastingAbility().getDisplayName(), ix, cy, COLOR_VALUE);
            cy += SLH + 4;
        }

        List<SubclassData> subs = SubclassRegistry.getByClass(cls.id());
        if (!subs.isEmpty()) {
            cy = drawSection(g, ix, cy, iw, "SUBCLASSES", COLOR_COPPER);
            for (SubclassData sub : subs) {
                drawSmallAt(g, "◆ " + sub.displayName(), ix, cy, COLOR_VALUE);
                cy += SLH + 2;
            }
        }
        esc(g);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        int ry = top + HDR_H + 1 - scrollCat;
        for (ClassCategory cat : cats) {
            if (inB(mx, my, 3, ry, CAT_W - 6, ROW_H)) {
                selCat = cat;
                classes = ClassRegistry.getByCategory(cat);
                selCls = null; scrollList = 0; scrollDet = 0; click();
                return true;
            }
            ry += ROW_H;
        }

        ry = top + HDR_H + 1 - scrollList;
        for (ClassData cls : classes) {
            if (inB(mx, my, listX + 3, ry, LIST_W - 6, ROW_H)) {
                selCls = cls; scrollDet = 0; click(); return true;
            }
            ry += ROW_H;
        }

        if (selCls != null && isNext(mx, my)) {
            click();
            List<SubclassData> subs = SubclassRegistry.getByClass(selCls.id());
            // Only show SubclassSelectionScreen during initial selection for classes
            // that unlock their subclass at level 1 (Warlock, Cleric, Sorcerer).
            // Classes that unlock at level 2+ (Wizard) or 3 (Barbarian, Monk) skip
            // the subclass screen here — it opens automatically via ClassLevelUpRegistry
            // when the player reaches the unlock level.
            boolean showSubclassNow = selCls.subclassUnlockClassLevel() <= 1 && !subs.isEmpty();
            Minecraft.getInstance().gui.setScreen(showSubclassNow
                    ? new SubclassSelectionScreen(selCls)
                    : new ConfirmClassScreen(selCls, null, null));
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
            int max = Math.max(0, classes.size() * ROW_H - (ph - HDR_H));
            scrollList = Math.clamp((int)(scrollList - v * 10), 0, max); return true;
        }
        if (inB(x, y, detX, top, detW, ph)) {
            scrollDet = Math.max(0, (int)(scrollDet - v * 10)); return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
}