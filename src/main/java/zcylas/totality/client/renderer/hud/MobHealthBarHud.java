package zcylas.totality.client.renderer.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.Totality;
import zcylas.totality.client.mob.MobStatsClientCache;

public class MobHealthBarHud {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mob_health_bar");

    // ── Perception mastery levels (stubbed — wire to mastery system later) ────
    private static int getPerceptionMasteryLevel() {
        return 0; // TODO: read from ClientMasteryManager
    }

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COLOR_PANEL_BG     = 0xDD080C12;
    private static final int COLOR_BORDER       = 0xFF8B6914; // copper
    private static final int COLOR_BORDER_INNER = 0xFF3A2A08;
    private static final int COLOR_ACCENT       = 0xFF00CCFF; // cyan

    // Name colors
    private static final int COLOR_UNKNOWN      = 0xFFAAAAAA; // grey
    private static final int COLOR_WEAKER       = 0xFFFFFFFF; // white
    private static final int COLOR_EQUAL        = 0xFFFF9933; // orange
    private static final int COLOR_STRONGER     = 0xFFDD2222; // red
    private static final int COLOR_OVERWHELMING = 0xFFAA44FF; // purple
    private static final int COLOR_BOSS         = 0xFFFFCC00; // gold

    // Bar colors
    private static final int COLOR_HP_FILL      = 0xFFCC2222;
    private static final int COLOR_HP_BG        = 0xFF1A0505;
    private static final int COLOR_MANA_FILL    = 0xFF2244CC;
    private static final int COLOR_MANA_BG      = 0xFF050515;
    private static final int COLOR_POISE_FILL   = 0xFF44AA22;
    private static final int COLOR_POISE_BG     = 0xFF051505;
    private static final int COLOR_BAR_NUM      = 0xFFCCCCCC;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int NAME_H      = 10;
    private static final int BAR_H       = 9; // taller bar to fit numbers inside
    private static final int BAR_PAD_X   = 6;
    private static final int BAR_SPACING = 1;
    private static final int PANEL_PAD_Y = 2;
    private static final int PANEL_TOP_Y = 6; // distance from top of screen

    // ── State ─────────────────────────────────────────────────────────────────
    private static LivingEntity combatTarget    = null;
    private static int          combatTimer     = 0;
    private static LivingEntity crosshairTarget = null;
    private static final int COMBAT_DISPLAY_TICKS = 80; // 4 seconds

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            // Update crosshair target
            Entity looked = mc.crosshairPickEntity;
            crosshairTarget = (looked instanceof LivingEntity le
                    && !(le instanceof Player)
                    && mc.player.distanceTo(le) <= 24) ? le : null;

            LivingEntity target = getDisplayTarget(mc);
            if (target == null) return;

            boolean inCombat = isInCombat(target);
            int perception   = getPerceptionMasteryLevel();

