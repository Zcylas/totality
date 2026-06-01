package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.combat.weapon.TotalityWeaponItem;
import zcylas.totality.api.rpg.combat.weapon.WeaponType;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.theme.TooltipTheme;

public class TooltipWeaponBlock {

    private static final int LABEL_COLOR    = 0xFF6B7280;
    private static final int DAMAGE_COLOR   = 0xFFE08060;
    private static final int MODIFIER_COLOR = 0xFF00BCD4;
    private static final int PROPERTY_COLOR = 0xFF9CA3AF;
    private static final int STAMINA_COLOR  = 0xFF66BB6A;
    private static final int DICE_BOX_BG    = 0xFF1A1C22;
    private static final int DICE_BOX_BORD  = 0xFF2D3748;

    public static boolean hasWeaponStats(ItemStack stack) {
        return stack.getItem() instanceof TotalityWeaponItem;
    }

    public static int estimateHeight(Font font, ItemStack stack) {
        if (!(stack.getItem() instanceof TotalityWeaponItem weapon)) return 0;
        int rowH   = font.lineHeight + 2;
        int badgeH = font.lineHeight + 4;
        int rightColH = font.lineHeight + 3 + badgeH;
        int diceBoxH  = rightColH + 8;
        return rowH          // DAMAGE label
                + diceBoxH + 6 // dice block
                + 1 + 5        // mini separator
                + rowH         // PROPERTIES label
                + badgeH + 4   // badges
                + rowH         // category + range
                + rowH + 4;    // stamina + bottom padding
    }

