package zcylas.totality.client.renderer.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.api.rpg.classes.ClientClassManager;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.combat.ArmorClass;
import zcylas.totality.api.rpg.combat.armor.VanillaArmorStats;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.ClientStatsManager;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.client.hud.resource.ISecondaryResource;
import zcylas.totality.client.hud.resource.SecondaryResourceRegistry;
import zcylas.totality.client.renderer.hud.context.AbilityContextHud;
import zcylas.totality.client.renderer.hud.context.MagicContextHud;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.stamina.ClientStaminaManager;

import java.util.List;

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
            // Power attack flash
            PowerAttackFlash.tick();
            if (PowerAttackFlash.isActive()) {
                int flashColor = (int)(PowerAttackFlash.getAlpha() * 255) << 24 | 0xFF6600;
                graphics.fill(0, 0, screenW, screenH, flashColor);
            }
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

            // ── AC INDICATOR (testing — replaced during HUD redesign) ──
            int ac = calculateClientAC(client);
            graphics.text(client.font, "AC " + ac,
                    leftX,
                    hpY - client.font.lineHeight - 2,  // ← above HP bar
                    0xFF00CCFF, true);

            // ── RIGHT SIDE — Hunger ──
            int rightX = screenW - BG_WIDTH - 6;
            drawBarMirrored(graphics, client, rightX, hpY,
                    TotalityGuiSprites.HUD_HUNGER_FILL,
                    client.player.getFoodData().getFoodLevel(), 20,
                    client.player.getFoodData().getFoodLevel(), 20);
            // ── RIGHT SIDE — Secondary Resources (below hunger bar) ──
            drawSecondaryResources(graphics, client, screenW - 6, hpY + BG_HEIGHT + BAR_SPACING);
            // TODO: Thirst aligned with Stamina
            // TODO: Temperature aligned with Mana

            // ── CONTEXT RENDERERS ──
            MagicContextHud.render(graphics, client, screenW, screenH);
            AbilityContextHud.render(graphics, client, screenW, screenH);
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

    private static void drawSecondaryResources(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            int rightEdgeX, int y) {

        List<ISecondaryResource> active = SecondaryResourceRegistry.all()
                .stream()
                .filter(r -> r.shouldShow(client) && r.getMax(client) > 0)
                .toList();

        if (active.isEmpty()) return;

        for (ISecondaryResource resource : active) {
            int current = resource.getCurrent(client);
            int max     = resource.getMax(client);
            int color   = resource.getColor();
            int dim     = (color & 0x00FFFFFF) | 0x33000000;

            if (resource.getDisplayType() == ISecondaryResource.DisplayType.PIPS) {
                int pipSz  = 8;
                int pipGap = 2;
                int totalW = max * (pipSz + pipGap) - pipGap;
                int pipX   = rightEdgeX - totalW;

                for (int i = 0; i < max; i++) {
                    boolean filled = i < current;
                    if (filled) {
                        graphics.fill(pipX, y, pipX + pipSz, y + pipSz, color);
                    } else {
                        graphics.fill(pipX, y, pipX + pipSz, y + pipSz, 0x22FFFFFF);
                        graphics.fill(pipX,          y,            pipX + pipSz, y + 1,            dim);
                        graphics.fill(pipX,          y + pipSz - 1, pipX + pipSz, y + pipSz,       dim);
                        graphics.fill(pipX,          y,            pipX + 1,     y + pipSz,        dim);
                        graphics.fill(pipX + pipSz - 1, y,         pipX + pipSz, y + pipSz,        dim);
                    }
                    pipX += pipSz + pipGap;
                }
                y += pipSz + 3;

            } else { // BAR
                int barW = BG_WIDTH - 10;
                int barH = 4;
                int barX = rightEdgeX - barW;
                int fill = max > 0 ? (int)((float) current / max * barW) : 0;

                graphics.fill(barX, y, barX + barW, y + barH, 0x44000000);
                if (fill > 0)
                    graphics.fill(barX + barW - fill, y, barX + barW, y + barH, color);
                graphics.fill(barX, y, barX + barW, y + 1, dim);
                graphics.fill(barX, y + barH - 1, barX + barW, y + barH, dim);
                y += barH + 3;
            }
        }
    }
    private static int calculateClientAC(Minecraft client) {
        if (client.player == null) return 10;

        int totalAc = 0;
        ArmorClass.ArmorType heaviest = null;
        boolean hasArmor = false;

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = client.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            VanillaArmorStats.PieceStats piece = VanillaArmorStats.get(stack.getItem());
            if (piece == null) continue;
            hasArmor = true;
            totalAc += piece.ac();
            if (heaviest == null || piece.type().ordinal() > heaviest.ordinal())
                heaviest = piece.type();
        }

        int dexMod = ClientStatsManager.getModifier(AbilityScore.DEX);
        int base;

        if (!hasArmor) {
            // Barbarian Unarmored Defense: 10 + STR + CON
            if (ClientClassManager.getClassLevels()
                    .containsKey(TotalityClasses.BARBARIAN_ID)) {
                base = 10 + ClientStatsManager.getModifier(AbilityScore.STR)
                        + ClientStatsManager.getModifier(AbilityScore.CON);
            } else {
                base = 10 + dexMod;
            }
        } else {
            int cappedDex = switch (heaviest) {
                case LIGHT  -> dexMod;
                case MEDIUM -> Math.min(dexMod, 2);
                case HEAVY  -> 0;
            };
            base = (10 + totalAc) + cappedDex;
        }

        // Shield bonus
        ItemStack offhand = client.player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.getItem() instanceof ShieldItem) base += 2;

        return base;
    }
}