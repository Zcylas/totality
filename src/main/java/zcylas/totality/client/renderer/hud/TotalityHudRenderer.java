package zcylas.totality.client.renderer.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.client.renderer.hud.context.MagicContextHud;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.stamina.ClientStaminaManager;

public class TotalityHudRenderer {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "totality_hud");

    private static final int BG_PNG_W      = 83;
    private static final int BG_PNG_H      = 8;
    private static final int FILL_PNG_W    = 73;
    private static final int FILL_PNG_H    = 4;

    private static final int BG_WIDTH      = 83;
    private static final int BG_HEIGHT     = 8;
    private static final int DRAW_FILL_W   = 73;
    private static final int DRAW_FILL_H   = 4;

    private static final int FILL_OFFSET_X = 9;
    private static final int FILL_OFFSET_Y = 2;

    private static final int BAR_SPACING   = 3;
    private static final int BOTTOM_MARGIN = 2;

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, old -> (graphics, delta) -> {});
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR,  old -> (graphics, delta) -> {});
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR,   old -> (graphics, delta) -> {});

        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;

            int screenW = graphics.guiWidth();
            int screenH = graphics.guiHeight();

            int leftX    = 6;
            int staminaY = screenH - BOTTOM_MARGIN - BG_HEIGHT;  // bottom
            int manaY    = staminaY - BAR_SPACING  - BG_HEIGHT;  // middle
            int hpY      = manaY    - BAR_SPACING  - BG_HEIGHT;  // top

            // ── LEFT SIDE — HP, Stamina, Mana ──
            drawBar(graphics, client, leftX, hpY,
                    TotalityGuiSprites.HUD_HEALTH_FILL,
                    client.player.getHealth(), client.player.getMaxHealth(),
                    RpgDisplayUtils.toDisplayHp(client.player.getHealth()),
                    RpgDisplayUtils.toDisplayHp(client.player.getMaxHealth()));

            int stamina    = ClientStaminaManager.getStamina();
            int maxStamina = ClientStaminaManager.getMaxStamina();
            drawBar(graphics, client, leftX, staminaY,
                    TotalityGuiSprites.HUD_STAMINA_FILL,
                    stamina, maxStamina, stamina, maxStamina);

            int mana    = ClientManaManager.getMana();
            int maxMana = ClientManaManager.getMaxMana();
            if (maxMana > 0) {
                drawBar(graphics, client, leftX, manaY,
                        TotalityGuiSprites.HUD_MANA_FILL,
                        mana, maxMana, mana, maxMana);
            }

            // ── RIGHT SIDE — Hunger ──
            int rightX = screenW - BG_WIDTH - 6;
            drawBarMirrored(graphics, client, rightX, hpY,
                    TotalityGuiSprites.HUD_HUNGER_FILL,
                    client.player.getFoodData().getFoodLevel(), 20,
                    client.player.getFoodData().getFoodLevel(), 20);
            // TODO: Thirst aligned with Stamina
            // TODO: Temperature aligned with Mana

            // ── CONTEXT RENDERERS ──
            MagicContextHud.render(graphics, client, screenW, screenH);
            // TODO: CombatContextHud.render(graphics, client, screenW, screenH);
            // TODO: ToolContextHud.render(graphics, client, screenW, screenH);
            // TODO: TargetContextHud.render(graphics, client, screenW, screenH);
        });
    }

    /**
     * Formats a value for display — abbreviates large numbers to keep the HUD compact.
     * Examples: 999 → "999", 1000 → "1k", 1500 → "1.5k", 10000 → "10k"
     */
    private static String formatValue(int value) {
        if (value >= 10000) {
            return (value / 1000) + "k";
        } else if (value >= 1000) {
            String formatted = String.format("%.1f", value / 1000f);
            if (formatted.endsWith(".0")) formatted = formatted.substring(0, formatted.length() - 2);
            return formatted + "k";
        }
        return String.valueOf(value);
    }

    private static String buildText(int current, int max) {
        return formatValue(current) + " / " + formatValue(max);
    }

    /**
     * Left-side bar: background + fill (left to right) + text to the right.
     */
    private static void drawBar(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            int x, int y,
            Identifier fillSprite,
            float value, float maxValue,
            int displayCurrent, int displayMax
    ) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.HUD_BAR_BACKGROUND,
                BG_PNG_W, BG_PNG_H,
                0, 0,
                x, y,
                BG_WIDTH, BG_HEIGHT);

        if (maxValue > 0) {
            float pct = Mth.clamp(value / maxValue, 0f, 1f);
            int filledW = (int)(pct * DRAW_FILL_W);
            if (filledW > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        fillSprite,
                        FILL_PNG_W, FILL_PNG_H,
                        0, 0,
                        x + FILL_OFFSET_X, y + FILL_OFFSET_Y,
                        filledW, DRAW_FILL_H);
            }
        }

        String text = buildText(displayCurrent, displayMax);
        graphics.text(client.font, text,
                x + BG_WIDTH + 4,
                y + (BG_HEIGHT - client.font.lineHeight) / 2,
                0xFFCCCCCC, true);
    }

    /**
     * Right-side bar: background + fill (right to left) + text to the left.
     */
    private static void drawBarMirrored(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            int x, int y,
            Identifier fillSprite,
            float value, float maxValue,
            int displayCurrent, int displayMax
    ) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.HUD_BAR_BACKGROUND,
                BG_PNG_W, BG_PNG_H,
                0, 0,
                x, y,
                BG_WIDTH, BG_HEIGHT);

        if (maxValue > 0) {
            float pct = Mth.clamp(value / maxValue, 0f, 1f);
            int filledW = (int)(pct * DRAW_FILL_W);
            if (filledW > 0) {
                int fillStartX = x + FILL_OFFSET_X + (DRAW_FILL_W - filledW);
                int textureX   = FILL_PNG_W - (int)(pct * FILL_PNG_W);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        fillSprite,
                        FILL_PNG_W, FILL_PNG_H,
                        textureX, 0,
                        fillStartX, y + FILL_OFFSET_Y,
                        filledW, DRAW_FILL_H);
            }
        }

        String text = buildText(displayCurrent, displayMax);
        int textW = client.font.width(text);
        graphics.text(client.font, text,
                x - textW - 4,
                y + (BG_HEIGHT - client.font.lineHeight) / 2,
                0xFFCCCCCC, true);
    }
}