            render(graphics, mc, target, inCombat, perception);
        });
    }

    public static void tick() {
        if (combatTimer > 0) combatTimer--;
        if (combatTimer <= 0) combatTarget = null;
    }

    public static void onPlayerHitMob(LivingEntity mob) {
        combatTarget = mob;
        combatTimer  = COMBAT_DISPLAY_TICKS;
    }

    // ── Target resolution ─────────────────────────────────────────────────────

    private static LivingEntity getDisplayTarget(Minecraft mc) {
        // Combat target takes priority
        if (combatTarget != null && combatTimer > 0
                && combatTarget.isAlive()
                && mc.player.distanceTo(combatTarget) <= 24) {
            return combatTarget;
        }
        return crosshairTarget;
    }

    private static boolean isInCombat(LivingEntity target) {
        if (target == combatTarget && combatTimer > 0) return true;
        if (target instanceof Mob mob && mob.getTarget() instanceof Player) return true;
        if (target.getLastHurtByMob() instanceof Player) return true;
        return false;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private static void render(GuiGraphicsExtractor g, Minecraft mc,
                               LivingEntity target, boolean inCombat, int perception) {
        int screenW = g.guiWidth();

        MobStatsClientCache.MobClientData mobData = MobStatsClientCache.get(target.getId());

        boolean showBars      = inCombat || perception >= 1;
        boolean showNameColor = inCombat || perception >= 2;
        boolean showExtraBars = inCombat && perception >= 3;
        boolean showRank      = inCombat;
        boolean showAc        = inCombat; // testing — gate with perception mastery later

        int panelW = getPanelW(mc, target, showBars, showExtraBars);
        int panelH = getPanelH(showBars, showExtraBars, showAc);

        int panelX = screenW / 2 - panelW / 2;
        int panelY = PANEL_TOP_Y;

        drawPanel(g, panelX, panelY, panelW, panelH);
        int cy = panelY + PANEL_PAD_Y;

        // ── Name ──
        String name   = buildName(target, showRank, mobData);
        int nameColor = showNameColor ? getThreatColor(target, mobData) : COLOR_UNKNOWN;
        g.text(mc.font, net.minecraft.network.chat.Component.literal(name),
                screenW / 2 - mc.font.width(name) / 2,
                cy + (NAME_H - 8) / 2, nameColor, true);
        cy += NAME_H;

        // ── HP bar ──
        if (showBars) {
            int barX = panelX + BAR_PAD_X;
            int barW = panelW - BAR_PAD_X * 2;
            cy += BAR_SPACING;
            drawBar(g, barX, cy, barW, BAR_H,
                    target.getHealth(), target.getMaxHealth(),
                    COLOR_HP_FILL, COLOR_HP_BG);
            int dispHp    = Math.round(target.getHealth() * 5);
            int dispMaxHp = Math.round(target.getMaxHealth() * 5);
            String hpText = dispHp + " / " + dispMaxHp;
            g.text(mc.font, net.minecraft.network.chat.Component.literal(hpText),
                    screenW / 2 - mc.font.width(hpText) / 2,
                    cy + (BAR_H - 8) / 2, 0xFFFFFFFF, true);
            cy += BAR_H;
        }

        // ── AC (testing) ──
        if (showAc && mobData != null) {
            cy += BAR_SPACING + 1;
            String acText = "AC " + mobData.ac();
            g.text(mc.font, net.minecraft.network.chat.Component.literal(acText),
                    screenW / 2 - mc.font.width(acText) / 2,
                    cy, COLOR_ACCENT, true);
        }
    }

    // ── Panel drawing ─────────────────────────────────────────────────────────

    private static void drawPanel(GuiGraphicsExtractor g,
                                  int x, int y, int w, int h) {
        // Background
        g.fill(x, y, x + w, y + h, COLOR_PANEL_BG);

        // Outer copper border
        g.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        g.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);

        // Inner subtle border
        g.fill(x + 2, y + 2, x + w - 2, y + 3,     COLOR_BORDER_INNER);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, COLOR_BORDER_INNER);
        g.fill(x + 2, y + 2, x + 3,     y + h - 2, COLOR_BORDER_INNER);
        g.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, COLOR_BORDER_INNER);

        // Corner accents
        drawCorner(g, x,     y,     true,  true);
        drawCorner(g, x + w, y,     false, true);
        drawCorner(g, x,     y + h, true,  false);
        drawCorner(g, x + w, y + h, false, false);
    }

    private static void drawCorner(GuiGraphicsExtractor g, int x, int y,
                                   boolean left, boolean top) {
        int dx = left ? 1 : -1, dy = top ? 1 : -1;
        g.fill(x, y, x + dx * 8, y + dy, COLOR_BORDER);
        g.fill(x, y, x + dx, y + dy * 8, COLOR_BORDER);
        // Bright copper tip
        g.fill(x + dx, y + dy, x + dx * 3, y + dy * 2, 0xFFD4A030);
        g.fill(x + dx, y + dy, x + dx * 2, y + dy * 3, 0xFFD4A030);
    }


    private static void drawBar(GuiGraphicsExtractor g,
                                int x, int y, int w, int h,
                                float value, float max,
                                int fillColor, int bgColor) {
        // Background — slightly lighter than before
        g.fill(x, y, x + w, y + h, bgColor);
        // Subtle inner border on bg
        g.fill(x, y, x + w, y + 1, 0xFF2A0808);

        float pct   = Mth.clamp(value / max, 0f, 1f);
        int   fillW = (int)(pct * w);
        if (fillW > 0) {
            // Main fill
            g.fill(x, y, x + fillW, y + h, fillColor);
            // Brighter line on top of fill
            g.fill(x, y, x + fillW, y + 1, 0xFFFF6666);
            // Subtle dark line at bottom of fill
            g.fill(x, y + h - 1, x + fillW, y + h, 0x44000000);
        }
    }

    // ── Name + rank ───────────────────────────────────────────────────────────

    private static String buildName(LivingEntity target, boolean showRank,
                                    MobStatsClientCache.MobClientData mobData) {
        String name = target.getName().getString();
        if (showRank && mobData != null) {
            String rank = zcylas.totality.api.mob.stats.MobRank.values()[
                    Math.min(mobData.rankOrdinal(),
                            zcylas.totality.api.mob.stats.MobRank.values().length - 1)].name();
            return name + " [" + rank + "]";
        }
        return name;
    }

    // ── Threat color ──────────────────────────────────────────────────────────

    private static int getThreatColor(LivingEntity target,
                                      MobStatsClientCache.MobClientData mobData) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return COLOR_UNKNOWN;

        if (mobData != null) {
            // Use real level comparison (stub player level as 1 until ClientStatsManager has level)
            int playerLevel = zcylas.totality.api.rpg.stats.ClientStatsManager.getLevel();
            int diff = mobData.level() - playerLevel;
            if (diff >= 20) return COLOR_OVERWHELMING;
            if (diff >= 10) return COLOR_STRONGER;
            if (diff >= 0)  return COLOR_EQUAL;
            return COLOR_WEAKER;
        }

        // Fallback — HP comparison
        float maxHp = target.getMaxHealth();
        float playerMaxHp = mc.player.getMaxHealth();
        if (maxHp > playerMaxHp * 3f) return COLOR_OVERWHELMING;
        if (maxHp > playerMaxHp * 1.5f) return COLOR_STRONGER;
        if (maxHp > playerMaxHp * 0.75f) return COLOR_EQUAL;
        return COLOR_WEAKER;
    }

    // ── Rank ─────────────────────────────────────────────────────────────────

    private static String getRank(LivingEntity target) {
        // TODO: replace with proper mob level system
        float maxHp = target.getMaxHealth();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "E";
        float playerMaxHp = mc.player.getMaxHealth();

        if (maxHp > playerMaxHp * 10f) return "S";
        if (maxHp > playerMaxHp * 5f)  return "A";
        if (maxHp > playerMaxHp * 3f)  return "B";
        if (maxHp > playerMaxHp * 1.5f) return "C";
        if (maxHp > playerMaxHp * 0.75f) return "D";
        return "E";
    }

    // ── Dynamic panel sizing ──────────────────────────────────────────────────

    private static int getPanelW(Minecraft mc, LivingEntity target,
                                 boolean showBars, boolean showExtraBars) {
        int nameW = mc.font.width(buildName(target, false, null)) + 24; // less padding
        int minW  = showExtraBars ? 180 : showBars ? 140 : 80;
        int maxW  = showExtraBars ? 320 : showBars ? 240 : 160;
        return Mth.clamp(nameW, minW, maxW);
    }

    private static int getPanelH(boolean showBars, boolean showExtraBars, boolean showAc) {
        if (!showBars) return NAME_H + PANEL_PAD_Y * 2;
        int h = PANEL_PAD_Y + NAME_H + BAR_SPACING + BAR_H + PANEL_PAD_Y;
        if (showAc) h += BAR_SPACING + 9;
        if (showExtraBars) h += (BAR_SPACING + BAR_H) * 2;
        return h;
    }
}