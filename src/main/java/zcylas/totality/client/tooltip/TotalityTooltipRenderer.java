package zcylas.totality.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.client.tooltip.renderer.*;
import zcylas.totality.client.tooltip.theme.TooltipBorderStyle;
import zcylas.totality.client.tooltip.theme.TooltipColors;
import zcylas.totality.client.tooltip.theme.TooltipTheme;

import java.util.ArrayList;
import java.util.List;

public class TotalityTooltipRenderer {

    private static final int PADDING = 10;

    public static void render(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        RarityComponent rarity = stack.get(ItemComponents.getRarity());
        ItemTypeComponent type = stack.get(ItemComponents.getItemType());

        if (rarity == null) return;

        ItemType itemType = type != null ? type.type() : null;
        TooltipTheme theme = resolveTheme(rarity.rarity(), itemType);

        String title = stack.getHoverName().getString();
        String rarityText = rarity.rarity().getSerializedName().toUpperCase();
        String typeText = type != null ? type.type().getSerializedName().toUpperCase() : null;

        int iconSize = 16;
        int iconAreaW = iconSize + 8;
        int separatorH = 10;
        int footerH = 14;
        int badgeRowH = font.lineHeight + 4 + 2; // lineHeight + padding + gap
        int headerH = PADDING + iconSize + badgeRowH - font.lineHeight + PADDING;

        // Panel width — first pass without lore
        int minContentW = Math.min(Math.max(iconAreaW + font.width(title) + 4, 160), 180);
        // Lore
        LoreComponent lore = stack.get(ItemComponents.getLore());
        List<String> loreLines = null;
        if (lore != null) {
            loreLines = TooltipPainter.wrapText(lore.text(), font, minContentW);
            for (String line : loreLines)
                minContentW = Math.max(minContentW, font.width(line) + 4);
        }

        // Extension lines
        List<Component> extensionLines = new ArrayList<>();
        if (stack.getItem() instanceof TooltipExtension ext) {
            ext.addTooltipLines(stack, font, extensionLines);
        }

        int panelW = PADDING + minContentW + PADDING;

        // Body height
        int loreH = loreLines != null ? loreLines.size() * (font.lineHeight + 2) + 4 : 0;
        int extensionH = extensionLines.isEmpty() ? 0 : extensionLines.size() * (font.lineHeight + 2) + 4;
        int bodyH = loreH + extensionH + font.lineHeight + 8;
        int statH   = TooltipStatBlock.hasStats(stack)   ? TooltipStatBlock.estimateHeight(font, stack)   : 0;
        int weaponH = TooltipWeaponBlock.hasWeaponStats(stack) ? TooltipWeaponBlock.estimateHeight(font, stack) : 0;
        int panelH  = headerH + separatorH + bodyH + statH + weaponH + footerH;


        // Screen bounds + clamping
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int panelX = x + 12;
        int panelY = y - 12;
        if (panelX + panelW > screenW - 6) panelX = x - panelW - 12;
        if (panelX < 6) panelX = 6;
        if (panelY + panelH > screenH - 6) panelY = screenH - panelH - 6;
        if (panelY < 6) panelY = 6;

        // Draw background + border
        TooltipPainter.drawBackground(graphics, panelX, panelY, panelW, panelH, theme);
        TooltipFrameRenderer.drawBorder(graphics, panelX, panelY, panelW, panelH, theme, rarity.rarity());

        // Header
        int iconX = panelX + PADDING;
        int iconY = panelY + PADDING;
        TooltipPainter.drawItem(graphics, stack, iconX, iconY);

        int nameX = iconX + iconAreaW;
        int nameY = panelY + PADDING;

// Name
        long timeMs = System.currentTimeMillis();
        drawAnimatedTitle(graphics, font, title, nameX, nameY, theme.name(), rarity.rarity(), timeMs);

// Rarity + Type badges on same row below name
        int badgePadX = 4;
        int badgePadY = 2;
        int badgeY = nameY + font.lineHeight + 2;
        int badgeCursorX = nameX;

        // Rarity badge
        int rarityColor = TooltipColors.forRarity(rarity.rarity());
        int rarityBadgeBg = darken(rarityColor, 0.4f);
        int minBadgeW = 60;
        int rarityBadgeW = Math.max(font.width(rarityText) + badgePadX * 2, minBadgeW);
        int rarityBadgeH = font.lineHeight + badgePadY * 2;
        graphics.fill(badgeCursorX, badgeY, badgeCursorX + rarityBadgeW, badgeY + rarityBadgeH, rarityBadgeBg);
        int rarityTextX = badgeCursorX + (rarityBadgeW - font.width(rarityText)) / 2;
        TooltipPainter.drawText(graphics, font, rarityText, rarityTextX, badgeY + badgePadY, lighten(rarityColor, 0.4f));
        badgeCursorX += rarityBadgeW + 4;

        // Type badge
        if (typeText != null) {
            int typeColor = TooltipColors.forType(type.type());
            int typeBadgeBg = darken(typeColor, 0.4f);
            int typeBadgeW = Math.max(font.width(typeText) + badgePadX * 2, minBadgeW);
            graphics.fill(badgeCursorX, badgeY, badgeCursorX + typeBadgeW, badgeY + rarityBadgeH, typeBadgeBg);
            int typeTextX = badgeCursorX + (typeBadgeW - font.width(typeText)) / 2;
            TooltipPainter.drawText(graphics, font, typeText, typeTextX, badgeY + badgePadY, lighten(typeColor, 0.4f));
        }

        // Separator
        int separatorY = panelY + headerH;
        TooltipPainter.drawSeparator(graphics, panelX + PADDING, separatorY, panelW - PADDING * 2, theme);

        // Body
        int cursorY = separatorY + separatorH;

        // Weapon stats block — first in body for weapon items
        if (TooltipWeaponBlock.hasWeaponStats(stack)) {
            int consumed = TooltipWeaponBlock.draw(
                    graphics, font, stack, panelX + PADDING, cursorY, panelW, PADDING, theme);
            cursorY += consumed + 4;
        }


        // Extension lines first
        if (!extensionLines.isEmpty()) {
            for (Component line : extensionLines) {
                TooltipPainter.drawText(graphics, font, line, panelX + PADDING, cursorY, 0xFFFFFFFF);
                cursorY += font.lineHeight + 2;
            }
            cursorY += 4;
        }
        if (TooltipStatBlock.hasStats(stack)) {
            int consumed = TooltipStatBlock.draw(graphics, font, stack, panelX + PADDING, cursorY, panelW, PADDING, theme);
            cursorY += consumed + 4;
        }
        // Lore — pinned just above Totality
        if (loreLines != null) {
            int loreStartY = panelY + panelH - footerH - font.lineHeight - 2
                    - 4
                    - loreLines.size() * (font.lineHeight + 2);
            int loreLineY = loreStartY - 4;

            int loreColor = TooltipPainter.lerpColor(theme.body(), theme.separator(), 0.5f) | 0xFF000000;

            graphics.fill(panelX + PADDING + 10, loreLineY,
                    panelX + panelW - PADDING - 10, loreLineY + 1, 0x44888888);
            for (String line : loreLines) {
                Component loreLine = Component.literal(line)
                        .withStyle(s -> s.withItalic(true).withColor(loreColor).withFont(FontDescription.DEFAULT));
                TooltipPainter.drawText(graphics, font, loreLine, panelX + PADDING, loreStartY, loreColor);
                loreStartY += font.lineHeight + 2;
            }
        }

        // Totality credit
        Component totalityLine = Component.literal("Totality")
                .withStyle(s -> s.withColor(0xFF5588FF).withItalic(true).withFont(FontDescription.DEFAULT));
        TooltipPainter.drawText(graphics, font, totalityLine, panelX + PADDING, panelY + panelH - footerH - font.lineHeight - 2, 0xFF5588FF);

        // Footer
        TooltipPainter.drawFooterDots(graphics, panelX + panelW / 2, panelY + panelH - 8, theme);
    }