    public static int draw(GuiGraphicsExtractor graphics, Font font, ItemStack stack,
                           int x, int y, int panelW, int padding, TooltipTheme theme) {
        if (!(stack.getItem() instanceof TotalityWeaponItem weapon)) return 0;

        int innerW  = panelW - padding * 2;
        int cursorY = y;
        int rowH    = font.lineHeight + 2;

        // Badge dimensions — equal top/bottom padding of 2px
        int badgePad = 2;
        int badgeH   = font.lineHeight + badgePad * 2;

        // ── DAMAGE ────────────────────────────────────────────────────────────────
        graphics.text(font, TotalityIcons.iconLabel(TotalityIcons.DAMAGE, DAMAGE_COLOR, "DAMAGE"),
                x, cursorY, LABEL_COLOR, false);
        cursorY += rowH;

        // Dice box height — tall enough to contain damage type + modifier badge
        // Right column: lineHeight (Piercing) + 3 gap + badgeH (STR/DEX)
        int rightColH = font.lineHeight + 3 + badgeH;
        int diceBoxH  = rightColH + 8; // 4px top + 4px bottom

        // Dice box
        String diceStr  = weapon.getDiceCount() + weapon.getDamageDie().name().toLowerCase();
        int    diceBoxW = Math.max(font.width(diceStr) + 16, 32);
        graphics.fill(x, cursorY, x + diceBoxW, cursorY + diceBoxH, DICE_BOX_BG);
        graphics.fill(x,              cursorY,              x + diceBoxW, cursorY + 1,           DICE_BOX_BORD);
        graphics.fill(x,              cursorY + diceBoxH-1, x + diceBoxW, cursorY + diceBoxH,    DICE_BOX_BORD);
        graphics.fill(x,              cursorY,              x + 1,         cursorY + diceBoxH,    DICE_BOX_BORD);
        graphics.fill(x + diceBoxW-1, cursorY,              x + diceBoxW,  cursorY + diceBoxH,    DICE_BOX_BORD);
        // Dice text — centered in box
        TooltipPainter.drawText(graphics, font, diceStr,
                x + (diceBoxW - font.width(diceStr)) / 2,
                cursorY + (diceBoxH - font.lineHeight) / 2,
                0xFFF0F0F0);

        // Right column — same top level as dice box, 4px top padding
        int rightX    = x + diceBoxW + 8;
        int rightTopY = cursorY + 4;

        // Damage type — top of right column
        String dmgName = resolveDamageTypeName(weapon);
        TooltipPainter.drawText(graphics, font, dmgName, rightX, rightTopY, DAMAGE_COLOR);

        // Modifier badges — below damage type with 3px gap
        int modY = rightTopY + font.lineHeight + 3;
        if (weapon.isFinesse()) {
            int strBW = drawModBadge(graphics, font, "STR", rightX, modY, badgePad);
            int orX   = rightX + strBW + 3;
            TooltipPainter.drawText(graphics, font, "or", orX, modY + badgePad, LABEL_COLOR);
            drawModBadge(graphics, font, "DEX", orX + font.width("or") + 3, modY, badgePad);
        } else {
            drawModBadge(graphics, font, weapon.getDefaultAbilityScore().name(), rightX, modY, badgePad);
        }

        cursorY += diceBoxH + 6;

        // Mini separator
        graphics.fill(x, cursorY, x + innerW, cursorY + 1, 0x221E2028);
        cursorY += 5;

        // ── PROPERTIES ────────────────────────────────────────────────────────────
        graphics.text(font, TotalityIcons.iconLabel(TotalityIcons.PROPERTIES, MODIFIER_COLOR, "PROPERTIES"),
                x, cursorY, LABEL_COLOR, false);
        cursorY += rowH;

        int badgeX = x;
        badgeX = tryBadge(graphics, font, weapon.isFinesse(),   "Finesse",   badgeX, cursorY, badgePad, 0xFF0D2A2A, MODIFIER_COLOR);
        badgeX = tryBadge(graphics, font, weapon.getWeaponType() == WeaponType.THROWN, "Thrown", badgeX, cursorY, badgePad, 0xFF0D1A2A, 0xFF42A5F5);
        badgeX = tryBadge(graphics, font, weapon.isLight(),     "Light",     badgeX, cursorY, badgePad, 0xFF1A1C22, 0xFF6B7280);
        badgeX = tryBadge(graphics, font, weapon.isHeavy(),     "Heavy",     badgeX, cursorY, badgePad, 0xFF2A1A1A, 0xFFEF5350);
        badgeX = tryBadge(graphics, font, weapon.isReach(),     "Reach",     badgeX, cursorY, badgePad, 0xFF1A2A1A, 0xFF66BB6A);
        badgeX = tryBadge(graphics, font, weapon.isVersatile(), "Versatile", badgeX, cursorY, badgePad, 0xFF2A2A1A, 0xFFFFD700);
        cursorY += badgeH + 4;

        // Category + range
        TooltipPainter.drawText(graphics, font, weapon.getWeaponCategory().displayName(), x, cursorY, LABEL_COLOR);
        int[] range = weapon.getThrowRange();
        if (range != null) {
            Component rangeComp = TotalityIcons.iconLabel(TotalityIcons.RANGE, 0xFF42A5F5, LABEL_COLOR,
                    range[0] + " / " + range[1] + " ft.");
            int rangeW = font.width(rangeComp);
            graphics.text(font, rangeComp, x + innerW - rangeW, cursorY, LABEL_COLOR, false);
        }
        cursorY += rowH;

        // Stamina cost
        int cost = weapon.getWeaponType() == WeaponType.THROWN
                ? weapon.getThrownAttackCost() : weapon.getNormalAttackCost();
        Component staminaLabel = TotalityIcons.iconLabel(TotalityIcons.STAMINA, STAMINA_COLOR , "STAMINA COST");
        graphics.text(font, staminaLabel, x, cursorY, STAMINA_COLOR, false);
        String costStr = String.valueOf(cost);
        TooltipPainter.drawText(graphics, font, costStr, x + innerW - font.width(costStr), cursorY, STAMINA_COLOR);
        cursorY += rowH + 4;

        return cursorY - y;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String resolveDamageTypeName(TotalityWeaponItem weapon) {
        String path = weapon.getDamageType().getId().getPath();
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /** Draws modifier badge, returns width consumed. */
    private static int drawModBadge(GuiGraphicsExtractor graphics, Font font,
                                    String label, int x, int y, int pad) {
        int badgeW = font.width(label) + 8;
        int badgeH = font.lineHeight + pad * 2;
        graphics.fill(x, y, x + badgeW, y + badgeH, 0xFF0D2A2A);
        graphics.fill(x,           y,           x + badgeW, y + 1,         MODIFIER_COLOR);
        graphics.fill(x,           y + badgeH-1, x + badgeW, y + badgeH,   MODIFIER_COLOR);
        graphics.fill(x,           y,            x + 1,       y + badgeH,   MODIFIER_COLOR);
        graphics.fill(x + badgeW-1, y,           x + badgeW,  y + badgeH,   MODIFIER_COLOR);
        TooltipPainter.drawText(graphics, font, label, x + 4, y + pad, MODIFIER_COLOR);
        return badgeW;
    }

    private static int tryBadge(GuiGraphicsExtractor graphics, Font font,
                                boolean condition, String label,
                                int x, int y, int pad, int bg, int border) {
        if (!condition) return x;
        int badgeW = font.width(label) + 8;
        int badgeH = font.lineHeight + pad * 2;
        graphics.fill(x, y, x + badgeW, y + badgeH, bg);
        graphics.fill(x,           y,            x + badgeW, y + 1,         border);
        graphics.fill(x,           y + badgeH-1, x + badgeW, y + badgeH,   border);
        graphics.fill(x,           y,            x + 1,       y + badgeH,   border);
        graphics.fill(x + badgeW-1, y,           x + badgeW,  y + badgeH,   border);
        TooltipPainter.drawText(graphics, font, label, x + 4, y + pad, border);
        return x + badgeW + 3;
    }

    private TooltipWeaponBlock() {}
}