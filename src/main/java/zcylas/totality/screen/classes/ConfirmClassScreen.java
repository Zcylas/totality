package zcylas.totality.screen.classes;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.classes.covenant.CovenantData;
import zcylas.totality.api.rpg.classes.covenant.CovenantRegistry;
import zcylas.totality.networking.classes.AddClassLevelPayload;
import zcylas.totality.networking.classes.SelectClassPayload;
import zcylas.totality.screen.ancestry.BaseAncestryScreen;

public class ConfirmClassScreen extends BaseAncestryScreen {

    private final ClassData          cls;
    private final @Nullable SubclassData  sub;
    private final @Nullable CovenantData  covenant;

    public ConfirmClassScreen(ClassData cls,
                              @Nullable SubclassData sub,
                              @Nullable CovenantData covenant) {
        super(Component.literal("Confirm Your Class"));
        this.cls      = cls;
        this.sub      = sub;
        this.covenant = covenant;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        String crumb = cls.displayName()
                + (sub != null ? "  ▶  " + sub.displayName() : "")
                + (covenant != null ? "  ▶  " + covenant.displayName() : "");
        drawHeader(g, "Confirm Your Class", "Final Step: Confirm Selection", crumb);
        drawSummaryPanel(g, mx, my);
        drawCenterPanel(g, mx, my);
        drawConfirmPanel(g, mx, my);
        drawBottomBar(g, mx, my, true, true, true, "CONFIRM");
    }

    private void drawSummaryPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 0, y = top, w = CAT_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "CLASS SUMMARY");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int cy = y + HDR_H + 4 - scrollCat;

        drawSmallAt(g, "CLASS", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
        drawSmallAt(g, cls.displayName(), x + PAD + 3, cy, COLOR_ACCENT); cy += SLH + 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;

        if (sub != null) {
            drawSmallAt(g, "SUBCLASS", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
            drawSmallAt(g, sub.displayName(), x + PAD + 3, cy, COLOR_ACCENT); cy += SLH + 5;
            g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;
        }
        if (covenant != null) {
            drawSmallAt(g, "PATRON", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
            drawSmallAt(g, covenant.displayName(), x + PAD + 3, cy, COLOR_ACCENT); cy += SLH + 5;
            g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;
        }

        cy += 4;
        drawSmallWrap(g, "⚠ This choice is permanent.", x + PAD, cy, w - PAD * 2, COLOR_COPPER_BRIGHT);
        esc(g);
    }

    private void drawCenterPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = listX, y = top, w = LIST_W, h = bot - top;
        g.fill(x, y, x + w, y + h, 0xFF080C12);
        drawCopperBorder(g, x, y, w, h);
        drawCorner(g, x + 1, y + 1, true, true);
        drawCorner(g, x + w - 1, y + 1, false, true);
        drawCorner(g, x + 1, y + h - 1, true, false);
        drawCorner(g, x + w - 1, y + h - 1, false, false);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, x + 10, y + 10, x + w - 10, y + h - 20, 60, 0f, mx, my, player);
        }
    }

    private void drawConfirmPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = detX, y = top, w = detW, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "CONFIRM CLASS");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ix = x + PAD, iw = w - PAD * 2;
        int cy = y + HDR_H + 4 - scrollDet;

        cy = drawSection(g, ix, cy, iw, "RESOURCE DICE", COLOR_COPPER);
        drawSmallAt(g, "HP      " + cls.hpDie().name(),      ix, cy, COLOR_VALUE); cy += SLH + 2;
        drawSmallAt(g, "Stamina " + cls.staminaDie().name(),  ix, cy, COLOR_VALUE); cy += SLH + 2;
        drawSmallAt(g, "Mana    " + cls.manaDie().name(),     ix, cy, COLOR_VALUE); cy += SLH + 6;

        cy = drawSection(g, ix, cy, iw, "SAVING THROWS", COLOR_COPPER);
        for (var s : cls.savingThrowProficiencies()) {
            drawSmallAt(g, "◆ " + s.getDisplayName(), ix, cy, COLOR_VALUE); cy += SLH + 2;
        }
        cy += 4;

        if (cls.spellcastingAbility() != null) {
            cy = drawSection(g, ix, cy, iw, "SPELLCASTING", COLOR_COPPER);
            drawSmallAt(g, cls.spellcastingAbility().getDisplayName(), ix, cy, COLOR_VALUE);
            cy += SLH + 6;
        }

        if (covenant != null) {
            cy = drawSection(g, ix, cy, iw, "PATRON", COLOR_COPPER);
            cy = drawSmallWrap(g, covenant.description(), ix, cy, iw, COLOR_LABEL);
        }
        esc(g);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        if (isBack(mx, my)) {
            click();
            if (covenant != null) {
                Minecraft.getInstance().gui.setScreen(
                        new CovenantSelectionScreen(cls, sub,
                                CovenantRegistry.getCategory(covenant.categoryId()).orElse(null) != null
                                        ? CovenantRegistry.getCategory(covenant.categoryId()).get() : null));
            } else if (sub != null) {
                Minecraft.getInstance().gui.setScreen(new SubclassSelectionScreen(cls));
            } else {
                Minecraft.getInstance().gui.setScreen(new ClassSelectionScreen());
            }
            return true;
        }
        if (isNext(mx, my)) {
            click();
            if (ClassScreenMode.IS_MULTICLASSING) {
                // Multiclass: spend a class point on this new class
                ClientPlayNetworking.send(new AddClassLevelPayload(cls.id().toString()));
                ClassScreenMode.IS_MULTICLASSING = false;
            } else {
                // First-time class selection
                ClientPlayNetworking.send(new SelectClassPayload(
                        cls.id().toString(),
                        sub != null ? sub.id().toString() : null,
                        covenant != null ? covenant.id().toString() : null));
            }
            onClose();
            return true;
        }
        return super.mouseClicked(mouse, dc);
    }
}