package zcylas.totality.client.renderer.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.api.rpg.classes.ClientClassManager;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.combat.ArmorClass;
import zcylas.totality.api.rpg.combat.armor.VanillaArmorStats;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.presentation.ClientResourcePresentationResolver;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatterRegistry;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.ClientStatsManager;
import zcylas.totality.client.combat.DualWieldTracker;
import zcylas.totality.client.equipment.ClientEquipmentManager;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.client.hud.resource.ISecondaryResource;
import zcylas.totality.client.hud.resource.SecondaryResourceRegistry;
import zcylas.totality.client.renderer.hud.context.AbilityContextHud;
import zcylas.totality.client.renderer.hud.context.MagicContextHud;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.stamina.ClientStaminaManager;
import zcylas.totality.util.color.ColorUtils;
import zcylas.totality.util.math.SmoothValue;

import java.util.List;

public class TotalityHudRenderer {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "totality_hud");

    // Vanilla's own crosshair attack-indicator sprites — reused as-is for the offhand's mirrored
    // indicator (drawn above the crosshair instead of below) so it matches vanilla's style exactly.
    private static final Identifier CROSSHAIR_ATTACK_INDICATOR_FULL =
            Identifier.fromNamespaceAndPath("minecraft", "hud/crosshair_attack_indicator_full");
    private static final Identifier CROSSHAIR_ATTACK_INDICATOR_BACKGROUND =
            Identifier.fromNamespaceAndPath("minecraft", "hud/crosshair_attack_indicator_background");
    private static final Identifier CROSSHAIR_ATTACK_INDICATOR_PROGRESS =
            Identifier.fromNamespaceAndPath("minecraft", "hud/crosshair_attack_indicator_progress");

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

    // ── Smooth bar animation ──────────────────────────────────────────────────
    // SmoothValue lerps the fill percentage so bars animate instead of snapping.
    private static final SmoothValue hpSmooth      = new SmoothValue(200);
    private static final SmoothValue staminaSmooth = new SmoothValue(100);
    private static final SmoothValue manaSmooth    = new SmoothValue(150);
    private static final SmoothValue hungerSmooth  = new SmoothValue(800);
    // On the very first render frame, snap all bars to their real values instead
    // of animating from 0 — otherwise bars start invisible until the lerp catches up.
    private static boolean smoothsReady = false;

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, old -> (graphics, delta) -> {});
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR,  old -> (graphics, delta) -> {});
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR,   old -> (graphics, delta) -> {});

        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gui.hud.isHidden()) return;

            int screenW = graphics.guiWidth();
            int screenH = graphics.guiHeight();

            int leftX    = 6;
            int staminaY = screenH - BOTTOM_MARGIN - BG_HEIGHT;  // bottom
            int manaY    = staminaY - BAR_SPACING  - BG_HEIGHT;  // middle
            int hpY      = manaY    - BAR_SPACING  - BG_HEIGHT;  // top
            // Power attack flash — timer advanced by PowerAttackFlash's own END_CLIENT_TICK
            // registration (correction pass), never here; this reads current state only.
            if (PowerAttackFlash.isActive()) {
                int flashColor = ColorUtils.setAlpha(0xFFFF6600, (int)(PowerAttackFlash.getAlpha() * 255));
                graphics.fill(0, 0, screenW, screenH, flashColor);
            }
            renderOffhandAttackIndicator(graphics, client, screenW, screenH);
            // ── LEFT SIDE — HP, Stamina, Mana ──
            float hp    = client.player.getHealth();
            float maxHp = client.player.getMaxHealth();
            // Phase 3C: presentation source migrated to the trusted Generic client Resource view,
            // falling back to the legacy managers only when the Generic query is unavailable — see
            // ClientResourcePresentationResolver's javadoc for the full fallback policy. Health/Food
            // stay native-backed (unchanged) per Phase 3C scope.
            ClientResourcePresentationResolver.ScalarPresentation staminaView =
                    ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.STAMINA,
                            () -> ClientStaminaManager.getStamina(), () -> ClientStaminaManager.getMaxStamina());
            int stamina    = (int) staminaView.current();
            int maxStamina = (int) staminaView.maximum();
            ClientResourcePresentationResolver.ScalarPresentation manaView =
                    ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.MANA,
                            () -> ClientManaManager.getMana(), () -> ClientManaManager.getMaxMana());
            int mana    = (int) manaView.current();
            int maxMana = (int) manaView.maximum();
            int hunger  = client.player.getFoodData().getFoodLevel();

            double hpPct      = maxHp > 0 ? hp / maxHp : 0;
            double staminaPct = maxStamina > 0 ? (double) stamina / maxStamina : 0;
            double manaPct    = maxMana > 0 ? (double) mana / maxMana : 0;
            double hungerPct  = hunger / 20.0;

            if (!smoothsReady) {
                // First frame — snap immediately so bars are visible right away.
                hpSmooth.setStart(hpPct);
                staminaSmooth.setStart(staminaPct);
                manaSmooth.setStart(manaPct);
                hungerSmooth.setStart(hungerPct);
                smoothsReady = true;
            } else {
                // Only call set() when the target changes — set() resets the timestamp
                // to "now", so calling it every frame alongside tick() gives tick() 0ms
                // of elapsed time and the bar never moves.
                if (hpPct      != hpSmooth.aimed())      hpSmooth.set(hpPct);
                if (staminaPct != staminaSmooth.aimed())  staminaSmooth.set(staminaPct);
                if (manaPct    != manaSmooth.aimed())     manaSmooth.set(manaPct);
                if (hungerPct  != hungerSmooth.aimed())   hungerSmooth.set(hungerPct);
                hpSmooth.tick();
                staminaSmooth.tick();
                manaSmooth.tick();
                hungerSmooth.tick();
            }

            // Bar fill keeps using the native hp/maxHp ratio (hpSmooth, above) unchanged; only the
            // numeric text is queried through PlayerResourceService + the shared totality:health
            // formatter. Falls back to the pre-existing RpgDisplayUtils values (which now compute
            // the identical result through the same shared conversion) if the resource somehow
            // isn't queryable, so this can never regress the number shown.
            long[] hpDisplay = resourceDisplayCurrentMax(client.player, PlayerResourceIds.HEALTH,
                    RpgDisplayUtils.toDisplayHp(hp), RpgDisplayUtils.toDisplayHp(maxHp));
            drawBarSmooth(graphics, client, leftX, hpY,
                    TotalityGuiSprites.HUD_HEALTH_FILL,
                    hpSmooth,
                    (int) hpDisplay[0],
                    (int) hpDisplay[1]);

            drawBarSmooth(graphics, client, leftX, staminaY,
                    TotalityGuiSprites.HUD_STAMINA_FILL,
                    staminaSmooth, stamina, maxStamina);

            if (maxMana > 0) {
                drawBarSmooth(graphics, client, leftX, manaY,
                        TotalityGuiSprites.HUD_MANA_FILL,
                        manaSmooth, mana, maxMana);
            }

            // ── AC INDICATOR (testing — replaced during HUD redesign) ──
            int ac = calculateClientAC(client);
            graphics.text(client.font, "AC " + ac,
                    leftX,
                    hpY - client.font.lineHeight - 2,  // ← above HP bar
                    0xFF00CCFF, true);

            // ── RIGHT SIDE — Hunger ──
            // Bar fill keeps using the native hunger/20 ratio (hungerSmooth, above) unchanged; the
            // displayed 0-100 numbers come from PlayerResourceService + the shared totality:food
            // formatter (canonical §19.8: Food aligns to the same 100 baseline as Health/Mana/Stamina).
            int rightX = screenW - BG_WIDTH - 6;
            // The fallback itself must not bypass the shared conversion either — Food's unitScale
            // is 1, so this is the same totality:food 5/1 conversion the primary (query) path
            // uses, applied directly to the raw mechanical values rather than a hardcoded * 5.
            long hungerFallbackCurrent = ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(hunger, 1);
            long hungerFallbackMax = ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(20, 1);
            long[] hungerDisplay = resourceDisplayCurrentMax(
                    client.player, PlayerResourceIds.FOOD, hungerFallbackCurrent, hungerFallbackMax);
            drawBarMirroredSmooth(graphics, client, rightX, hpY,
                    TotalityGuiSprites.HUD_HUNGER_FILL,
                    hungerSmooth, (int) hungerDisplay[0], (int) hungerDisplay[1]);
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
     * Queries {@code resourceId} through {@link PlayerResourceService} and converts the result to
     * display units via the shared {@link ResourceValueFormatterRegistry} formatter, returning
     * {@code [displayCurrent, displayMax]}. Falls back to the given pre-computed values (never a
     * fabricated zero) if the resource isn't queryable or has no registered formatter — defensive
     * only; both {@code totality:health} and {@code totality:food} are always registered in
     * production by {@code ProductionResourceDefinitions}.
     */
    private static long[] resourceDisplayCurrentMax(
            Player player, Identifier resourceId, long fallbackCurrent, long fallbackMax) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, resourceId);
        if (result instanceof ResourceQueryResult.Success success) {
            var formatter = ResourceValueFormatterRegistry.INSTANCE.get(resourceId);
            if (formatter.isPresent()) {
                return new long[] {
                        formatter.get().toDisplayCurrent(success.snapshot()),
                        formatter.get().toDisplayMaximum(success.snapshot())
                };
            }
        }
        return new long[] { fallbackCurrent, fallbackMax };
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
     * Smooth left-side bar — uses a SmoothValue for the fill percentage so it
     * animates when the underlying value changes rather than snapping instantly.
     */
    private static void drawBarSmooth(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            int x, int y,
            Identifier fillSprite,
            SmoothValue smooth,
            int displayCurrent, int displayMax
    ) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.HUD_BAR_BACKGROUND,
                BG_PNG_W, BG_PNG_H,
                0, 0,
                x, y,
                BG_WIDTH, BG_HEIGHT);

        float pct = (float) Mth.clamp(smooth.current(), 0.0, 1.0);
        int filledW = (int)(pct * DRAW_FILL_W);
        if (filledW > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    fillSprite,
                    FILL_PNG_W, FILL_PNG_H,
                    0, 0,
                    x + FILL_OFFSET_X, y + FILL_OFFSET_Y,
                    filledW, DRAW_FILL_H);
        }

        String text = buildText(displayCurrent, displayMax);
        graphics.text(client.font, text,
                x + BG_WIDTH + 4,
                y + (BG_HEIGHT - client.font.lineHeight) / 2,
                0xFFCCCCCC, true);
    }

    /**
     * Smooth right-side bar (right-to-left fill).
     */
    private static void drawBarMirroredSmooth(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            int x, int y,
            Identifier fillSprite,
            SmoothValue smooth,
            int displayCurrent, int displayMax
    ) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.HUD_BAR_BACKGROUND,
                BG_PNG_W, BG_PNG_H,
                0, 0,
                x, y,
                BG_WIDTH, BG_HEIGHT);

        float pct = (float) Mth.clamp(smooth.current(), 0.0, 1.0);
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

        String text = buildText(displayCurrent, displayMax);
        int textW = client.font.width(text);
        graphics.text(client.font, text,
                x - textW - 4,
                y + (BG_HEIGHT - client.font.lineHeight) / 2,
                0xFFCCCCCC, true);
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
            int dim     = ColorUtils.setAlpha(color, 0x33);

            if (resource.getDisplayType() == ISecondaryResource.DisplayType.PIPS) {
                int pipSz  = 10;
                int pipGap = 2;
                int totalW = max * (pipSz + pipGap) - pipGap;
                int pipX   = rightEdgeX - totalW;

                Identifier activeSprite = resource.getActivePipSprite();
                Identifier spentSprite  = resource.getSpentPipSprite();

                for (int i = 0; i < max; i++) {
                    boolean filled = i < current;
                    if (activeSprite != null && spentSprite != null) {
                        // Sprite-based pip
                        Identifier sprite = filled ? activeSprite : spentSprite;
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                                sprite, pipSz, pipSz, 0, 0,
                                pipX, y, pipSz, pipSz);
                    } else {
                        // Fallback: colored fill
                        if (filled) {
                            graphics.fill(pipX, y, pipX + pipSz, y + pipSz, color);
                        } else {
                            graphics.fill(pipX, y, pipX + pipSz, y + pipSz, 0x22FFFFFF);
                            graphics.fill(pipX,          y,             pipX + pipSz, y + 1,            dim);
                            graphics.fill(pipX,          y + pipSz - 1, pipX + pipSz, y + pipSz,        dim);
                            graphics.fill(pipX,          y,             pipX + 1,     y + pipSz,         dim);
                            graphics.fill(pipX + pipSz - 1, y,          pipX + pipSz, y + pipSz,         dim);
                        }
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
    /**
     * Offhand's own attack-readiness indicator — mirrors vanilla's crosshair attack indicator
     * (same sprites, same style) but drawn above the crosshair instead of below, since vanilla
     * only ever shows one such indicator (tied to the mainhand). Only shown while dual-wielding.
     */
    private static void renderOffhandAttackIndicator(GuiGraphicsExtractor graphics, Minecraft client, int screenW, int screenH) {
        if (client.player == null || !client.options.getCameraType().isFirstPerson()) return;
        if (client.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) return;

        ItemStack main = client.player.getMainHandItem();
        ItemStack off  = client.player.getOffhandItem();
        boolean dualWielding = (main.is(ItemTags.SWORDS) || main.getItem() instanceof TotalityMeleeWeaponItem)
                && (off.is(ItemTags.SWORDS) || off.getItem() instanceof TotalityMeleeWeaponItem);
        if (!dualWielding) return;

        float scale = DualWieldTracker.getOffhandAttackStrengthScale(client.player);

        // Vanilla's indicator top edge sits at centerY + 9 (centerY - 7 + 16), i.e. its near edge
        // to the crosshair is 9px away. Mirror that same 9px near-edge distance on the opposite
        // side: the mirrored sprite's BOTTOM edge sits at centerY - 9, extending upward from there.
        int x = screenW / 2 - 8;
        int bottomY = screenH / 2 - 9;

        // MIN_CROSSHAIR_ATTACK_SPEED parity: vanilla only shows the "ready" icon when the weapon
        // is slow enough (delay > 5 ticks) that the indicator is actually meaningful.
        boolean readyOnTarget = client.crosshairPickEntity instanceof LivingEntity living
                && living.isAlive() && scale >= 1.0f
                && DualWieldTracker.getOffhandAttackStrengthDelay(client.player) > 5.0;

        if (readyOnTarget) {
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_FULL, x, bottomY - 16, 16, 16);
        } else if (scale < 1.0f) {
            int y = bottomY - 4;
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_BACKGROUND, x, y, 16, 4);
            int filled = (int)(scale * 17);
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_PROGRESS,
                    16, 4, 0, 0, x, y, filled, 4);
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

        // Equipment bonus (rings, etc.)
        base += ClientEquipmentManager.getAcBonus(client.player.getUUID());

        return base;
    }
}