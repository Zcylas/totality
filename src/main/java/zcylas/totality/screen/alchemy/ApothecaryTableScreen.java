package zcylas.totality.screen.alchemy;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.alchemy.*;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.networking.alchemy.BrewPayload;
import zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Apothecary Table screen — Skyrim-faithful layout.
 *
 * Layout:
 *   - Transparent background (world visible behind)
 *   - Left column: effect filter list with semi-transparent dark backing
 *   - Middle column: ingredient list with semi-transparent dark backing
 *   - Right side: transparent — shows ingredient/potion info as floating text
 *   - Bottom bar: semi-transparent dark strip for buttons
 */
public class ApothecaryTableScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private int LEFT_W;
    private int MID_W;
    private int MID_X;
    private int RIGHT_W;
    private int RIGHT_X;
    private int TOTAL_W;
    private int TOTAL_H;
    private int CONTENT_H;

    private int guiLeft;   // left edge of left column
    private int guiTop;    // top edge of columns

    private static final int HEADER_H     = 26;
    private static final int ROW_H        = 18;
    private static final int BOTTOM_BAR_H = 28;
    private static final int COL_GAP      = 2;  // gap between columns

    // Semi-transparent dark panel (like Skyrim's column backing)
    private static final int BG_PANEL     = 0xCC000000;
    private static final int BG_HEADER    = 0xDD111111;
    private static final int BG_HOVER     = 0x55FFFFFF;
    private static final int BG_SELECTED  = 0x66FFFFFF;
    private static final int BG_CHOSEN    = 0x55D4AF37;
    private static final int BG_BOTTOM    = 0xCC000000;

    // Text colors — Skyrim-style
    private static final int COLOR_TITLE    = 0xFFFFFFFF;
    private static final int COLOR_NORMAL   = 0xFFCCCCCC;
    private static final int COLOR_DIM      = 0xFF888888;
    private static final int COLOR_GOLD     = 0xFFD4AF37;
    private static final int COLOR_UNKNOWN  = 0xFF777777;
    private static final int COLOR_GREEN    = 0xFF66FF66;
    private static final int COLOR_RED      = 0xFFFF6666;
    private static final int COLOR_WHITE    = 0xFFFFFFFF;
    private static final int COLOR_SEP      = 0x88FFFFFF;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<IngredientEntry> allIngredients      = new ArrayList<>();
    private final List<IngredientEntry> filteredIngredients = new ArrayList<>();
    private final List<IngredientEntry> selected            = new ArrayList<>();

    private AlchemyEffect effectFilter        = null;
    private boolean middleVisible             = false;
    private IngredientEntry hoveredIngredient = null;

    private int effectScroll     = 0;
    private int ingredientScroll = 0;

    private BrewingLogic.BrewResult currentBrewResult = null;
    private int guiRight; // right edge of middle column (start of transparent right area)

    // ── Popup state ───────────────────────────────────────────────────────────
    private boolean showPopup = false;
    private String popupPotionName = "";
    private List<zcylas.totality.networking.alchemy.BrewResultPayload.DiscoveredEffect> popupEffects = new ArrayList<>();

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

        // Left column — effect names + INGREDIENTS header
        int leftContent = font.width("INGREDIENTS");
        for (AlchemyEffect effect : getDiscoveredEffects()) {
            leftContent = Math.max(leftContent, font.width("  " + effect.getDisplayName()));
        }
        LEFT_W = leftContent + padding;

        // Middle column — ingredient names
        int midContent = 80;
        for (IngredientEntry entry : allIngredients) {
            midContent = Math.max(midContent, font.width("    " + displayName(entry) + " (99)"));
        }
        MID_W = midContent + padding;

        // Right area — floating text, transparent, takes remaining screen space
        MID_X    = LEFT_W + COL_GAP;
        RIGHT_X  = MID_X + MID_W + COL_GAP;
        RIGHT_W  = width - RIGHT_X - 20; // leave right margin

        TOTAL_H   = (int)(height * 0.72f);
        CONTENT_H = TOTAL_H - HEADER_H - BOTTOM_BAR_H;
        TOTAL_W   = RIGHT_X + RIGHT_W;

        // Left-align the two columns to the left side of the screen with margin
        guiLeft = 20;
        guiTop  = (height - TOTAL_H) / 2;

        // Recompute absolute positions
        MID_X   = guiLeft + LEFT_W + COL_GAP;
        RIGHT_X = MID_X + MID_W + COL_GAP;
        RIGHT_W = width - RIGHT_X - 20;
        guiRight = RIGHT_X;
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    private void scanInventory() {
        allIngredients.clear();
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        Map<Item, IngredientEntry> seen = new LinkedHashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
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
        // Fully transparent — world renders behind
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        drawLeftColumn(g, mx, my);
        if (middleVisible) drawMiddleColumn(g, mx, my);
        drawRightArea(g, mx, my);
        drawBottomBar(g, mx, my);
        if (showPopup) drawPopup(g, mx, my);
    }

    // ── Left column ───────────────────────────────────────────────────────────

    private void drawLeftColumn(GuiGraphicsExtractor g, int mx, int my) {
        int x = guiLeft;
        int y = guiTop;
        int colH = TOTAL_H - BOTTOM_BAR_H;

        // Dark panel behind entire left column
        g.fill(x, y, x + LEFT_W, y + colH, BG_PANEL);

        // INGREDIENTS header
        boolean headerHov = inBounds(mx, my, x, y, LEFT_W, HEADER_H);
        boolean headerSel = effectFilter == null && middleVisible;
        g.fill(x, y, x + LEFT_W, y + HEADER_H,
                headerSel ? BG_SELECTED : (headerHov ? BG_HOVER : BG_HEADER));

        // Thin separator line below header
        g.fill(x + 6, y + HEADER_H - 1, x + LEFT_W - 6, y + HEADER_H, COLOR_SEP);

        String headerLabel = "INGREDIENTS";
        g.text(font, Component.literal(headerLabel),
                x + LEFT_W / 2 - font.width(headerLabel) / 2, y + (HEADER_H - 8) / 2,
                headerSel ? COLOR_GOLD : COLOR_TITLE, true);

        // Effect rows
        int rowY = y + HEADER_H + 4;
        List<AlchemyEffect> effects = getDiscoveredEffects();
        int visRows = CONTENT_H / ROW_H;

        for (int i = effectScroll; i < Math.min(effectScroll + visRows, effects.size()); i++) {
            AlchemyEffect effect = effects.get(i);
            boolean hov = inBounds(mx, my, x, rowY, LEFT_W, ROW_H);
            boolean sel = effect == effectFilter;
            if (hov || sel)
                g.fill(x, rowY, x + LEFT_W, rowY + ROW_H, sel ? BG_SELECTED : BG_HOVER);

            int col = switch (effect.getType()) {
                case BENEFICIAL -> sel ? COLOR_GREEN  : 0xFF449944;
                case HARMFUL    -> sel ? COLOR_RED    : 0xFF994444;
                case NEUTRAL    -> sel ? COLOR_WHITE  : COLOR_NORMAL;
            };
            g.text(font, Component.literal("  " + effect.getDisplayName()),
                    x + 6, rowY + (ROW_H - 8) / 2, col, false);
            rowY += ROW_H;
        }
    }

    // ── Middle column ─────────────────────────────────────────────────────────

    private void drawMiddleColumn(GuiGraphicsExtractor g, int mx, int my) {
        int x = MID_X;
        int y = guiTop;
        int colH = TOTAL_H - BOTTOM_BAR_H;

        // Dark panel
        g.fill(x, y, x + MID_W, y + colH, BG_PANEL);

        // Header — shows current filter name or "All"
        String label = effectFilter != null ? effectFilter.getDisplayName() : "All Ingredients";
        g.fill(x, y, x + MID_W, y + HEADER_H, BG_HEADER);
        g.fill(x + 6, y + HEADER_H - 1, x + MID_W - 6, y + HEADER_H, COLOR_SEP);
        g.text(font, Component.literal(label),
                x + 8, y + (HEADER_H - 8) / 2, COLOR_TITLE, true);

        // Ingredient rows
        int rowY = y + HEADER_H;
        int visRows = CONTENT_H / ROW_H;

        for (int i = ingredientScroll;
             i < Math.min(ingredientScroll + visRows, filteredIngredients.size()); i++) {
            IngredientEntry entry  = filteredIngredients.get(i);
            boolean hov    = entry == hoveredIngredient;
            boolean chosen = selected.contains(entry);

            if (chosen)   g.fill(x, rowY, x + MID_W, rowY + ROW_H, BG_CHOSEN);
            else if (hov) g.fill(x, rowY, x + MID_W, rowY + ROW_H, BG_HOVER);

            if (chosen) g.text(font, Component.literal("◇").withStyle(ChatFormatting.GOLD),
                    x + 4, rowY + (ROW_H - 8) / 2, COLOR_GOLD, false);

            String name = (chosen ? "    " : "  ") + displayName(entry) + " (" + entry.count + ")";
            int col = chosen ? COLOR_GOLD : (hov ? COLOR_WHITE : COLOR_NORMAL);
            g.text(font, Component.literal(name), x + 4, rowY + (ROW_H - 8) / 2, col, false);
            rowY += ROW_H;
        }
    }

    // ── Right area — transparent floating info ─────────────────────────────────

    private void drawRightArea(GuiGraphicsExtractor g, int mx, int my) {
        int x = RIGHT_X + 16; // indent from middle column
        int y = guiTop + 20;

        if (selected.size() >= 2) {
            drawPotionInfo(g, x, y);
        } else if (hoveredIngredient != null) {
            drawIngredientInfo(g, x, y, hoveredIngredient);
        } else {
            // Skyrim-style hint — just text, no panel
            String title = "Alchemy: Combine ingredients";
            String sub   = "to make potions";
            g.text(font, Component.literal(title).withStyle(ChatFormatting.WHITE),
                    x, y, COLOR_NORMAL, true);
            g.text(font, Component.literal(sub),
                    x, y + 14, COLOR_DIM, false);
        }
    }

    private void drawIngredientInfo(GuiGraphicsExtractor g, int x, int y, IngredientEntry entry) {
        // Item sprite — 3x scale (48px), centered in the right area
        int spriteSize = 48;
        float scale = 3.0f;
        int centerX = RIGHT_X + RIGHT_W / 2;
        int spriteX = centerX - spriteSize / 2;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.item(entry.stack, (int)(spriteX / scale), (int)(y / scale));
        g.pose().popMatrix();
        y += spriteSize + 10;

        // Name — uppercase, centered, white with shadow like Skyrim
        String name = displayName(entry).toUpperCase() + "  (" + entry.count + ")";
        int nameX = centerX - font.width(name) / 2;
        g.text(font, Component.literal(name).withStyle(ChatFormatting.WHITE),
                nameX, y, COLOR_TITLE, true);
        y += 16;

        // Thin separator centered under name
        int sepW = Math.min(RIGHT_W - 16, font.width(name) + 20);
        g.fill(centerX - sepW / 2, y, centerX + sepW / 2, y + 1, 0xAAFFFFFF);
        y += 8;

        // 2x2 effect grid — no panel, just floating text
        List<AlchemyEffectInstance> effects = entry.ai.getAlchemyEffects();
        int halfW = Math.max(80, RIGHT_W / 2 - 8);
        int col1X = x;
        int col2X = x + halfW;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                int slot = row * 2 + col;
                if (slot >= effects.size()) continue;
                int ex = col == 0 ? col1X : col2X;
                int ey = y + row * 22;
                AlchemyEffectInstance inst = effects.get(slot);

                if (ClientAlchemyKnowledgeManager.isRevealed(entry.ai.getIngredientId(), slot)) {
                    AlchemyEffect effect = inst.effect();
                    int ec = switch (effect.getType()) {
                        case BENEFICIAL -> COLOR_GREEN;
                        case HARMFUL    -> COLOR_RED;
                        case NEUTRAL    -> COLOR_WHITE;
                    };
                    g.text(font, Component.literal(effect.getDisplayName()), ex, ey, ec, true);
                } else {
                    g.text(font, Component.literal("UNKNOWN"), ex, ey, COLOR_UNKNOWN, true);
                }
            }
        }
    }

    private void drawPotionInfo(GuiGraphicsExtractor g, int x, int y) {
        int centerX = RIGHT_X + RIGHT_W / 2;
        int spriteY = y;

        // Always show POTION OF UNKNOWN EFFECT as default with 2+ ingredients
        if (currentBrewResult == null || !currentBrewResult.isSuccess()
                || ((BrewingLogic.BrewResult.Success) currentBrewResult).effects().isEmpty()) {

            // Show BREWED_POTION with purple POTION_DATA component
            net.minecraft.world.item.ItemStack unknownPotion =
                    new net.minecraft.world.item.ItemStack(
                            zcylas.totality.init.items.PotionItems.BREWED_POTION);
            unknownPotion.set(zcylas.totality.api.potions.PotionDataComponent.POTION_DATA,
                    zcylas.totality.api.potions.PotionData.of(
                            "Potion of Unknown Effect",
                            zcylas.totality.api.potions.PotionData.COLOR_PURPLE,
                            java.util.List.of(), false));
            g.pose().pushMatrix();
            g.pose().scale(3.0f, 3.0f);
            g.item(unknownPotion, (centerX - 24) / 3, spriteY / 3);
            g.pose().popMatrix();
            y += 58;

            int nameX = centerX - font.width("POTION OF UNKNOWN EFFECT") / 2;
            g.text(font, net.minecraft.network.chat.Component.literal("POTION OF UNKNOWN EFFECT")
                            .withStyle(net.minecraft.ChatFormatting.WHITE),
                    nameX, y, COLOR_TITLE, true);
            y += 16;
            g.fill(centerX - 80, y, centerX + 80, y + 1, 0xAAFFFFFF);
            y += 8;
            g.text(font, net.minecraft.network.chat.Component.literal("Unknown Effect"),
                    centerX - font.width("Unknown Effect") / 2, y, COLOR_UNKNOWN, true);
            return;
        }

        BrewingLogic.BrewResult.Success success = (BrewingLogic.BrewResult.Success) currentBrewResult;

        // Show the actual brewed potion item sprite
        // Build a temporary stack with the correct color for preview
        net.minecraft.world.item.ItemStack previewStack =
                new net.minecraft.world.item.ItemStack(
                        zcylas.totality.init.items.PotionItems.BREWED_POTION);
        boolean previewIsPoison = success.effects().stream()
                .anyMatch(e -> e.effect().isHarmful())
                && success.effects().stream().noneMatch(e -> e.effect().isBeneficial());
        int previewColor = previewIsPoison
                ? zcylas.totality.api.potions.PotionData.COLOR_PURPLE
                : getPotionPreviewColor(success.effects());
        String previewName = buildPotionName(success.effects());
        java.util.List<zcylas.totality.api.potions.EffectEntry> previewEntries =
                success.effects().stream()
                        .map(e -> zcylas.totality.api.potions.EffectEntry.of(
                                e.effect(), e.effect().getBaseMagnitude(),
                                e.effect().getBaseDurationTicks()))
                        .collect(java.util.stream.Collectors.toList());
        previewStack.set(zcylas.totality.api.potions.PotionDataComponent.POTION_DATA,
                zcylas.totality.api.potions.PotionData.of(
                        previewName, previewColor, previewEntries, previewIsPoison));

        g.pose().pushMatrix();
        g.pose().scale(3.0f, 3.0f);
        g.item(previewStack, (centerX - 24) / 3, spriteY / 3);
        g.pose().popMatrix();
        y += 58;

        // Dead code path kept for safety
        if (success.effects().isEmpty()) {
            g.text(font, Component.literal("POTION OF UNKNOWN EFFECT").withStyle(ChatFormatting.WHITE),
                    x, y, COLOR_TITLE, true);
            y += 16;
            g.fill(x, y, x + 160, y + 1, 0xAAFFFFFF);
            y += 8;
            g.text(font, Component.literal("Unknown Effect"), x, y, COLOR_UNKNOWN, true);
            return;
        }

        String potionName = buildPotionName(success.effects()).toUpperCase();
        g.text(font, Component.literal(potionName).withStyle(ChatFormatting.WHITE),
                x, y, COLOR_TITLE, true);
        y += 16;
        g.fill(x, y, x + font.width(potionName), y + 1, 0xAAFFFFFF);
        y += 10;

        // List effects with contributing ingredients
        for (AlchemyEffectInstance inst : success.effects()) {
            AlchemyEffect effect = inst.effect();
            int col = switch (effect.getType()) {
                case BENEFICIAL -> COLOR_GREEN;
                case HARMFUL    -> COLOR_RED;
                case NEUTRAL    -> COLOR_WHITE;
            };
            String contributors = selected.stream()
                    .filter(e -> e.ai.getAlchemyEffects().stream()
                            .anyMatch(i -> i.effect() == effect))
                    .map(this::displayName)
                    .collect(Collectors.joining(", "));

            g.text(font, Component.literal(effect.getDisplayName()).withStyle(s -> s.withColor(col)),
                    x, y, col, true);
            y += 11;

            // Wrap contributor line if too wide
            int maxW = RIGHT_W - 8;
            if (font.width("  " + contributors) <= maxW) {
                g.text(font, Component.literal("  " + contributors), x, y, COLOR_DIM, false);
                y += 14;
            } else {
                // Split at comma boundaries
                String[] parts = contributors.split(", ");
                StringBuilder line = new StringBuilder("  ");
                for (String part : parts) {
                    String candidate = line + (line.length() > 2 ? ", " : "") + part;
                    if (font.width(candidate) > maxW && line.length() > 2) {
                        g.text(font, Component.literal(line.toString()), x, y, COLOR_DIM, false);
                        y += 11;
                        line = new StringBuilder("  " + part);
                    } else {
                        if (line.length() > 2) line.append(", ");
                        line.append(part);
                    }
                }
                if (line.length() > 2) {
                    g.text(font, Component.literal(line.toString()), x, y, COLOR_DIM, false);
                    y += 11;
                }
                y += 3;
            }
        }
    }

    // ── Popup ─────────────────────────────────────────────────────────────────

    private int popupHeight() {
        boolean isFailed = popupPotionName.equals("Potion creation failed");
        int lineH = 14;
        int effectLines = (!isFailed && !popupEffects.isEmpty())
                ? 1 + popupEffects.size() // "Discovered effects:" + each effect
                : 0;
        return 30 + 16 + effectLines * lineH + 24;
    }

    private void drawPopup(GuiGraphicsExtractor g, int mx, int my) {
        // Measure popup size
        int popupW = 220;
        int lineH   = 14;
        int popupH  = popupHeight();

        int px = width  / 2 - popupW / 2;
        int py = height / 2 - popupH / 2;

        // Dark panel
        g.fill(px, py, px + popupW, py + popupH, 0xEE000000);
        // Border
        g.fill(px,              py,              px + popupW,     py + 1,          0xAAFFFFFF);
        g.fill(px,              py + popupH - 1, px + popupW,     py + popupH,     0xAAFFFFFF);
        g.fill(px,              py,              px + 1,          py + popupH,     0xAAFFFFFF);
        g.fill(px + popupW - 1, py,              px + popupW,     py + popupH,     0xAAFFFFFF);

        int cy = py + 10;

        boolean isFailed = popupPotionName.equals("Potion creation failed");

        // Title — "Potion creation failed" or "Created Potion of X"
        String title = isFailed ? popupPotionName : "Created " + popupPotionName;
        g.text(font, net.minecraft.network.chat.Component.literal(title)
                        .withStyle(net.minecraft.ChatFormatting.WHITE),
                px + popupW / 2 - font.width(title) / 2, cy,
                isFailed ? COLOR_RED : COLOR_TITLE, true);
        cy += 18;

        // Separator
        g.fill(px + 16, cy, px + popupW - 16, cy + 1, COLOR_SEP);
        cy += 8;

        // Only show discovered effects if not a failure
        if (!isFailed && !popupEffects.isEmpty()) {
            String sub = "Discovered effects:";
            g.text(font, net.minecraft.network.chat.Component.literal(sub),
                    px + popupW / 2 - font.width(sub) / 2, cy, COLOR_NORMAL, false);
            cy += lineH;

            for (var de : popupEffects) {
                String line = de.effectName() + " (" + de.ingredientName() + ")";
                g.text(font, net.minecraft.network.chat.Component.literal(line)
                                .withStyle(net.minecraft.ChatFormatting.WHITE),
                        px + popupW / 2 - font.width(line) / 2, cy, COLOR_GREEN, true);
                cy += lineH;
            }
        }

        // Ok button — position matches click handler: py + popupH - 18
        int btnW = 50;
        int okY  = py + popupH - 18;
        int btnX = px + popupW / 2 - btnW / 2;
        boolean okHov = inBounds(mx, my, btnX, okY, btnW, 14);
        g.fill(btnX, okY, btnX + btnW, okY + 14, okHov ? 0x88FFFFFF : 0x44FFFFFF);
        g.text(font, net.minecraft.network.chat.Component.literal("Ok"),
                btnX + btnW / 2 - font.width("Ok") / 2, okY + 3,
                okHov ? COLOR_WHITE : COLOR_NORMAL, false);
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private void drawBottomBar(GuiGraphicsExtractor g, int mx, int my) {
        int barY = guiTop + TOTAL_H - BOTTOM_BAR_H;

        // Semi-transparent dark strip full width
        g.fill(0, barY, width, barY + BOTTOM_BAR_H, BG_BOTTOM);
        g.fill(0, barY, width, barY + 1, 0x88FFFFFF);

        int btnY      = barY + 4;
        int btnH      = BOTTOM_BAR_H - 8;
        int rightEdge = width - 8;

        // Craft
        boolean canCraft = selected.size() >= 2;
        if (canCraft) {
            int bw = 52;
            drawBtn(g, mx, my, rightEdge - bw, btnY, bw, btnH, "[R] Craft", true);
            rightEdge -= bw + 8;
        }
        // Clear
        if (!selected.isEmpty()) {
            int bw = 60;
            drawBtn(g, mx, my, rightEdge - bw, btnY, bw, btnH, "[F] Clear", false);
            rightEdge -= bw + 8;
        }
        // Exit
        {
            int bw = 62;
            drawBtn(g, mx, my, rightEdge - bw, btnY, bw, btnH, "[Esc] Exit", false);
        }

        // Left side — show selected ingredient names like Skyrim's "Requires:" line
        if (!selected.isEmpty()) {
            String req = "Requires: " + selected.stream()
                    .map(this::displayName)
                    .collect(Collectors.joining(", "));
            if (selected.size() < 2) req += ", Ingredient";
            if (selected.size() < 3) req += ", Optional";
            g.text(font, Component.literal(req),
                    8, btnY + (btnH - 8) / 2, COLOR_NORMAL, false);
        }
    }

    private void drawBtn(GuiGraphicsExtractor g, int mx, int my,
                         int x, int y, int w, int h, String label, boolean highlight) {
        boolean hov = inBounds(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? 0x88FFFFFF : 0x44FFFFFF);
        int col = highlight
                ? (hov ? COLOR_GREEN  : 0xFF44AA44)
                : (hov ? COLOR_WHITE  : COLOR_NORMAL);
        g.text(font, Component.literal(label),
                x + w / 2 - font.width(label) / 2,
                y + (h - 8) / 2, col, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x();
        int my = (int) mouse.y();

        // Popup Ok button
        if (showPopup) {
            int popupW = 220;
            int popupH = popupHeight();
            int px = width  / 2 - popupW / 2;
            int py = height / 2 - popupH / 2;
            int btnW = 50;
            int btnX = px + popupW / 2 - btnW / 2;
            int btnY2 = py + popupH - 18;
            if (inBounds(mx, my, btnX, btnY2, btnW, 14)) {
                showPopup = false;
                playClick();
                return true;
            }
            return true; // block clicks behind popup
        }

        // Bottom bar buttons
        int barY      = guiTop + TOTAL_H - BOTTOM_BAR_H;
        int btnY      = barY + 4;
        int btnH      = BOTTOM_BAR_H - 8;
        int rightEdge = width - 8;

        boolean canCraft = selected.size() >= 2;
        if (canCraft) {
            int bw = 52;
            if (inBounds(mx, my, rightEdge - bw, btnY, bw, btnH)) { sendBrew(); return true; }
            rightEdge -= bw + 8;
        }
        if (!selected.isEmpty()) {
            int bw = 60;
            if (inBounds(mx, my, rightEdge - bw, btnY, bw, btnH)) {
                selected.clear(); currentBrewResult = null; playClick(); return true;
            }
            rightEdge -= bw + 8;
        }
        {
            int bw = 62;
            if (inBounds(mx, my, rightEdge - bw, btnY, bw, btnH)) { onClose(); return true; }
        }

        // Left column — INGREDIENTS header
        if (inBounds(mx, my, guiLeft, guiTop, LEFT_W, HEADER_H)) {
            effectFilter = null; middleVisible = true; applyFilter(); playClick(); return true;
        }

        // Left column — effect rows
        int effectRowStart = guiTop + HEADER_H + 4;
        int visRows = CONTENT_H / ROW_H;
        List<AlchemyEffect> effects = getDiscoveredEffects();
        for (int i = effectScroll; i < Math.min(effectScroll + visRows, effects.size()); i++) {
            int rowY = effectRowStart + (i - effectScroll) * ROW_H;
            if (inBounds(mx, my, guiLeft, rowY, LEFT_W, ROW_H)) {
                effectFilter = effects.get(i); middleVisible = true;
                applyFilter(); playClick(); return true;
            }
        }

        // Middle column — ingredient rows
        if (middleVisible) {
            int ingVisRows = CONTENT_H / ROW_H;
            for (int i = ingredientScroll;
                 i < Math.min(ingredientScroll + ingVisRows, filteredIngredients.size()); i++) {
                int rowY = guiTop + HEADER_H + (i - ingredientScroll) * ROW_H;
                if (inBounds(mx, my, MID_X, rowY, MID_W, ROW_H)) {
                    IngredientEntry entry = filteredIngredients.get(i);
                    if (selected.contains(entry)) selected.remove(entry);
                    else if (selected.size() < 3) selected.add(entry);
                    recomputeBrew(); playClick(); return true;
                }
            }
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int delta = sy > 0 ? -1 : 1;
        if (mx >= guiLeft && mx < guiLeft + LEFT_W) {
            int max = Math.max(0, getDiscoveredEffects().size() - CONTENT_H / ROW_H);
            effectScroll = Math.max(0, Math.min(effectScroll + delta, max));
            return true;
        }
        if (middleVisible && mx >= MID_X && mx < MID_X + MID_W) {
            int max = Math.max(0, filteredIngredients.size() - CONTENT_H / ROW_H);
            ingredientScroll = Math.max(0, Math.min(ingredientScroll + delta, max));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (!middleVisible) { hoveredIngredient = null; return; }
        hoveredIngredient = null;
        int ingVisRows = CONTENT_H / ROW_H;
        for (int i = ingredientScroll;
             i < Math.min(ingredientScroll + ingVisRows, filteredIngredients.size()); i++) {
            int rowY = guiTop + HEADER_H + (i - ingredientScroll) * ROW_H;
            if (mx >= MID_X && mx < MID_X + MID_W && my >= rowY && my < rowY + ROW_H) {
                hoveredIngredient = filteredIngredients.get(i);
                return;
            }
        }
    }

    // ── Brew ──────────────────────────────────────────────────────────────────

    private void sendBrew() {
        List<Identifier> ids = selected.stream()
                .map(e -> BuiltInRegistries.ITEM.getKey(e.stack.getItem()))
                .collect(Collectors.toList());
        ClientPlayNetworking.send(new BrewPayload(ids));

        // Immediately decrement counts client-side so the GUI updates right away
        // The server will sync the real inventory shortly after
        for (IngredientEntry entry : selected) {
            entry.count--;
            entry.stack.shrink(1);
        }
        selected.clear();
        currentBrewResult = null;
        // Rescan to reflect the decremented counts
        scanInventory();
        computeLayout();
        playClick();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        // Skyrim naming priority: highest (magnitude * duration) effect wins the name
        // Harmful effects → Poison, Beneficial/Neutral → Potion
        boolean anyHarmful = effects.stream().anyMatch(e -> e.effect().isHarmful());
        boolean anyBeneficial = effects.stream().anyMatch(e -> e.effect().isBeneficial());
        String prefix = (anyHarmful && !anyBeneficial) ? "Poison of " : "Potion of ";

        AlchemyEffectInstance primary = effects.stream()
                .max(Comparator.comparingDouble(e ->
                        (double) e.effect().getBaseMagnitude() *
                                Math.max(1, e.effect().getBaseDurationTicks())))
                .orElse(effects.get(0));
        return prefix + primary.effect().getDisplayName();
    }

    private int getPotionPreviewColor(java.util.List<AlchemyEffectInstance> effects) {
        if (effects.isEmpty()) return zcylas.totality.api.potions.PotionData.COLOR_PURPLE;
        AlchemyEffectInstance primary = effects.stream()
                .max(Comparator.comparingDouble(e ->
                        (double) e.effect().getBaseMagnitude() *
                                Math.max(1, e.effect().getBaseDurationTicks())))
                .orElse(effects.get(0));
        String id = primary.effect().getId().getPath();
        if (id.contains("health"))    return zcylas.totality.api.potions.PotionData.COLOR_RED;
        if (id.contains("mana"))      return zcylas.totality.api.potions.PotionData.COLOR_BLUE;
        if (id.contains("stamina"))   return zcylas.totality.api.potions.PotionData.COLOR_GREEN;
        if (id.contains("water") || id.contains("invisib")) return zcylas.totality.api.potions.PotionData.COLOR_WHITE;
        return zcylas.totality.api.potions.PotionData.COLOR_GOLD;
    }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Called by the client packet handler after a successful brew. */
    public void onBrewResult(String potionName,
                             List<zcylas.totality.networking.alchemy.BrewResultPayload.DiscoveredEffect> discovered) {
        // Check for failure sentinel
        if (!discovered.isEmpty() && discovered.get(0).effectName().equals("FAILED")) {
            this.popupPotionName = "Potion creation failed";
            this.popupEffects = java.util.List.of();
            this.showPopup = true;
            return;
        }
        if (discovered.isEmpty()) {
            // Already known — just show toast in chat
            net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(potionName + " Added")
                            .withStyle(net.minecraft.ChatFormatting.WHITE));
        } else {
            // New potion — show popup
            this.popupPotionName = potionName;
            this.popupEffects = discovered;
            this.showPopup = true;
        }
        // Inventory already updated in sendBrew() — nothing to do here
    }

    @Override public boolean isInGameUi()    { return true;  }
    @Override public boolean isPauseScreen() { return false; }

    // ── Inner class ───────────────────────────────────────────────────────────

    private static class IngredientEntry {
        final AlchemyIngredient ai;
        final ItemStack stack;
        int count;
        IngredientEntry(AlchemyIngredient ai, ItemStack stack, int count) {
            this.ai = ai; this.stack = stack; this.count = count;
        }
    }
}