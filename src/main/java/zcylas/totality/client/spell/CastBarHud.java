package zcylas.totality.client.spell;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.magic.spell.CastType;

/**
 * Renders a spell cast bar centered just below the crosshair.
 *
 * Layout (centered on screen):
 *
 *       [  Fireball  ]      ← spell name, colored
 *   [████████░░░░░░░░░░░]   ← progress bar filling left→right
 *      STATIONARY: red border if movement would interrupt
 */
public final class CastBarHud {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "cast_bar");

    private static final int BAR_W     = 140;
    private static final int BAR_H     = 7;
    private static final int Y_BELOW   = 18;   // pixels below crosshair center
    private static final int BG_COLOR  = 0xBB000000;
    private static final int BORDER_COLOR_MOBILE     = 0xFF334455;
    private static final int BORDER_COLOR_STATIONARY = 0xFF882222;

    private CastBarHud() {}

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, CastBarHud::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        if (!ClientCastManager.isCasting()) return;

        Minecraft mc = Minecraft.getInstance();
        int cx   = mc.getWindow().getGuiScaledWidth()  / 2;
        int cy   = mc.getWindow().getGuiScaledHeight() / 2;

        int barX = cx - BAR_W / 2;
        int barY = cy + Y_BELOW;

        float fraction   = ClientCastManager.getFraction();
        int   fillW      = (int)(fraction * (BAR_W - 2));
        int   spellColor = ClientCastManager.getColor();
        int   fillColor  = spellColor;
        int   borderColor = ClientCastManager.getCastType() == CastType.STATIONARY
                ? BORDER_COLOR_STATIONARY : BORDER_COLOR_MOBILE;

        // Background
        g.fill(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, BG_COLOR);
        // Border
        g.fill(barX - 1, barY - 1, barX + BAR_W + 1, barY, borderColor);           // top
        g.fill(barX - 1, barY + BAR_H, barX + BAR_W + 1, barY + BAR_H + 1, borderColor); // bottom
        g.fill(barX - 1, barY, barX, barY + BAR_H, borderColor);                   // left
        g.fill(barX + BAR_W, barY, barX + BAR_W + 1, barY + BAR_H, borderColor);   // right
        // Track (empty)
        g.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0x33FFFFFF);
        // Fill
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + BAR_H, fillColor);
            // Glint at leading edge
            g.fill(barX + fillW - 1, barY, barX + fillW, barY + BAR_H, 0xCCFFFFFF);
        }

        // Spell name centered above the bar
        Component nameText = Component.literal(ClientCastManager.getSpellName());
        int nameX = cx - mc.font.width(nameText) / 2;
        int nameY = barY - mc.font.lineHeight - 2;
        g.text(mc.font, nameText, nameX, nameY, spellColor, true);

        // Stationary warning dot
        if (ClientCastManager.getCastType() == CastType.STATIONARY) {
            Component warn = Component.literal("● Do not move");
            int warnX = cx - mc.font.width(warn) / 2;
            g.text(mc.font, warn, warnX, barY + BAR_H + 3, 0xFFAA3333, false);
        }
    }
}