package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.theme.TooltipTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * The detached modifier panels under the main tooltip — three small cards, not a footer line:
 * <pre>
 *   [ SHIFT  ]  [  ALT   ]  [  CTRL  ]
 *   [Details ]  [Interact]  [Technical]
 * </pre>
 * Only applicable modifiers get a panel (SHIFT when the item has details, CTRL when it has technical information;
 * ALT is part of the layout but has no interaction system yet, so it is never offered). Visible panels keep the
 * SHIFT, ALT, CTRL order, share one width and are centred under the tooltip, clamped to the screen. A held modifier
 * highlights its panel. The panels are part of the tooltip's vertical footprint ({@link #blockHeight}), so tooltip
 * placement and the body viewport make room for them instead of pushing them off-screen.
 */
public final class TooltipModifierPanels {

    public enum Modifier {
        SHIFT("SHIFT", "Details"),
        ALT("ALT", "Interact"),
        CTRL("CTRL", "Technical");

        public final String key;
        public final String action;

        Modifier(String key, String action) {
            this.key = key;
            this.action = action;
        }
    }

    public record Panel(Modifier modifier, boolean active) {}

    /** Gap between the main tooltip and the panel row. */
    public static final int GAP_ABOVE = 3;
    /** Gap between neighbouring panels. */
    public static final int GAP_BETWEEN = 4;
    static final int PAD_X = 6;
    static final int PAD_Y = 3;
    static final int LINE_GAP = 1;
    /** Panel height: two text rows. */
    public static final int PANEL_H = PAD_Y + 9 + LINE_GAP + 9 + PAD_Y - 1;

    static final int KEY_COLOR = 0xFFD2D6DC;
    static final int ACTION_COLOR = 0xFF7E858F;
    static final int ACTIVE_ACTION_COLOR = 0xFFB6BCC4;

    /**
     * Visible panels, in SHIFT, ALT, CTRL order. {@code altAvailable} is always false until the interaction system
     * exists — kept as a parameter so ALT slots in without a layout change.
     */
    public static List<Panel> visible(boolean detailsAvailable, boolean altAvailable, boolean technicalAvailable,
                                      TooltipDisclosureLevel disclosure) {
        List<Panel> out = new ArrayList<>();
        if (detailsAvailable) out.add(new Panel(Modifier.SHIFT, disclosure.includes(TooltipDisclosureLevel.DETAILS)));
        if (altAvailable) out.add(new Panel(Modifier.ALT, false));
        if (technicalAvailable) out.add(new Panel(Modifier.CTRL, disclosure.includes(TooltipDisclosureLevel.TECHNICAL)));
        return out;
    }

    /** Vertical space the panels add below the tooltip (0 when none are visible). */
    public static int blockHeight(List<Panel> panels) {
        return panels.isEmpty() ? 0 : GAP_ABOVE + PANEL_H;
    }

    /** One panel width for all visible panels: the widest key/action text plus padding. */
    public static int panelWidth(Font font, List<Panel> panels) {
        int max = 0;
        for (Panel p : panels) max = Math.max(max, Math.max(font.width(p.modifier().key), font.width(p.modifier().action)));
        return max + 2 * PAD_X;
    }

    public static int rowWidth(int panelW, int count) {
        return count <= 0 ? 0 : count * panelW + (count - 1) * GAP_BETWEEN;
    }

    /**
     * Left x of the panel row: centred under the tooltip ({@code tooltipX}, {@code tooltipW}), clamped so the whole
     * row stays within {@code [margin, screenW - margin]}. Pure — unit-tested.
     */
    public static int rowX(int tooltipX, int tooltipW, int rowW, int screenW, int margin) {
        int x = tooltipX + (tooltipW - rowW) / 2;
        return Math.max(margin, Math.min(x, screenW - margin - rowW));
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, List<Panel> panels, TooltipTheme theme,
                            int rarityColor, int x, int y, int panelW) {
        int frame = TooltipVignettePainter.withAlpha(rarityColor, 0.45f);
        int activeFrame = TooltipRarityPlaquePainter.labelColor(rarityColor);
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            int px = x + i * (panelW + GAP_BETWEEN);
            TooltipPainter.drawBackground(graphics, px, y, panelW, PANEL_H, theme);
            int border = panel.active() ? activeFrame : frame;
            graphics.fill(px, y, px + panelW, y + 1, border);
            graphics.fill(px, y + PANEL_H - 1, px + panelW, y + PANEL_H, border);
            graphics.fill(px, y + 1, px + 1, y + PANEL_H - 1, border);
            graphics.fill(px + panelW - 1, y + 1, px + panelW, y + PANEL_H - 1, border);
            String key = panel.modifier().key, action = panel.modifier().action;
            int keyColor = panel.active() ? activeFrame : KEY_COLOR;
            graphics.text(font, key, px + (panelW - font.width(key)) / 2, y + PAD_Y, keyColor, true);
            graphics.text(font, action, px + (panelW - font.width(action)) / 2, y + PAD_Y + 9 + LINE_GAP,
                    panel.active() ? ACTIVE_ACTION_COLOR : ACTION_COLOR, false);
        }
    }

    private TooltipModifierPanels() {}
}