    private static TooltipTheme resolveTheme(ItemRarity rarity, ItemType type) {
        int rarityStyle = rarityBorderStyle(rarity);
        int typeStyle = typeBorderStyle(type);

        return switch (rarity) {
            case UNCOMMON   -> TooltipTheme.uncommon(rarityStyle, typeStyle);
            case RARE       -> TooltipTheme.rare(rarityStyle, typeStyle);
            case EPIC       -> TooltipTheme.epic(rarityStyle, typeStyle);
            case LEGENDARY  -> TooltipTheme.legendary(rarityStyle, typeStyle);
            case MYTHICAL   -> TooltipTheme.mythical(rarityStyle, typeStyle);
            case ARTIFACT   -> TooltipTheme.artifact(rarityStyle, typeStyle);
            case CURSED     -> TooltipTheme.cursed(rarityStyle, typeStyle);
            case QUEST      -> TooltipTheme.quest(rarityStyle, typeStyle);
            case BLESSED    -> TooltipTheme.blessed(rarityStyle, typeStyle);
            case SACRED     -> TooltipTheme.sacred(rarityStyle, typeStyle);
            case CELESTIAL  -> TooltipTheme.celestial(rarityStyle, typeStyle);
            case DIVINE     -> TooltipTheme.divine(rarityStyle, typeStyle);
            case GODFORGED  -> TooltipTheme.godforged(rarityStyle, typeStyle);
            case FORBIDDEN  -> TooltipTheme.forbidden(rarityStyle, typeStyle);
            case CRUDE      -> TooltipTheme.crude(rarityStyle, typeStyle);
            case CALIBRATED -> TooltipTheme.calibrated(rarityStyle, typeStyle);
            case REINFORCED -> TooltipTheme.reinforced(rarityStyle, typeStyle);
            case PROTOTYPE  -> TooltipTheme.prototype(rarityStyle, typeStyle);
            case OVERCHARGED -> TooltipTheme.overcharged(rarityStyle, typeStyle);
            case MASTERWORK -> TooltipTheme.masterwork(rarityStyle, typeStyle);
            default         -> TooltipTheme.common(rarityStyle, typeStyle);
        };
    }

