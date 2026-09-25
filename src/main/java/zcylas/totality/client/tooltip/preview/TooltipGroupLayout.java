package zcylas.totality.client.tooltip.preview;

/**
 * Pure on-screen placement for the tooltip group: the main tooltip panel plus an optional companion
 * card. The group is positioned as one unit so neither part is pushed off-screen; sizes are constant
 * for a hovered item, so an animating preview never makes the group jump.
 *
 * <p>Main panel placement without a companion is exactly Tooltip V1's rule: right of the cursor,
 * flipped to the left of the cursor if it would overflow the right edge, then clamped into the margins.
 *
 * <p>Companion fallback chain (first that fits wins):
 * <ol>
 *   <li>{@link CardSide#LEFT} of the main panel (preferred);</li>
 *   <li>LEFT after shifting the whole group right, if the main panel still fits on screen;</li>
 *   <li>{@link CardSide#RIGHT} of the main panel;</li>
 *   <li>{@link CardSide#ABOVE} the main panel, then {@link CardSide#BELOW} it;</li>
 *   <li>{@link CardSide#HIDDEN} — only when the screen genuinely cannot show both without overlap.
 *       The card is never drawn off-screen.</li>
 * </ol>
 * The card is top-aligned with the main panel's header region (clamped to the screen) when beside it.
 */
public final class TooltipGroupLayout {

    public static final int CURSOR_OFFSET = 12;
    public static final int COMPANION_GAP = 4;

    public enum CardSide { NONE, LEFT, RIGHT, ABOVE, BELOW, HIDDEN }

    public record Placement(int panelX, int panelY, int cardX, int cardY, CardSide cardSide) {}

    /**
     * @param cardW companion card width, or 0 when there is no companion
     * @param cardH companion card height, or 0 when there is no companion
     */
    public static Placement place(int cursorX, int cursorY, int panelW, int panelH, int cardW, int cardH,
                                  int screenW, int screenH, int margin) {
        int panelX = cursorX + CURSOR_OFFSET;
        int panelY = cursorY - CURSOR_OFFSET;
        if (panelX + panelW > screenW - margin) panelX = cursorX - panelW - CURSOR_OFFSET;
        panelX = clamp(panelX, margin, screenW - margin - panelW);
        panelY = clamp(panelY, margin, screenH - margin - panelH);

        if (cardW <= 0 || cardH <= 0) return new Placement(panelX, panelY, 0, 0, CardSide.NONE);

        int besideY = clamp(panelY, margin, screenH - margin - cardH);
        boolean cardFitsVertically = cardH <= screenH - margin * 2;

        int leftX = panelX - COMPANION_GAP - cardW;
        if (cardFitsVertically && leftX >= margin) {
            return new Placement(panelX, panelY, leftX, besideY, CardSide.LEFT);
        }
        int shift = margin - leftX;
        if (cardFitsVertically && panelX + shift + panelW <= screenW - margin) {
            return new Placement(panelX + shift, panelY, margin, besideY, CardSide.LEFT);
        }
        int rightX = panelX + panelW + COMPANION_GAP;
        if (cardFitsVertically && rightX + cardW <= screenW - margin) {
            return new Placement(panelX, panelY, rightX, besideY, CardSide.RIGHT);
        }
        int alignedX = clamp(panelX, margin, screenW - margin - cardW);
        boolean cardFitsHorizontally = cardW <= screenW - margin * 2;
        int aboveY = panelY - COMPANION_GAP - cardH;
        if (cardFitsHorizontally && aboveY >= margin) {
            return new Placement(panelX, panelY, alignedX, aboveY, CardSide.ABOVE);
        }
        int belowY = panelY + panelH + COMPANION_GAP;
        if (cardFitsHorizontally && belowY + cardH <= screenH - margin) {
            return new Placement(panelX, panelY, alignedX, belowY, CardSide.BELOW);
        }
        return new Placement(panelX, panelY, 0, 0, CardSide.HIDDEN);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private TooltipGroupLayout() {}
}
