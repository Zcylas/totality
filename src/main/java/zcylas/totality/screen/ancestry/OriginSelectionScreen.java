// screen/ancestry/OriginSelectionScreen.java
package zcylas.totality.screen.ancestry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.rpg.ancestry.*;

import java.util.List;

public class OriginSelectionScreen extends BaseAncestryScreen {

    private final SpeciesData     species;
    private final List<OriginData> origins;
    private OriginData selOr = null;
    private OriginData hovOr = null;

    public OriginSelectionScreen(SpeciesData species) {
        super(Component.literal("Choose Your Ancestry"));
        this.species = species;
        this.origins = OriginRegistry.getForSpecies(species.getId());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        hovOr = null;

        String crumb = species.getCategory().getDisplayName()
                + "  ▶  " + species.getDisplayName();
        drawHeader(g, "Choose Your Ancestry", "Step 2: Choose an Origin", crumb);

        drawSpeciesPanel(g, mx, my);
        drawOriginListPanel(g, mx, my);
        drawDetailPanel(g, mx, my);
        drawBottomBar(g, mx, my, true, true, selOr != null, "NEXT");
    }

    // ── Left: selected species summary ────────────────────────────────────────

    private void drawSpeciesPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 0, y = top, w = CAT_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "SELECTED SPECIES");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int cy = y + HDR_H + 4 - scrollCat;

        g.text(font, Component.literal(species.getDisplayName()),
                x + PAD, cy, COLOR_ACCENT, false);
        cy += NLH + 2;
        drawSmallAt(g, "[ " + species.getCategory().getDisplayName() + " ]",
                x + PAD, cy, COLOR_COPPER);
        cy += SLH + 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR);
        cy += 4;
        cy = drawSmallWrap(g, species.getDescription(),
                x + PAD, cy, w - PAD * 2, COLOR_LABEL);
        cy += 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR);
        cy += 4;
        drawSmallAt(g, "◆ " + species.getCategory().getDisplayName(),
                x + PAD, cy, COLOR_LABEL);
        cy += SLH + 2;
        drawSmallAt(g, "  ▶ " + species.getDisplayName(), x + PAD, cy, COLOR_ACCENT);

        esc(g);
    }

    // ── Center: origins list ──────────────────────────────────────────────────

    private void drawOriginListPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = listX, y = top, w = LIST_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "ORIGINS");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ry = y + HDR_H + 1 - scrollList;

        for (OriginData o : origins) {
            if (ry + ROW_H > y + HDR_H && ry < bot) {
                boolean locked = o.getUnlockState() == UnlockState.LOCKED;
                boolean sel    = o == selOr;
                boolean hov    = !locked && inB(mx, my, x + 3, ry, w - 6, ROW_H);
                if (hov) hovOr = o;
                drawRow(g, x, ry, w, o.getDisplayName(),
                        "[" + o.getSourceTag().getDisplayName() + "]",
                        sel, hov, locked);
            }
            ry += ROW_H;
        }
        esc(g);
    }

    // ── Right: origin details ─────────────────────────────────────────────────

    private void drawDetailPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = detX, y = top, w = detW, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "ORIGIN DETAILS");

        OriginData disp = selOr != null ? selOr : hovOr;
        if (disp == null) {
            drawSmallAt(g, "Select an origin to view details",
                    x + PAD, y + h / 2, COLOR_LABEL);
            return;
        }

        int splitX = x + w / 2;
        int innerY = y + HDR_H + 2;

        var player = Minecraft.getInstance().player;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, x + 4, innerY, splitX - 2, y + h - 4,
                    40, 0.0f, mx, my, player);
        }

        sc(g, splitX, innerY, w / 2 - 4, h - HDR_H - 4);
        int ix = splitX + PAD, iw = w / 2 - PAD * 2;
        int cy = innerY + 4 - scrollDet;

        g.text(font, Component.literal(disp.getDisplayName()), ix, cy, COLOR_ACCENT, false);
        cy += NLH + 2;
        drawSmallAt(g, "[ " + disp.getSourceTag().getDisplayName() + " ]",
                ix, cy, disp.getSourceTag().getColor());
        cy += SLH + 5;

        cy = drawSection(g, ix, cy, iw, "DESCRIPTION", COLOR_COPPER);
        cy = drawSmallWrap(g, disp.getDescription(), ix, cy, iw, COLOR_LABEL);
        cy += 5;

        cy = drawSection(g, ix, cy, iw, "FINAL ANCESTRY BONUSES", COLOR_COPPER);
        cy = drawBonuses(g, ix, cy, iw, disp.getAbilityScoreBonus());
        cy += 3;

        cy = drawSection(g, ix, cy, iw, "HEIGHT RANGE", COLOR_COPPER);
        drawSmallAt(g, String.format("%.2f - %.2f blocks",
                disp.getMinHeight(), disp.getMaxHeight()), ix, cy, COLOR_VALUE);

        esc(g);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        int ry = top + HDR_H + 1 - scrollList;
        for (OriginData o : origins) {
            if (inB(mx, my, listX + 3, ry, LIST_W - 6, ROW_H)) {
                if (o.getUnlockState() != UnlockState.LOCKED) {
                    selOr = o; scrollDet = 0; click();
                }
                return true;
            }
            ry += ROW_H;
        }

        if (isBack(mx, my)) {
            click();
            Minecraft.getInstance().setScreen(new SpeciesSelectionScreen());
            return true;
        }
        if (selOr != null && isNext(mx, my)) {
            click();
            Minecraft.getInstance().setScreen(new ConfirmAncestryScreen(species, selOr));
            return true;
        }
        return super.mouseClicked(mouse, dc);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int x = (int) mx, y = (int) my, ph = bot - top;
        if (inB(x, y, 0, top, CAT_W, ph)) {
            scrollCat = Math.max(0, (int) (scrollCat - v * 10)); return true;
        }
        if (inB(x, y, listX, top, LIST_W, ph)) {
            int max = Math.max(0, origins.size() * ROW_H - (ph - HDR_H));
            scrollList = Math.clamp((int) (scrollList - v * 10), 0, max); return true;
        }
        if (inB(x, y, detX, top, detW, ph)) {
            scrollDet = Math.max(0, (int) (scrollDet - v * 10)); return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
}