package zcylas.totality.screen.ancestry;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.rpg.ancestry.*;
import zcylas.totality.networking.ancestry.SelectAncestryPayload;

public class ConfirmAncestryScreen extends BaseAncestryScreen {

    private final Species species;
    private final Origin  origin;

    public ConfirmAncestryScreen(Species species, Origin origin) {
        super(Component.literal("Confirm Your Ancestry"));
        this.species = species;
        this.origin  = origin;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        String crumb = species.getCategory().getDisplayName()
                + "  ▶  " + species.getDisplayName()
                + (origin != null ? "  ▶  " + origin.getDisplayName() : "");
        drawHeader(g, "Confirm Your Ancestry", "Step 3: Confirm Selection", crumb);

        drawSummaryPanel(g, mx, my);
        drawCenterPanel(g, mx, my);
        drawConfirmPanel(g, mx, my);
        drawBottomBar(g, mx, my, true, true, true, "CONFIRM");
    }

    // ── Left: ancestry summary ────────────────────────────────────────────────

    private void drawSummaryPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 0, y = top, w = CAT_W, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "ANCESTRY SUMMARY");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int cy = y + HDR_H + 4 - scrollCat;

        drawSmallAt(g, "CATEGORY", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
        drawSmallAt(g, species.getCategory().getIcon() + " "
                        + species.getCategory().getDisplayName(),
                x + PAD + 3, cy, COLOR_VALUE); cy += SLH + 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;

        drawSmallAt(g, "SPECIES", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
        drawSmallAt(g, species.getDisplayName(), x + PAD + 3, cy, COLOR_ACCENT); cy += SLH + 5;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;

        if (origin != null) {
            drawSmallAt(g, "ORIGIN", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
            drawSmallAt(g, origin.getDisplayName(), x + PAD + 3, cy, COLOR_ACCENT); cy += SLH + 5;
            g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;

            drawSmallAt(g, "SOURCE TAG", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
            drawSmallAt(g, origin.getSourceTag().getDisplayName(),
                    x + PAD + 3, cy, origin.getSourceTag().getColor()); cy += SLH + 5;
            g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;
        }

        float mn = origin != null ? origin.getMinHeight() : species.getMinHeight();
        float mx2 = origin != null ? origin.getMaxHeight() : species.getMaxHeight();
        drawSmallAt(g, "HEIGHT RANGE", x + PAD, cy, COLOR_COPPER); cy += SLH + 2;
        drawSmallAt(g, String.format("%.2f - %.2f blocks", mn, mx2),
                x + PAD + 3, cy, COLOR_VALUE); cy += SLH + 10;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_SEPARATOR); cy += 4;

        drawSmallWrap(g, "⚠ This choice is permanent.", x + PAD, cy, w - PAD * 2, COLOR_COPPER_BRIGHT);
        esc(g);
    }

    // ── Center: full player render ────────────────────────────────────────────

    private void drawCenterPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = listX, y = top, w = LIST_W, h = bot - top;
        g.fill(x, y, x + w, y + h, 0xFF080C12);
        drawCopperBorder(g, x, y, w, h);
        drawCorner(g, x + 1, y + 1, true,  true);
        drawCorner(g, x + w - 1, y + 1, false, true);
        drawCorner(g, x + 1, y + h - 1, true,  false);
        drawCorner(g, x + w - 1, y + h - 1, false, false);

        var player = Minecraft.getInstance().player;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, x + 10, y + 10, x + w - 10, y + h - 20,
                    60, 0.0f, mx, my, player);
        }
    }

    // ── Right: confirm panel ──────────────────────────────────────────────────

    private void drawConfirmPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = detX, y = top, w = detW, h = bot - top;
        drawPanel(g, x, y, w, h);
        drawPanelHdr(g, x, y, w, "CONFIRM ANCESTRY");

        sc(g, x + 3, y + HDR_H + 1, w - 6, h - HDR_H - 4);
        int ix = x + PAD, iw = w - PAD * 2;
        int cy = y + HDR_H + 4 - scrollDet;

        if (origin != null) {
            cy = drawSection(g, ix, cy, iw, "FINAL BONUSES", COLOR_COPPER);
            cy = drawBonuses(g, ix, cy, iw, origin.getAbilityScoreBonus());
            cy += 4;
        }

        cy = drawSection(g, ix, cy, iw, "PASSIVE TRAITS", COLOR_COPPER);
        drawSmallAt(g, "Unlocked via progression", ix, cy, COLOR_LABEL);
        cy += SLH + 8;

        cy = drawSection(g, ix, cy, iw, "ABILITY SUMMARY", COLOR_COPPER);
        drawSmallAt(g, "Coming with class selection", ix, cy, COLOR_LABEL);

        esc(g);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int)mouse.x(), my = (int)mouse.y();

        if (isBack(mx, my)) {
            click();
            Minecraft.getInstance().setScreen(origin != null
                    ? new OriginSelectionScreen(species)
                    : new SpeciesSelectionScreen());
            return true;
        }
        if (isNext(mx, my)) {
            click();
            net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("Sending ancestry: " + species.name() + " / " + (origin != null ? origin.name() : "null"))
            );
            ClientPlayNetworking.send(new SelectAncestryPayload(
                    species.name(), origin != null ? origin.name() : null));
            onClose();
            return true;
        }
        return super.mouseClicked(mouse, dc);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int x = (int)mx, y = (int)my, ph = bot - top;
        if (inB(x, y, 0, top, CAT_W, ph)) {
            scrollCat = Math.max(0, (int)(scrollCat - v * 10)); return true;
        }
        if (inB(x, y, detX, top, detW, ph)) {
            scrollDet = Math.max(0, (int)(scrollDet - v * 10)); return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
}