    private static int rarityBorderStyle(ItemRarity rarity) {
        return switch (rarity) {
            case UNCOMMON  -> TooltipBorderStyle.TICK;
            case RARE      -> TooltipBorderStyle.GEM;
            case EPIC      -> TooltipBorderStyle.RUNE;
            case LEGENDARY -> TooltipBorderStyle.CROWN;
            case MYTHICAL  -> TooltipBorderStyle.PRISMATIC;
            case ARTIFACT  -> TooltipBorderStyle.ANCIENT;
            case CURSED -> TooltipBorderStyle.CURSED;
            case FORBIDDEN -> TooltipBorderStyle.FORBIDDEN;
            case BLESSED   -> TooltipBorderStyle.BLESSED;
            case SACRED    -> TooltipBorderStyle.SACRED;
            case CELESTIAL -> TooltipBorderStyle.CELESTIAL;
            case DIVINE    -> TooltipBorderStyle.DIVINE;
            case GODFORGED -> TooltipBorderStyle.GODFORGED;
            case CRUDE       -> TooltipBorderStyle.CRUDE;
            case CALIBRATED  -> TooltipBorderStyle.CALIBRATED;
            case REINFORCED  -> TooltipBorderStyle.REINFORCED;
            case PROTOTYPE   -> TooltipBorderStyle.PROTOTYPE;
            case OVERCHARGED -> TooltipBorderStyle.OVERCHARGED;
            case MASTERWORK  -> TooltipBorderStyle.MASTERWORK;
            case QUEST -> TooltipBorderStyle.QUEST;
            default        -> TooltipBorderStyle.NONE;
        };
    }

