package zcylas.totality.screen.alchemy;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.skills.alchemy.*;
import zcylas.totality.api.rpg.skills.core.ClientSkillsManager;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.networking.alchemy.BrewPayload;
import zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Apothecary Table screen — restyled to match Totality's dark blue/cyan theme.
 *
 * Changes from original:
 *   - Colors match the rest of the mod UI (dark blue bg, cyan accents, etc.)
 *   - Alchemy XP bar added bottom-right (like Skyrim's skill bar)
 *   - Continuous crafting: after brewing, same ingredients are re-selected
 *     automatically if still available in inventory
 */
public class ApothecaryTableScreen extends Screen {

    // ── Colors (matching Totality UI theme) ───────────────────────────────────
    private static final int COLOR_BG           = 0xFF000005;
    private static final int COLOR_BORDER       = 0xFF0A5070;
    private static final int COLOR_BORDER_GLOW  = 0x440A8FBF;
    private static final int COLOR_VALUE        = 0xFF00CCFF;
    private static final int COLOR_LABEL        = 0xFF5599BB;
    private static final int COLOR_SEPARATOR    = 0xFF0A3A5A;
    private static final int COLOR_ROW_SEL      = 0xCC002844;
    private static final int COLOR_ROW_HOV      = 0x88001830;
    private static final int COLOR_ROW_EVEN     = 0x11FFFFFF;
    private static final int COLOR_HEADER_BG    = 0xDD000005;
    private static final int COLOR_CHOSEN       = 0xBB003355;
    private static final int COLOR_BOTTOM       = 0xDD000005;
    private static final int COLOR_XP_BG        = 0xFF001830;
    private static final int COLOR_XP_FILL      = 0xFF0066AA;
    private static final int COLOR_BTN          = 0xFF003355;
    private static final int COLOR_BTN_HOVER    = 0xFF0066AA;
    private static final int COLOR_BTN_CRAFT    = 0xFF004422;
    private static final int COLOR_BTN_CRAFT_H  = 0xFF006633;
    private static final int COLOR_GREEN        = 0xFF44FF88;
    private static final int COLOR_RED          = 0xFFFF4444;
    private static final int COLOR_GOLD         = 0xFFFFCC44;
    private static final int COLOR_UNKNOWN      = 0xFF334455;
    private static final int COLOR_POPUP_BG     = 0xEE000005;

    // ── Layout ────────────────────────────────────────────────────────────────
    private int LEFT_W;
    private int MID_W;
    private int MID_X;
    private int RIGHT_W;
    private int RIGHT_X;
    private int TOTAL_H;
    private int CONTENT_H;
    private int guiLeft;
    private int guiTop;

    private static final int HEADER_H     = 24;
    private static final int ROW_H        = 16;
    private static final int BOTTOM_BAR_H = 28;
    private static final int XP_BAR_W     = 160;
    private static final int XP_BAR_H     = 5;
    private static final int COL_GAP      = 6;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<IngredientEntry> allIngredients      = new ArrayList<>();
    private final List<IngredientEntry> filteredIngredients = new ArrayList<>();
    private final List<IngredientEntry> selected            = new ArrayList<>();

    // For continuous crafting — stores ingredient IDs of last brew
    private List<Identifier> lastBrewedIngredientIds = new ArrayList<>();

    private AlchemyEffect effectFilter    = null;
    private boolean middleVisible         = false;
    private boolean rightVisible  = false;
    private IngredientEntry hoveredIngredient = null;

    private int effectScroll     = 0;
    private int ingredientScroll = 0;

    private BrewingLogic.BrewResult currentBrewResult = null;

    // ── Popup state ───────────────────────────────────────────────────────────
    private boolean showPopup    = false;
    private String popupPotionName = "";
    private List<zcylas.totality.networking.alchemy.BrewResultPayload.DiscoveredEffect>
            popupEffects = new ArrayList<>();

    public ApothecaryTableScreen() {
        super(Component.literal("Alchemy"));
    }

    @Override
    protected void init() {
        super.init();
        scanInventory();
        computeLayout();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void computeLayout() {
        int padding = 22;

        int leftContent = font.width("INGREDIENTS");
        for (AlchemyEffect effect : getDiscoveredEffects()) {
            leftContent = Math.max(leftContent, font.width("  " + effect.getDisplayName()));
        }
        LEFT_W = leftContent + padding;

        int midContent = 80;
        for (IngredientEntry entry : allIngredients) {
            midContent = Math.max(midContent,
                    font.width("    " + displayName(entry) + " (99)"));
        }
        MID_W = midContent + padding;

        TOTAL_H   = (int)(height * 0.80f);
        CONTENT_H = TOTAL_H - HEADER_H - BOTTOM_BAR_H;

        guiLeft = 20;
        guiTop  = (height - TOTAL_H) / 2;

        MID_X   = guiLeft + LEFT_W + COL_GAP;
        RIGHT_X = MID_X + MID_W + COL_GAP;
        RIGHT_W = width - RIGHT_X - 20;
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    private void scanInventory() {
        allIngredients.clear();
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        Map<Item, IngredientEntry> seen = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof AlchemyIngredient ai)) continue;
            seen.merge(stack.getItem(),
                    new IngredientEntry(ai, stack.copy(), stack.getCount()),
                    (e, inc) -> { e.count += inc.count; return e; });
        }
        allIngredients.addAll(seen.values());
        allIngredients.sort(Comparator.comparing(e -> e.ai.getIngredientId().getPath()));
        applyFilter();
    }

    private void applyFilter() {
        filteredIngredients.clear();
        if (effectFilter == null) {
            filteredIngredients.addAll(allIngredients);
        } else {
            for (IngredientEntry entry : allIngredients) {
                for (AlchemyEffectInstance inst : entry.ai.getAlchemyEffects()) {
                    if (inst.effect() == effectFilter
                            && ClientAlchemyKnowledgeManager.isRevealed(
                            entry.ai.getIngredientId(), inst.slot())) {
                        filteredIngredients.add(entry);
                        break;
                    }
                }
            }
        }
        ingredientScroll = 0;
        hoveredIngredient = null;
    }

    private void recomputeBrew() {
        if (selected.size() < 2) { currentBrewResult = null; return; }
        currentBrewResult = BrewingLogic.simulate(
                selected.stream().map(e -> e.stack).collect(Collectors.toList()));
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, COLOR_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        drawLeftColumn(g, mx, my);
        if (middleVisible) drawMiddleColumn(g, mx, my);
        if (rightVisible) drawRightArea(g, mx, my);
        drawBottomBar(g, mx, my);
        drawXpBar(g);
        if (showPopup) drawPopup(g, mx, my);
    }

    // ── Left column ───────────────────────────────────────────────────────────

    private void drawLeftColumn(GuiGraphicsExtractor g, int mx, int my) {
        int x = guiLeft;
        int y = guiTop;
        int colH = TOTAL_H - BOTTOM_BAR_H;

        // Panel background + border
        g.fill(x, y, x + LEFT_W, y + colH, 0x55001020);
        drawBorder(g, x, y, LEFT_W, colH);

        // INGREDIENTS header
        boolean headerSel = effectFilter == null && middleVisible;
        boolean headerHov = inBounds(mx, my, x, y, LEFT_W, HEADER_H);
        g.fill(x + 1, y + 1, x + LEFT_W - 1, y + HEADER_H,
                headerSel ? COLOR_ROW_SEL : headerHov ? COLOR_ROW_HOV : COLOR_HEADER_BG);
        g.fill(x, y + HEADER_H, x + LEFT_W, y + HEADER_H + 1, COLOR_SEPARATOR);

        String headerLabel = "INGREDIENTS";
        g.text(font, Component.literal(headerLabel),
                x + LEFT_W / 2 - font.width(headerLabel) / 2,
                y + (HEADER_H - 8) / 2,
                headerSel ? COLOR_VALUE : COLOR_LABEL, false);

        // Effect rows
        int rowY = y + HEADER_H + 1;
        List<AlchemyEffect> effects = getDiscoveredEffects();
        int visRows = (colH - HEADER_H - 1) / ROW_H;

        for (int i = effectScroll;
             i < Math.min(effectScroll + visRows, effects.size()); i++) {
            AlchemyEffect effect = effects.get(i);
            boolean hov = inBounds(mx, my, x + 1, rowY, LEFT_W - 2, ROW_H);
            boolean sel = effect == effectFilter;

            int rowBg = sel ? COLOR_ROW_SEL : hov ? COLOR_ROW_HOV
                    : (i % 2 == 0 ? COLOR_ROW_EVEN : 0);
            if (rowBg != 0)
                g.fill(x + 1, rowY, x + LEFT_W - 1, rowY + ROW_H, rowBg);

            int col = switch (effect.getType()) {
                case BENEFICIAL -> sel ? COLOR_GREEN  : 0xFF336644;
                case HARMFUL    -> sel ? COLOR_RED    : 0xFF663333;
                case NEUTRAL    -> sel ? COLOR_VALUE  : COLOR_LABEL;
            };
            g.text(font, Component.literal(effect.getDisplayName()),
                    x + 8, rowY + (ROW_H - 8) / 2, col, false);
            rowY += ROW_H;
        }

        // Scrollbar
        drawScrollbar(g, x + LEFT_W - 3, y + HEADER_H + 1,
                colH - HEADER_H - 1, effects.size(), visRows, effectScroll);
    }

    // ── Middle column ─────────────────────────────────────────────────────────

    private void drawMiddleColumn(GuiGraphicsExtractor g, int mx, int my) {
        int x = MID_X;
        int y = guiTop;
        int colH = TOTAL_H - BOTTOM_BAR_H;

        g.fill(x, y, x + MID_W, y + colH, 0x55001020);
        drawBorder(g, x, y, MID_W, colH);

        // Header
        String label = effectFilter != null
                ? effectFilter.getDisplayName() : "All Ingredients";
        g.fill(x + 1, y + 1, x + MID_W - 1, y + HEADER_H, COLOR_HEADER_BG);
        g.fill(x, y + HEADER_H, x + MID_W, y + HEADER_H + 1, COLOR_SEPARATOR);
        g.text(font, Component.literal(label),
                x + 8, y + (HEADER_H - 8) / 2, COLOR_VALUE, false);

        // Ingredient rows
        int rowY = y + HEADER_H + 1;
        int visRows = (colH - HEADER_H - 1) / ROW_H;

        for (int i = ingredientScroll;
             i < Math.min(ingredientScroll + visRows, filteredIngredients.size()); i++) {
            IngredientEntry entry  = filteredIngredients.get(i);
            boolean hov    = entry == hoveredIngredient;
            boolean chosen = selected.contains(entry);

            int rowBg = chosen ? COLOR_CHOSEN
                    : hov    ? COLOR_ROW_HOV
                    : i % 2 == 0 ? COLOR_ROW_EVEN : 0;
            if (rowBg != 0)
                g.fill(x + 1, rowY, x + MID_W - 1, rowY + ROW_H, rowBg);

            // Chosen indicator
            if (chosen) {
                g.text(font, Component.literal("◆"),
                        x + 4, rowY + (ROW_H - 8) / 2, COLOR_VALUE, false);
            }

            String name = displayName(entry) + " (" + entry.count + ")";
            int col = chosen ? COLOR_VALUE : hov ? 0xFFFFFFFF : COLOR_LABEL;
            g.text(font, Component.literal(name),
                    x + (chosen ? 16 : 8), rowY + (ROW_H - 8) / 2, col, false);
            rowY += ROW_H;
        }

        drawScrollbar(g, x + MID_W - 3, y + HEADER_H + 1,
                colH - HEADER_H - 1,
                filteredIngredients.size(), visRows, ingredientScroll);
    }

    // ── Right area ────────────────────────────────────────────────────────────

    private void drawRightArea(GuiGraphicsExtractor g, int mx, int my) {
        int x = RIGHT_X;
        int y = guiTop;
        int colH = TOTAL_H - BOTTOM_BAR_H;

        // Panel
        g.fill(x, y, x + RIGHT_W, y + colH, 0x55001020);
        drawBorder(g, x, y, RIGHT_W, colH);

        // Header
        String header = selected.size() >= 2 ? "Potion Preview" : "Ingredient Details";
        g.fill(x + 1, y + 1, x + RIGHT_W - 1, y + HEADER_H, COLOR_HEADER_BG);
        g.fill(x, y + HEADER_H, x + RIGHT_W, y + HEADER_H + 1, COLOR_SEPARATOR);
        g.text(font, Component.literal(header),
                x + RIGHT_W / 2 - font.width(header) / 2,
                y + (HEADER_H - 8) / 2, COLOR_LABEL, false);

        int cy = y + HEADER_H + 12;

        if (selected.size() >= 2) {
            drawPotionInfo(g, x + 8, cy, x + RIGHT_W / 2);
        } else if (hoveredIngredient != null) {
            drawIngredientInfo(g, x + 8, cy, hoveredIngredient, x + RIGHT_W / 2);
        } else {
            String hint = "Select 2–3 ingredients to brew";
            g.text(font, Component.literal(hint),
                    x + RIGHT_W / 2 - font.width(hint) / 2, cy,
                    COLOR_LABEL, false);
        }
    }

    private void drawIngredientInfo(GuiGraphicsExtractor g, int x, int y,
                                    IngredientEntry entry, int centerX) {
        // Item sprite 2x
        float scale = 2.0f;
        int spriteSize = (int)(16 * scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.item(entry.stack, (int)((centerX - spriteSize / 2f) / scale), (int)(y / scale));
        g.pose().popMatrix();
        y += spriteSize + 8;

        // Name
        String name = displayName(entry).toUpperCase();
        g.text(font, Component.literal(name),
                centerX - font.width(name) / 2, y, COLOR_VALUE, true);
        y += 12;

        // Count
        String cnt = "In inventory: " + entry.count;
        g.text(font, Component.literal(cnt),
                centerX - font.width(cnt) / 2, y, COLOR_LABEL, false);
        y += 10;

        // Separator
        g.fill(x, y, x + RIGHT_W - 16, y + 1, COLOR_SEPARATOR);
        y += 8;

        // Effects — 4 slots
        g.text(font, Component.literal("Effects"),
                x, y, COLOR_LABEL, false);
        y += 11;

        List<AlchemyEffectInstance> effects = entry.ai.getAlchemyEffects();
        for (int slot = 0; slot < 4; slot++) {
            if (slot >= effects.size()) break;
            AlchemyEffectInstance inst = effects.get(slot);
            if (ClientAlchemyKnowledgeManager.isRevealed(
                    entry.ai.getIngredientId(), slot)) {
                AlchemyEffect effect = inst.effect();
                int col = switch (effect.getType()) {
                    case BENEFICIAL -> COLOR_GREEN;
                    case HARMFUL    -> COLOR_RED;
                    case NEUTRAL    -> COLOR_VALUE;
                };
                g.text(font, Component.literal("• " + effect.getDisplayName()),
                        x + 4, y, col, false);
            } else {
                g.text(font, Component.literal("• ???"),
                        x + 4, y, COLOR_UNKNOWN, false);
            }
            y += 10;
        }
    }

    private void drawPotionInfo(GuiGraphicsExtractor g, int x, int y, int centerX) {
        boolean isUnknown = currentBrewResult == null
                || !currentBrewResult.isSuccess()
                || ((BrewingLogic.BrewResult.Success) currentBrewResult).effects().isEmpty();

        // Sprite
        float scale = 2.0f;
        int spriteSize = (int)(16 * scale);
        ItemStack previewStack = buildPreviewStack(isUnknown);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.item(previewStack, (int)((centerX - spriteSize / 2f) / scale), (int)(y / scale));
        g.pose().popMatrix();
        y += spriteSize + 8;

        if (isUnknown) {
            String name = "POTION OF UNKNOWN EFFECT";
            g.text(font, Component.literal(name),
                    centerX - font.width(name) / 2, y, COLOR_LABEL, false);
            y += 12;
            g.fill(x, y, x + RIGHT_W - 16, y + 1, COLOR_SEPARATOR);
            y += 8;
            g.text(font, Component.literal("Unknown Effect"),
                    x + 4, y, COLOR_UNKNOWN, false);
            return;
        }

        BrewingLogic.BrewResult.Success success =
                (BrewingLogic.BrewResult.Success) currentBrewResult;
        String name = buildPotionName(success.effects()).toUpperCase();
        g.text(font, Component.literal(name),
                centerX - font.width(name) / 2, y, COLOR_VALUE, true);
        y += 12;

        g.fill(x, y, x + RIGHT_W - 16, y + 1, COLOR_SEPARATOR);
        y += 8;

        // Effects list
        g.text(font, Component.literal("Effects"), x, y, COLOR_LABEL, false);
        y += 11;

        for (AlchemyEffectInstance inst : success.effects()) {
            AlchemyEffect effect = inst.effect();
            int col = switch (effect.getType()) {
                case BENEFICIAL -> COLOR_GREEN;
                case HARMFUL    -> COLOR_RED;
                case NEUTRAL    -> COLOR_VALUE;
            };
            g.text(font, Component.literal("• " + effect.getDisplayName()),
                    x + 4, y, col, false);
            y += 10;
            String desc = effect.buildDescription(
                    effect.getBaseMagnitude(), effect.getBaseDurationTicks());
            g.text(font, Component.literal("  " + desc),
                    x + 4, y, COLOR_LABEL, false);
            y += 11;
        }
    }

    // ── XP bar (bottom right, like Skyrim) ────────────────────────────────────

    private void drawXpBar(GuiGraphicsExtractor g) {
        int barX = width - XP_BAR_W - 12;
        int barY = height - 18;

        int level = ClientSkillsManager.getLevel(Skill.ALCHEMY);
        int xp    = ClientSkillsManager.getXp(Skill.ALCHEMY);
        int req   = ClientSkillsManager.getXpRequired(Skill.ALCHEMY);

        // Label: "Alchemy 16"
        String label = "Alchemy " + level;
        g.text(font, Component.literal(label),
                barX, barY - 10, COLOR_LABEL, false);

        // XP bar background
        g.fill(barX, barY, barX + XP_BAR_W, barY + XP_BAR_H, COLOR_XP_BG);

        // XP bar fill
        if (req > 0 && xp > 0) {
            int fill = Math.min((int)((float) xp / req * XP_BAR_W), XP_BAR_W);
            g.fill(barX, barY, barX + fill, barY + XP_BAR_H, COLOR_XP_FILL);
        }

        // Border
        g.fill(barX,             barY,             barX + XP_BAR_W, barY + 1,         COLOR_BORDER);
        g.fill(barX,             barY + XP_BAR_H,  barX + XP_BAR_W, barY + XP_BAR_H + 1, COLOR_BORDER);
        g.fill(barX,             barY,             barX + 1,         barY + XP_BAR_H, COLOR_BORDER);
        g.fill(barX + XP_BAR_W, barY,             barX + XP_BAR_W + 1, barY + XP_BAR_H, COLOR_BORDER);

        // XP text centered on bar
        String xpStr = xp + " / " + req;
        g.text(font, Component.literal(xpStr),
                barX + XP_BAR_W / 2 - font.width(xpStr) / 2,
                barY + XP_BAR_H + 2, COLOR_LABEL, false);
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private void drawBottomBar(GuiGraphicsExtractor g, int mx, int my) {
        int barY = guiTop + TOTAL_H - BOTTOM_BAR_H;

        g.fill(0, barY, width, barY + BOTTOM_BAR_H, COLOR_BOTTOM);
        g.fill(0, barY, width, barY + 1, COLOR_BORDER);

        int btnY      = barY + 5;
        int btnH      = BOTTOM_BAR_H - 10;
        int rightEdge = width - 8;

        // Craft button
        boolean canCraft = selected.size() >= 2;
        if (canCraft) {
            int bw = 56;
            boolean hov = inBounds(mx, my, rightEdge - bw, btnY, bw, btnH);
            drawButton(g, rightEdge - bw, btnY, bw, btnH, "[R] Craft",
                    COLOR_BTN_CRAFT, COLOR_BTN_CRAFT_H, COLOR_GREEN, hov);
            rightEdge -= bw + 6;
        }

        // Clear button
        if (!selected.isEmpty()) {
            int bw = 56;
            boolean hov = inBounds(mx, my, rightEdge - bw, btnY, bw, btnH);
            drawButton(g, rightEdge - bw, btnY, bw, btnH, "[F] Clear",
                    COLOR_BTN, COLOR_BTN_HOVER, COLOR_VALUE, hov);
            rightEdge -= bw + 6;
        }

        // Exit button
        {
            int bw = 60;
            boolean hov = inBounds(mx, my, rightEdge - bw, btnY, bw, btnH);
            drawButton(g, rightEdge - bw, btnY, bw, btnH, "[Esc] Exit",
                    COLOR_BTN, COLOR_BTN_HOVER, COLOR_LABEL, hov);
        }

        // Left side: selected ingredients
        if (!selected.isEmpty()) {
            String req = "Selected: " + selected.stream()
                    .map(this::displayName)
                    .collect(Collectors.joining(", "));
            if (selected.size() < 2) req += " (need 1 more)";
            g.text(font, Component.literal(req),
                    8, btnY + (btnH - 8) / 2, COLOR_LABEL, false);
        } else {
            g.text(font, Component.literal("Select 2–3 ingredients to brew"),
                    8, btnY + (btnH - 8) / 2, COLOR_LABEL, false);
        }
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h,
                            String label, int bgNorm, int bgHov, int textCol,
                            boolean hovered) {
        int bg = hovered ? bgHov : bgNorm;
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x,       y,       x + w, y + 1,     COLOR_BORDER);
        g.fill(x,       y + h - 1, x + w, y + h,   COLOR_BORDER);
        g.fill(x,       y,       x + 1, y + h,     COLOR_BORDER);
        g.fill(x + w - 1, y,     x + w, y + h,     COLOR_BORDER);
        g.text(font, Component.literal(label),
                x + w / 2 - font.width(label) / 2,
                y + (h - 8) / 2, textCol, false);
    }

    // ── Popup ─────────────────────────────────────────────────────────────────

    private int popupHeight() {
        boolean isFailed = popupPotionName.equals("Potion creation failed");
        int effectLines = (!isFailed && !popupEffects.isEmpty())
                ? 1 + popupEffects.size() : 0;
        return 30 + 16 + effectLines * 14 + 24;
    }

    private void drawPopup(GuiGraphicsExtractor g, int mx, int my) {
        int popupW = 240;
        int popupH = popupHeight();
        int px = width  / 2 - popupW / 2;
        int py = height / 2 - popupH / 2;

        g.fill(px - 2, py - 2, px + popupW + 2, py + popupH + 2,
                COLOR_BORDER_GLOW);
        g.fill(px, py, px + popupW, py + popupH, COLOR_POPUP_BG);
        drawBorder(g, px, py, popupW, popupH);

        int cy = py + 12;
        boolean isFailed = popupPotionName.equals("Potion creation failed");
        String title = isFailed ? popupPotionName : "Created " + popupPotionName;
        g.text(font, Component.literal(title),
                px + popupW / 2 - font.width(title) / 2, cy,
                isFailed ? COLOR_RED : COLOR_VALUE, true);
        cy += 18;

        g.fill(px + 16, cy, px + popupW - 16, cy + 1, COLOR_SEPARATOR);
        cy += 8;

        if (!isFailed && !popupEffects.isEmpty()) {
            String sub = "Newly discovered:";
            g.text(font, Component.literal(sub),
                    px + popupW / 2 - font.width(sub) / 2, cy, COLOR_LABEL, false);
            cy += 14;
            for (var de : popupEffects) {
                String line = de.effectName() + " (" + de.ingredientName() + ")";
                g.text(font, Component.literal(line),
                        px + popupW / 2 - font.width(line) / 2, cy, COLOR_GREEN, true);
                cy += 14;
            }
        }

        // Ok button
        int btnW = 60;
        int okY  = py + popupH - 20;
        int btnX = px + popupW / 2 - btnW / 2;
        boolean okHov = inBounds(mx, my, btnX, okY, btnW, 14);
        drawButton(g, btnX, okY, btnW, 14, "Ok",
                COLOR_BTN, COLOR_BTN_HOVER, COLOR_VALUE, okHov);
    }

    // ── Brew ──────────────────────────────────────────────────────────────────

    private void sendBrew() {
        // Remember ingredient IDs for re-selection after brew
        lastBrewedIngredientIds = selected.stream()
                .map(e -> BuiltInRegistries.ITEM.getKey(e.stack.getItem()))
                .collect(Collectors.toList());

        List<Identifier> ids = new ArrayList<>(lastBrewedIngredientIds);
        ClientPlayNetworking.send(new BrewPayload(ids));

        // Decrement counts client-side immediately
        for (IngredientEntry entry : selected) {
            entry.count--;
            entry.stack.shrink(1);
        }
        selected.clear();
        currentBrewResult = null;

        // Rescan and try to re-select the same ingredients (continuous crafting)
        scanInventory();
        computeLayout();
        reSelectLastIngredients();
        playClick();
    }

    /**
     * After brewing, tries to re-select the same ingredient types automatically
     * if they are still available in the player's inventory.
     * This enables continuous crafting without manual re-selection.
     */
    private void reSelectLastIngredients() {
        if (lastBrewedIngredientIds.isEmpty()) return;
        selected.clear();
        for (Identifier id : lastBrewedIngredientIds) {
            for (IngredientEntry entry : allIngredients) {
                if (BuiltInRegistries.ITEM.getKey(entry.stack.getItem()).equals(id)
                        && entry.count > 0
                        && !selected.contains(entry)) {
                    selected.add(entry);
                    break;
                }
            }
        }
        recomputeBrew();
        rightVisible = !selected.isEmpty();

        // If we couldn't re-select enough ingredients, reset the middle column too
        // so the filter header doesn't linger showing a now-irrelevant effect
        if (selected.isEmpty() && filteredIngredients.isEmpty()) {
            middleVisible = false;
            effectFilter  = null;
        }
    }

    // ── Popup callback ────────────────────────────────────────────────────────

    public void onBrewResult(String potionName,
                             List<zcylas.totality.networking.alchemy.BrewResultPayload.DiscoveredEffect>
                                     discovered) {
        if (!discovered.isEmpty()
                && discovered.get(0).effectName().equals("FAILED")) {
            this.popupPotionName = "Potion creation failed";
            this.popupEffects    = List.of();
            this.showPopup       = true;
            return;
        }
        if (discovered.isEmpty()) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal(potionName + " — Added to inventory")
                            .withStyle(ChatFormatting.AQUA));
        } else {
            this.popupPotionName = potionName;
            this.popupEffects    = discovered;
            this.showPopup       = true;
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x();
        int my = (int) mouse.y();

        if (showPopup) {
            int popupW = 240, popupH = popupHeight();
            int px = width / 2 - popupW / 2, py = height / 2 - popupH / 2;
            int btnW = 60, btnX = px + popupW / 2 - btnW / 2, btnY = py + popupH - 20;
            if (inBounds(mx, my, btnX, btnY, btnW, 14)) {
                showPopup = false; playClick(); return true;
            }
            return true;
        }

        int barY = guiTop + TOTAL_H - BOTTOM_BAR_H;
        int btnY = barY + 5, btnH = BOTTOM_BAR_H - 10, rightEdge = width - 8;

        boolean canCraft = selected.size() >= 2;
        if (canCraft) {
            int bw = 56;
            if (inBounds(mx, my, rightEdge - bw, btnY, bw, btnH)) {
                sendBrew(); return true;
            }
            rightEdge -= bw + 6;
        }
        if (!selected.isEmpty()) {
            int bw = 56;
            if (inBounds(mx, my, rightEdge - bw, btnY, bw, btnH)) {
                selected.clear(); currentBrewResult = null;
                lastBrewedIngredientIds.clear();
                rightVisible = hoveredIngredient != null;
                playClick(); return true;
            }
            rightEdge -= bw + 6;
        }
        {
            int bw = 60;
            if (inBounds(mx, my, rightEdge - bw, btnY, bw, btnH)) {
                onClose(); return true;
            }
        }

        // Left column — INGREDIENTS header
        if (inBounds(mx, my, guiLeft, guiTop, LEFT_W, HEADER_H)) {
            effectFilter = null; middleVisible = true;
            applyFilter(); playClick(); return true;
        }

        // Left column — effect rows
        int colH = TOTAL_H - BOTTOM_BAR_H;
        int visRows = (colH - HEADER_H - 1) / ROW_H;
        List<AlchemyEffect> effects = getDiscoveredEffects();
        for (int i = effectScroll;
             i < Math.min(effectScroll + visRows, effects.size()); i++) {
            int rowY = guiTop + HEADER_H + 1 + (i - effectScroll) * ROW_H;
            if (inBounds(mx, my, guiLeft, rowY, LEFT_W, ROW_H)) {
                effectFilter = effects.get(i); middleVisible = true;
                applyFilter(); playClick(); return true;
            }
        }

        // Middle column — ingredient rows
        if (middleVisible) {
            int ingVisRows = (colH - HEADER_H - 1) / ROW_H;
            for (int i = ingredientScroll;
                 i < Math.min(ingredientScroll + ingVisRows, filteredIngredients.size()); i++) {
                int rowY = guiTop + HEADER_H + 1 + (i - ingredientScroll) * ROW_H;
                if (inBounds(mx, my, MID_X, rowY, MID_W, ROW_H)) {
                    IngredientEntry entry = filteredIngredients.get(i);
                    if (selected.contains(entry)) selected.remove(entry);
                    else if (selected.size() < 3) selected.add(entry);
                    rightVisible = !selected.isEmpty() || hoveredIngredient != null;
                    recomputeBrew(); playClick(); return true;
                }
            }
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_R && selected.size() >= 2) {
            sendBrew(); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_F && !selected.isEmpty()) {
            selected.clear(); currentBrewResult = null;
            lastBrewedIngredientIds.clear();
            rightVisible = hoveredIngredient != null;
            playClick(); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose(); return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int delta = sy > 0 ? -1 : 1;
        if (mx >= guiLeft && mx < guiLeft + LEFT_W) {
            int colH = TOTAL_H - BOTTOM_BAR_H;
            int visRows = (colH - HEADER_H - 1) / ROW_H;
            int max = Math.max(0, getDiscoveredEffects().size() - visRows);
            effectScroll = Math.max(0, Math.min(effectScroll + delta, max));
            return true;
        }
        if (middleVisible && mx >= MID_X && mx < MID_X + MID_W) {
            int colH = TOTAL_H - BOTTOM_BAR_H;
            int visRows = (colH - HEADER_H - 1) / ROW_H;
            int max = Math.max(0, filteredIngredients.size() - visRows);
            ingredientScroll = Math.max(0, Math.min(ingredientScroll + delta, max));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (!middleVisible) { hoveredIngredient = null; return; }
        hoveredIngredient = null;
        if (selected.isEmpty()) rightVisible = false;
        int colH = TOTAL_H - BOTTOM_BAR_H;
        int visRows = (colH - HEADER_H - 1) / ROW_H;
        for (int i = ingredientScroll;
             i < Math.min(ingredientScroll + visRows, filteredIngredients.size()); i++) {
            int rowY = guiTop + HEADER_H + 1 + (i - ingredientScroll) * ROW_H;
            if (mx >= MID_X && mx < MID_X + MID_W
                    && my >= rowY && my < rowY + ROW_H) {
                hoveredIngredient = filteredIngredients.get(i);
                rightVisible = true;
                return;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        g.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int y,
                               int trackH, int totalItems, int visItems, int scroll) {
        if (totalItems <= visItems) return;
        int thumbH = Math.max(10, trackH * visItems / totalItems);
        int maxScroll = totalItems - visItems;
        int thumbY = y + (int)((float) scroll / maxScroll * (trackH - thumbH));
        g.fill(x, y, x + 2, y + trackH, COLOR_XP_BG);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, COLOR_BORDER);
    }

    private List<AlchemyEffect> getDiscoveredEffects() {
        Set<AlchemyEffect> found = new LinkedHashSet<>();
        for (IngredientEntry entry : allIngredients) {
            for (AlchemyEffectInstance inst : entry.ai.getAlchemyEffects()) {
                if (ClientAlchemyKnowledgeManager.isRevealed(
                        entry.ai.getIngredientId(), inst.slot()))
                    found.add(inst.effect());
            }
        }
        return new ArrayList<>(found);
    }

    private String displayName(IngredientEntry entry) {
        String path = entry.ai.getIngredientId().getPath();
        return Arrays.stream(path.split("_"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String buildPotionName(List<AlchemyEffectInstance> effects) {
        if (effects.isEmpty()) return "Potion of Unknown Effect";
        boolean anyHarmful     = effects.stream().anyMatch(e -> e.effect().isHarmful());
        boolean anyBeneficial  = effects.stream().anyMatch(e -> e.effect().isBeneficial());
        String prefix = (anyHarmful && !anyBeneficial) ? "Poison of " : "Potion of ";
        AlchemyEffectInstance primary = effects.stream()
                .max(Comparator.comparingDouble(e ->
                        (double) e.effect().getBaseMagnitude()
                                * Math.max(1, e.effect().getBaseDurationTicks())))
                .orElse(effects.get(0));
        return prefix + primary.effect().getDisplayName();
    }

    private ItemStack buildPreviewStack(boolean unknown) {
        ItemStack stack = new ItemStack(
                zcylas.totality.init.items.PotionItems.BREWED_POTION);
        int color;
        String name;
        List<zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry> entries;
        boolean isPoison = false;

        if (unknown || currentBrewResult == null || !currentBrewResult.isSuccess()) {
            color   = zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.COLOR_PURPLE;
            name    = "Potion of Unknown Effect";
            entries = List.of();
        } else {
            var success = (BrewingLogic.BrewResult.Success) currentBrewResult;
            isPoison = success.effects().stream().anyMatch(e -> e.effect().isHarmful())
                    && success.effects().stream().noneMatch(e -> e.effect().isBeneficial());
            color   = getPotionPreviewColor(success.effects());
            name    = buildPotionName(success.effects());
            entries = success.effects().stream()
                    .map(e -> zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry.of(
                            e.effect(), e.effect().getBaseMagnitude(),
                            e.effect().getBaseDurationTicks()))
                    .collect(Collectors.toList());
        }
        stack.set(
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionDataComponent.POTION_DATA,
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.of(
                        name, color, entries, isPoison));
        return stack;
    }

    private int getPotionPreviewColor(List<AlchemyEffectInstance> effects) {
        if (effects.isEmpty())
            return zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.COLOR_PURPLE;
        AlchemyEffectInstance primary = effects.stream()
                .max(Comparator.comparingDouble(e ->
                        (double) e.effect().getBaseMagnitude()
                                * Math.max(1, e.effect().getBaseDurationTicks())))
                .orElse(effects.get(0));
        String id = primary.effect().getId().getPath();
        if (id.contains("health"))  return zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.COLOR_RED;
        if (id.contains("mana"))    return zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.COLOR_BLUE;
        if (id.contains("stamina")) return zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.COLOR_GREEN;
        if (id.contains("water") || id.contains("invisib"))
            return zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.COLOR_WHITE;
        return zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.COLOR_GOLD;
    }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean isInGameUi()    { return false;  }
    @Override public boolean isPauseScreen() { return false; }

    private static class IngredientEntry {
        final AlchemyIngredient ai;
        final ItemStack stack;
        int count;
        IngredientEntry(AlchemyIngredient ai, ItemStack stack, int count) {
            this.ai = ai; this.stack = stack; this.count = count;
        }
    }
}