    private static int typeBorderStyle(ItemType type) {
        if (type == null) return TooltipBorderStyle.NONE;
        return switch (type) {
            case MAGICAL    -> TooltipBorderStyle.RUNE;
            case RITUAL     -> TooltipBorderStyle.TYPE_RITUAL;
            case CONSUMABLE -> TooltipBorderStyle.TYPE_CONSUMABLE;
            case MATERIAL   -> TooltipBorderStyle.TYPE_MATERIAL;
            case POTION     -> TooltipBorderStyle.TYPE_POTION;
            case FUEL       -> TooltipBorderStyle.TYPE_FUEL;
            case FOOD       -> TooltipBorderStyle.TYPE_FOOD;
            case CURRENCY   -> TooltipBorderStyle.TYPE_CURRENCY;
            case WEAPON     -> TooltipBorderStyle.TYPE_WEAPON;
            case ARMOR      -> TooltipBorderStyle.TYPE_ARMOR;
            case TOOL       -> TooltipBorderStyle.TYPE_TOOL;
            case INDUSTRIAL -> TooltipBorderStyle.TYPE_INDUSTRIAL;
            case CABLE      -> TooltipBorderStyle.TYPE_CABLE;
            case BATTERY    -> TooltipBorderStyle.TYPE_BATTERY;
            case MACHINE    -> TooltipBorderStyle.TYPE_MACHINE;
            case COMPONENT  -> TooltipBorderStyle.TYPE_COMPONENT;
            case INGREDIENT -> TooltipBorderStyle.TYPE_INGREDIENT;
            default         -> TooltipBorderStyle.NONE;
        };
    }

    private static int darken(int color, float factor) {
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int color, float factor) {
        int r = Math.min(255, (int)(((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * factor));
        int g = Math.min(255, (int)(((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * factor));
        int b = Math.min(255, (int)((color & 0xFF) + (255 - (color & 0xFF)) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawAnimatedTitle(GuiGraphicsExtractor graphics, Font font, String text,
                                          int x, int y, int color, ItemRarity rarity, long timeMs) {
        switch (rarity) {
            case UNCOMMON -> TooltipAnimator.drawUncommonText(graphics, font, text, x, y, TooltipColors.forRarity(rarity), timeMs);
            case RARE      -> TooltipAnimator.drawSlowShineText(graphics, font, text, x, y, TooltipColors.forRarity(rarity), timeMs);
            case EPIC      -> TooltipAnimator.drawEpicWaveText(graphics, font, text, x, y, color, timeMs);
            case LEGENDARY -> TooltipAnimator.drawHeatDistortText(graphics, font, text, x, y, color, timeMs);
            case MYTHICAL  -> TooltipAnimator.drawMythicalText(graphics, font, text, x, y, color, timeMs);
            case ARTIFACT -> TooltipAnimator.drawArtifactText(graphics, font, text, x, y, color, timeMs);
            case BLESSED -> TooltipAnimator.drawBlessedText(graphics, font, text, x, y, color, timeMs);
            case SACRED    -> TooltipAnimator.drawSacredText(graphics, font, text, x, y, color, timeMs);
            case CELESTIAL -> TooltipAnimator.drawCelestialText(graphics, font, text, x, y, color, timeMs);
            case DIVINE    -> TooltipAnimator.drawRadianceText(graphics, font, text, x, y, color, timeMs);
            case GODFORGED -> TooltipAnimator.drawGodforgedText(graphics, font, text, x, y, color, timeMs);
            case CURSED     -> TooltipAnimator.drawCursedText(graphics, font, text, x, y, color, timeMs);
            case FORBIDDEN     -> TooltipAnimator.drawForbiddenText(graphics, font, text, x, y, color, timeMs);
            case QUEST      -> TooltipAnimator.drawQuestText(graphics, font, text, x, y, color, timeMs);
            case CRUDE      -> TooltipAnimator.drawCrudeText(graphics, font, text, x, y, color, timeMs);
            case CALIBRATED -> TooltipAnimator.drawCalibratedText(graphics, font, text, x, y, color, timeMs);
            case REINFORCED -> TooltipAnimator.drawReinforcedText(graphics, font, text, x, y, color, timeMs);
            case PROTOTYPE  -> TooltipAnimator.drawPrototypeGlitchText(graphics, font, text, x, y, color, timeMs);
            case OVERCHARGED -> TooltipAnimator.drawOverchargedText(graphics, font, text, x, y, color, timeMs);
            case MASTERWORK -> TooltipAnimator.drawMasterworkShineText(graphics, font, text, x, y, color, timeMs);
            default        -> graphics.text(font, text, x, y, color, true);
        }
    }

    private TotalityTooltipRenderer() {}
}