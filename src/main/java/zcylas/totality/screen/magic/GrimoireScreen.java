package zcylas.totality.screen.magic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.magic.GrimoireCaster;
import zcylas.totality.api.magic.MagicComponents;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.api.magic.rune.AbstractFormRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.init.magic.RuneRegistry;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.networking.magic.grimoire.UpdateGrimoirePayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GrimoireScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int GUI_WIDTH   = 260;
    private static final int GUI_HEIGHT  = 230; // taller to fit all sections
    private static final int SLOT_W      = 22;
    private static final int SLOT_H      = 20;
    private static final int MAX_RUNES   = 10;
    private static final int TAB_W       = 14; // narrower tabs
    private static final int TAB_H       = 16; // shorter tabs
    private static final int MAX_SPELLS  = 10;
    private static final int BAR_H       = 24;
    private static final int RUNE_SIZE   = 16;
    private static final int RUNE_STEP   = RUNE_SIZE + 2;

    // How many runes fit in one row
    private static final int RUNES_PER_ROW = 13;

    // ── State ─────────────────────────────────────────────────────────────────
    private final ItemStack   grimoireStack;
    private final GrimoireItem grimoireItem;

    private AbstractRune[]      currentFormula  = new AbstractRune[MAX_RUNES];
    private int                 currentSpellSlot = 0;
    private final List<AbstractRune[]> slotFormulas = new ArrayList<>();
    private final List<String>         slotNames    = new ArrayList<>();

    // Text field state
    private String  typedText        = "";
    private boolean textFieldFocused = false;
    private int     cursorPos        = 0; // character index

    // Search bar state
    private String  searchText       = "";
    private boolean searchFocused    = false;
    private int     searchCursor     = 0;

    // Scroll offsets per rune section (in whole rows)
    private int scrollForms    = 0;
    private int scrollEffects  = 0;
    private int scrollAugments = 0;

    // Filtered rune lists (after search)
    private List<AbstractRune> filteredForms    = new ArrayList<>();
    private List<AbstractRune> filteredEffects  = new ArrayList<>();
    private List<AbstractRune> filteredAugments = new ArrayList<>();

    // Full rune lists
    private List<AbstractRune> allForms    = new ArrayList<>();
    private List<AbstractRune> allEffects  = new ArrayList<>();
    private List<AbstractRune> allAugments = new ArrayList<>();

    // ── Computed layout positions ─────────────────────────────────────────────
    private int guiLeft, guiTop;
    private int slotRowY;
    private int textFieldX, textFieldY, textFieldW;
    private int clearBtnX, clearBtnY, clearBtnW;
    private int createBtnX, createBtnY, createBtnW;

    // Section Y positions (relative to guiTop)
    private static final int FORMS_LABEL_Y    = 8;
    private static final int FORMS_ROW_Y      = 20;
    private static final int EFFECTS_LABEL_Y  = 58;
    private static final int EFFECTS_ROW_Y    = 70;
    private static final int AUGMENTS_LABEL_Y = 128;
    private static final int AUGMENTS_ROW_Y   = 140;

    // Visible rows per section
    private static final int FORMS_VISIBLE_ROWS    = 2;
    private static final int EFFECTS_VISIBLE_ROWS  = 3;
    private static final int AUGMENTS_VISIBLE_ROWS = 3;

    // Search bar position (above gui, top-right)
    private int searchX, searchY, searchW;
    private static final int SEARCH_H = 14;

    public GrimoireScreen(ItemStack grimoireStack) {
        super(Component.literal("Grimoire"));
        this.grimoireStack = grimoireStack;
        this.grimoireItem  = (GrimoireItem) grimoireStack.getItem();

        GrimoireCaster caster = grimoireStack.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
        this.currentSpellSlot = caster.currentSlot();

        for (int i = 0; i < MAX_SPELLS; i++) {
            AbstractRune[] arr   = new AbstractRune[MAX_RUNES];
            List<AbstractRune> r = caster.getFormula(i).getRunes();
            for (int j = 0; j < Math.min(r.size(), MAX_RUNES); j++) arr[j] = r.get(j);
            slotFormulas.add(arr);
            slotNames.add(caster.getSpellName(i));
        }
        this.currentFormula = slotFormulas.get(currentSpellSlot).clone();
        this.typedText      = slotNames.get(currentSpellSlot);
        this.cursorPos      = typedText.length();
    }

    @Override
    protected void init() {
        super.init();

        guiLeft  = (width  - GUI_WIDTH)  / 2;
        guiTop   = (height - GUI_HEIGHT) / 2;
        slotRowY = guiTop + GUI_HEIGHT - BAR_H - SLOT_H - 8;

        int barY    = guiTop + GUI_HEIGHT - BAR_H + 4;
        textFieldW  = 80;
        textFieldX  = guiLeft + 8;
        textFieldY  = barY;
        clearBtnW   = 44;
        clearBtnX   = guiLeft + GUI_WIDTH / 2 + 4;
        clearBtnY   = barY;
        createBtnW  = 50;
        createBtnX  = guiLeft + GUI_WIDTH / 2 + clearBtnW + 12;
        createBtnY  = barY;

        // Search bar: top-right, above GUI
        searchW = 100;
        searchX = guiLeft + GUI_WIDTH - searchW;
        searchY = guiTop - SEARCH_H - 4;

        allForms.clear(); allEffects.clear(); allAugments.clear();
        for (AbstractRune rune : RuneRegistry.getAll()) {
            if      (rune instanceof AbstractFormRune)    allForms.add(rune);
            else if (rune instanceof AbstractEffectRune)  allEffects.add(rune);
            else if (rune instanceof AbstractAugmentRune) allAugments.add(rune);
        }
        applySearch();
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void applySearch() {
        String q = searchText.toLowerCase();
        filteredForms    = allForms.stream()
                .filter(r -> r.getName().toLowerCase().contains(q)).collect(Collectors.toList());
        filteredEffects  = allEffects.stream()
                .filter(r -> r.getName().toLowerCase().contains(q)).collect(Collectors.toList());
        filteredAugments = allAugments.stream()
                .filter(r -> r.getName().toLowerCase().contains(q)).collect(Collectors.toList());
        // Reset scroll when search changes
        scrollForms = scrollEffects = scrollAugments = 0;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.GRIMOIRE_BACKGROUND,
                guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        drawSearchBar(graphics, mouseX, mouseY);
        drawSpellTabs(graphics, mouseX, mouseY);
        drawSection(graphics, "Forms",    filteredForms,    guiLeft + 6, guiTop + FORMS_LABEL_Y,
                guiTop + FORMS_ROW_Y,    FORMS_VISIBLE_ROWS,    scrollForms,    mouseX, mouseY, false, true);
        drawSection(graphics, "Effects",  filteredEffects,  guiLeft + 6, guiTop + EFFECTS_LABEL_Y,
                guiTop + EFFECTS_ROW_Y,  EFFECTS_VISIBLE_ROWS,  scrollEffects,  mouseX, mouseY, false, false);
        drawSection(graphics, "Augments", filteredAugments, guiLeft + 6, guiTop + AUGMENTS_LABEL_Y,
                guiTop + AUGMENTS_ROW_Y, AUGMENTS_VISIBLE_ROWS, scrollAugments, mouseX, mouseY, true,  false);
        drawFormulaSlots(graphics, mouseX, mouseY);
        drawBottomBar(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
    }

    private void drawSearchBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int btnH = SEARCH_H;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.GRIMOIRE_TEXT_FIELD,
                searchX, searchY, searchW, btnH);

        String placeholder = searchText.isEmpty() && !searchFocused ? "Search runes..." : searchText;
        int    textColor   = searchText.isEmpty() && !searchFocused ? 0xFF555555 : 0xFFFFFFFF;

        // Cursor
        String display = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0
                ? insertCursor(searchText, searchCursor) : searchText;
        if (searchText.isEmpty() && !searchFocused) display = placeholder;

        graphics.text(font, Component.literal(display),
                searchX + 4, searchY + (btnH - 8) / 2, textColor, false);

        // Label
        graphics.text(font, Component.literal("🔍").withStyle(s -> s.withColor(0xFF888888)),
                searchX - 12, searchY + (btnH - 8) / 2, 0xFF888888, false);
    }

    private void drawSection(GuiGraphicsExtractor graphics, String label,
                             List<AbstractRune> runes,
                             int labelX, int labelY, int rowsStartY,
                             int visibleRows, int scrollOffset,
                             int mouseX, int mouseY,
                             boolean checkAugmentCompat, boolean isFormRow) {
        graphics.text(font, Component.literal(label), labelX, labelY, 0xFF9966ff, true);

        int maxTier = grimoireItem.getMaxTier();

        // Draw scroll arrows if needed
        int totalRows = (int) Math.ceil(runes.size() / (double) RUNES_PER_ROW);
        if (scrollOffset > 0) {
            // Up arrow
            graphics.text(font, Component.literal("▲"),
                    guiLeft + GUI_WIDTH - 14, labelY, 0xFF9966ff, false);
        }
        if (scrollOffset + visibleRows < totalRows) {
            // Down arrow
            graphics.text(font, Component.literal("▼"),
                    guiLeft + GUI_WIDTH - 14, labelY + 8, 0xFF9966ff, false);
        }

        for (int row = 0; row < visibleRows; row++) {
            int actualRow  = scrollOffset + row;
            int rowStart   = actualRow * RUNES_PER_ROW;
            int rowEnd     = Math.min(rowStart + RUNES_PER_ROW, runes.size());
            int ry         = rowsStartY + row * (RUNE_SIZE + 4);

            for (int i = rowStart; i < rowEnd; i++) {
                AbstractRune rune = runes.get(i);
                int rx = labelX + (i - rowStart) * RUNE_STEP;

                boolean locked       = rune.getTier() > maxTier;
                boolean incompatible = checkAugmentCompat && !isAugmentCompatible(rune);
                boolean formConflict = isFormRow && hasFormInFormula();
                boolean blocked      = locked || incompatible || formConflict;
                boolean hovered      = !blocked
                        && mouseX >= rx && mouseX <= rx + RUNE_SIZE
                        && mouseY >= ry && mouseY <= ry + RUNE_SIZE;

                if (hovered)
                    graphics.fill(rx - 1, ry - 1, rx + RUNE_SIZE + 1, ry + RUNE_SIZE + 1, 0x40FFFFFF);

                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, rune.getIcon(), rx, ry, RUNE_SIZE, RUNE_SIZE);

                if (locked)
                    graphics.fill(rx, ry, rx + RUNE_SIZE, ry + RUNE_SIZE, 0xAA222222);
                else if (formConflict)
                    graphics.fill(rx, ry, rx + RUNE_SIZE, ry + RUNE_SIZE, 0x88880000);
                else if (incompatible)
                    graphics.fill(rx, ry, rx + RUNE_SIZE, ry + RUNE_SIZE, 0x88222222);
            }
        }
    }

    private void drawFormulaSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        float spacing = (GUI_WIDTH - MAX_RUNES * SLOT_W) / (float)(MAX_RUNES + 1);
        for (int i = 0; i < MAX_RUNES; i++) {
            int sx = guiLeft + Math.round(spacing + i * (SLOT_W + spacing));
            int sy = slotRowY;

            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    TotalityGuiSprites.GRIMOIRE_SLOT, sx, sy, SLOT_W, SLOT_H);

            if (currentFormula[i] != null) {
                AbstractRune rune = currentFormula[i];
                int iconX = sx + (SLOT_W - RUNE_SIZE) / 2;
                int iconY = sy + (SLOT_H - RUNE_SIZE) / 2;
                boolean hovered = mouseX >= sx && mouseX <= sx + SLOT_W
                        && mouseY >= sy && mouseY <= sy + SLOT_H;

                if (hovered)
                    graphics.fill(sx, sy, sx + SLOT_W, sy + SLOT_H, 0x40FF4444);

                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        rune.getIcon(), iconX, iconY, RUNE_SIZE, RUNE_SIZE);
            }
        }
    }

    private void drawSpellTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int tabX = guiLeft + GUI_WIDTH;
        for (int i = 0; i < MAX_SPELLS; i++) {
            int tabY    = guiTop + 4 + i * (TAB_H + 2);
            boolean sel = i == currentSpellSlot;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    sel ? TotalityGuiSprites.SPELL_TAB_SELECTED
                            : TotalityGuiSprites.SPELL_TAB,
                    tabX, tabY, TAB_W, TAB_H);
            String num = String.valueOf(i + 1);
            graphics.text(font, Component.literal(num),
                    tabX + TAB_W / 2 - font.width(num) / 2,
                    tabY + TAB_H / 2 - 4,
                    sel ? 0xFFFFFFFF : 0xFF888888, true);
        }
    }

    private void drawBottomBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int btnH = 16;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.GRIMOIRE_TEXT_FIELD,
                textFieldX, textFieldY, textFieldW, btnH);

        String display;
        int textColor;
        if (typedText.isEmpty() && !textFieldFocused) {
            display   = "Spell Name...";
            textColor = 0xFF555555;
        } else {
            display   = textFieldFocused && (System.currentTimeMillis() / 500) % 2 == 0
                    ? insertCursor(typedText, cursorPos) : typedText;
            textColor = 0xFFFFFFFF;
        }

        // Truncate from left to fit
        int maxTW = textFieldW - 8;
        int start = 0;
        while (start < display.length() && font.width(display.substring(start)) > maxTW)
            start++;
        String truncated = display.substring(start);

        graphics.text(font, Component.literal(truncated),
                textFieldX + 4, textFieldY + (btnH - 8) / 2, textColor, false);

        boolean clearHovered = mouseX >= clearBtnX && mouseX <= clearBtnX + clearBtnW
                && mouseY >= clearBtnY && mouseY <= clearBtnY + btnH;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.GRIMOIRE_DARK_BACKGROUND,
                clearBtnX, clearBtnY, clearBtnW, btnH);
        graphics.text(font, Component.literal("✗ Clear"),
                clearBtnX + clearBtnW / 2 - font.width("✗ Clear") / 2,
                clearBtnY + (btnH - 8) / 2,
                clearHovered ? 0xFFFF6666 : 0xFFAA4444, false);

        boolean createHovered = mouseX >= createBtnX && mouseX <= createBtnX + createBtnW
                && mouseY >= createBtnY && mouseY <= createBtnY + btnH;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.GRIMOIRE_DARK_BACKGROUND,
                createBtnX, createBtnY, createBtnW, btnH);
        graphics.text(font, Component.literal("✓ Create"),
                createBtnX + createBtnW / 2 - font.width("✓ Create") / 2,
                createBtnY + (btnH - 8) / 2,
                createHovered ? 0xFF66FF66 : 0xFF44AA44, false);
    }

    private void drawTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int maxTier = grimoireItem.getMaxTier();
        int x       = guiLeft + 6;

        checkSectionTooltip(graphics, filteredForms,    x, guiTop + FORMS_ROW_Y,
                FORMS_VISIBLE_ROWS,    scrollForms,    maxTier, mouseX, mouseY, false, true);
        checkSectionTooltip(graphics, filteredEffects,  x, guiTop + EFFECTS_ROW_Y,
                EFFECTS_VISIBLE_ROWS,  scrollEffects,  maxTier, mouseX, mouseY, false, false);
        checkSectionTooltip(graphics, filteredAugments, x, guiTop + AUGMENTS_ROW_Y,
                AUGMENTS_VISIBLE_ROWS, scrollAugments, maxTier, mouseX, mouseY, true,  false);

        float spacing = (GUI_WIDTH - MAX_RUNES * SLOT_W) / (float)(MAX_RUNES + 1);
        for (int i = 0; i < MAX_RUNES; i++) {
            if (currentFormula[i] == null) continue;
            int sx = guiLeft + Math.round(spacing + i * (SLOT_W + spacing));
            if (mouseX >= sx && mouseX <= sx + SLOT_W
                    && mouseY >= slotRowY && mouseY <= slotRowY + SLOT_H) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal(currentFormula[i].getName())
                        .withStyle(s -> s.withColor(0xFF9966FF).withBold(true)));
                tt.add(Component.literal(currentFormula[i].getManaCost() + " mana")
                        .withStyle(s -> s.withColor(0xFFAAAAAA)));
                if (!currentFormula[i].getDescription().isEmpty())
                    tt.add(Component.literal(currentFormula[i].getDescription())
                            .withStyle(s -> s.withColor(0xFFCCCCCC).withItalic(true)));
                graphics.setComponentTooltipForNextFrame(font, tt, mouseX, mouseY);
            }
        }
    }

    private void checkSectionTooltip(GuiGraphicsExtractor graphics, List<AbstractRune> runes,
                                     int x, int rowsStartY,
                                     int visibleRows, int scrollOffset,
                                     int maxTier, int mouseX, int mouseY,
                                     boolean checkAugmentCompat, boolean isFormRow) {
        for (int row = 0; row < visibleRows; row++) {
            int actualRow = scrollOffset + row;
            int rowStart  = actualRow * RUNES_PER_ROW;
            int rowEnd    = Math.min(rowStart + RUNES_PER_ROW, runes.size());
            int ry        = rowsStartY + row * (RUNE_SIZE + 4);

            for (int i = rowStart; i < rowEnd; i++) {
                AbstractRune rune = runes.get(i);
                int rx = x + (i - rowStart) * RUNE_STEP;

                if (mouseX >= rx && mouseX <= rx + RUNE_SIZE
                        && mouseY >= ry && mouseY <= ry + RUNE_SIZE) {

                    boolean locked       = rune.getTier() > maxTier;
                    boolean incompatible = checkAugmentCompat && !isAugmentCompatible(rune);
                    boolean formConflict = isFormRow && hasFormInFormula();
                    String tierName = switch (rune.getTier()) {
                        case 1 -> "Novice";
                        case 2 -> "Apprentice";
                        case 3 -> "Archmage";
                        case 4 -> "Archon";
                        default -> "Unknown";
                    };

                    String augDesc = "";
                    if (checkAugmentCompat) {
                        AbstractRune lastEffect = null;
                        for (AbstractRune r : currentFormula)
                            if (r instanceof AbstractEffectRune) lastEffect = r;
                        if (lastEffect != null)
                            augDesc = lastEffect.getAugmentDescription(rune.getId().getPath());
                    }

                    List<Component> tt = new ArrayList<>();
                    tt.add(Component.literal(rune.getName())
                            .withStyle(s -> s.withColor(0xFF9966FF).withBold(true)));
                    tt.add(Component.literal("Tier: " + tierName + " | " + rune.getManaCost() + " mana")
                            .withStyle(s -> s.withColor(0xFFAAAAAA)));
                    if (!augDesc.isEmpty())
                        tt.add(Component.literal(augDesc)
                                .withStyle(s -> s.withColor(0xFFCCCCCC).withItalic(true)));
                    else if (!rune.getDescription().isEmpty())
                        tt.add(Component.literal(rune.getDescription())
                                .withStyle(s -> s.withColor(0xFFCCCCCC).withItalic(true)));
                    if (locked)
                        tt.add(Component.literal("Requires " + tierName + " Grimoire")
                                .withStyle(s -> s.withColor(0xFFFF4444)));
                    else if (formConflict)
                        tt.add(Component.literal("A spell can only have one Form")
                                .withStyle(s -> s.withColor(0xFFFF4444)));
                    else if (incompatible)
                        tt.add(Component.literal("Incompatible with current spell")
                                .withStyle(s -> s.withColor(0xFFFF4444)));

                    graphics.setComponentTooltipForNextFrame(font, tt, mouseX, mouseY);
                    return;
                }
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        double mouseX = mouse.x();
        double mouseY = mouse.y();
        int maxTier   = grimoireItem.getMaxTier();
        int x         = guiLeft + 6;

        // Search bar focus
        if (mouseX >= searchX && mouseX <= searchX + searchW
                && mouseY >= searchY && mouseY <= searchY + SEARCH_H) {
            searchFocused    = true;
            textFieldFocused = false;
            return true;
        }

        // Text field focus
        if (mouseX >= textFieldX && mouseX <= textFieldX + textFieldW
                && mouseY >= textFieldY && mouseY <= textFieldY + 16) {
            textFieldFocused = true;
            searchFocused    = false;
            // Place cursor at click position
            cursorPos = typedText.length(); // simple: put at end
            return true;
        }

        // Deselect both
        textFieldFocused = false;
        searchFocused    = false;

        // Spell tabs
        int tabX = guiLeft + GUI_WIDTH;
        for (int i = 0; i < MAX_SPELLS; i++) {
            int tabY = guiTop + 4 + i * (TAB_H + 2);
            if (mouseX >= tabX && mouseX <= tabX + TAB_W
                    && mouseY >= tabY && mouseY <= tabY + TAB_H) {
                saveCurrentSlot();
                sendToServer();
                currentSpellSlot = i;
                loadSlot(i);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new zcylas.totality.networking.magic.grimoire.SwitchGrimoireSlotPayload(i));
                playClick();
                return true;
            }
        }

        // Rune section clicks
        if (clickSection(filteredForms,    x, guiTop + FORMS_ROW_Y,
                FORMS_VISIBLE_ROWS,    scrollForms,    maxTier, mouseX, mouseY, false, true))  return true;
        if (clickSection(filteredEffects,  x, guiTop + EFFECTS_ROW_Y,
                EFFECTS_VISIBLE_ROWS,  scrollEffects,  maxTier, mouseX, mouseY, false, false)) return true;
        if (clickSection(filteredAugments, x, guiTop + AUGMENTS_ROW_Y,
                AUGMENTS_VISIBLE_ROWS, scrollAugments, maxTier, mouseX, mouseY, true,  false)) return true;

        // Formula slot removal
        float spacing = (GUI_WIDTH - MAX_RUNES * SLOT_W) / (float)(MAX_RUNES + 1);
        for (int i = 0; i < MAX_RUNES; i++) {
            int sx = guiLeft + Math.round(spacing + i * (SLOT_W + spacing));
            if (mouseX >= sx && mouseX <= sx + SLOT_W
                    && mouseY >= slotRowY && mouseY <= slotRowY + SLOT_H) {
                if (currentFormula[i] != null) {
                    currentFormula[i] = null;
                    playClick();
                }
                return true;
            }
        }

        // Clear / Create buttons
        if (mouseX >= clearBtnX && mouseX <= clearBtnX + clearBtnW
                && mouseY >= clearBtnY && mouseY <= clearBtnY + 16) {
            Arrays.fill(currentFormula, null);
            typedText = ""; cursorPos = 0;
            playClick();
            return true;
        }
        if (mouseX >= createBtnX && mouseX <= createBtnX + createBtnW
                && mouseY >= createBtnY && mouseY <= createBtnY + 16) {
            saveCurrentSlot();
            sendToServer();
            playClick();
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    private boolean clickSection(List<AbstractRune> runes, int x, int rowsStartY,
                                 int visibleRows, int scrollOffset,
                                 int maxTier, double mouseX, double mouseY,
                                 boolean checkAugmentCompat, boolean isFormRow) {
        for (int row = 0; row < visibleRows; row++) {
            int actualRow = scrollOffset + row;
            int rowStart  = actualRow * RUNES_PER_ROW;
            int rowEnd    = Math.min(rowStart + RUNES_PER_ROW, runes.size());
            int ry        = rowsStartY + row * (RUNE_SIZE + 4);

            for (int i = rowStart; i < rowEnd; i++) {
                AbstractRune rune = runes.get(i);
                int rx = x + (i - rowStart) * RUNE_STEP;

                if (mouseX >= rx && mouseX <= rx + RUNE_SIZE
                        && mouseY >= ry && mouseY <= ry + RUNE_SIZE) {
                    if (rune.getTier() > maxTier) return true;
                    if (checkAugmentCompat && !isAugmentCompatible(rune)) return true;
                    if (isFormRow && hasFormInFormula()) return true;
                    for (int j = 0; j < MAX_RUNES; j++) {
                        if (currentFormula[j] == null) {
                            currentFormula[j] = rune;
                            playClick();
                            break;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = guiLeft + 6;
        int delta = scrollY > 0 ? -1 : 1;

        // Check which section the mouse is over and scroll it
        if (isOverSection(mouseX, mouseY, x, guiTop + FORMS_ROW_Y, FORMS_VISIBLE_ROWS)) {
            int totalRows = (int) Math.ceil(filteredForms.size() / (double) RUNES_PER_ROW);
            scrollForms = Math.max(0, Math.min(scrollForms + delta, totalRows - FORMS_VISIBLE_ROWS));
            return true;
        }
        if (isOverSection(mouseX, mouseY, x, guiTop + EFFECTS_ROW_Y, EFFECTS_VISIBLE_ROWS)) {
            int totalRows = (int) Math.ceil(filteredEffects.size() / (double) RUNES_PER_ROW);
            scrollEffects = Math.max(0, Math.min(scrollEffects + delta, totalRows - EFFECTS_VISIBLE_ROWS));
            return true;
        }
        if (isOverSection(mouseX, mouseY, x, guiTop + AUGMENTS_ROW_Y, AUGMENTS_VISIBLE_ROWS)) {
            int totalRows = (int) Math.ceil(filteredAugments.size() / (double) RUNES_PER_ROW);
            scrollAugments = Math.max(0, Math.min(scrollAugments + delta, totalRows - AUGMENTS_VISIBLE_ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isOverSection(double mouseX, double mouseY,
                                  int x, int rowsStartY, int visibleRows) {
        int sectionH = visibleRows * (RUNE_SIZE + 4);
        return mouseX >= x && mouseX <= x + RUNES_PER_ROW * RUNE_STEP
                && mouseY >= rowsStartY && mouseY <= rowsStartY + sectionH;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();

        if (searchFocused) {
            if (event.isEscape()) { searchFocused = false; return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
                if (searchCursor > 0) {
                    searchText   = searchText.substring(0, searchCursor - 1)
                            + searchText.substring(searchCursor);
                    searchCursor = Math.max(0, searchCursor - 1);
                    applySearch();
                }
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)  { searchCursor = Math.max(0, searchCursor - 1); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { searchCursor = Math.min(searchText.length(), searchCursor + 1); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE && searchCursor < searchText.length()) {
                searchText = searchText.substring(0, searchCursor) + searchText.substring(searchCursor + 1);
                applySearch();
                return true;
            }
            return true;
        }

        if (textFieldFocused) {
            if (event.isEscape()) { textFieldFocused = false; return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !typedText.isEmpty() && cursorPos > 0) {
                typedText = typedText.substring(0, cursorPos - 1) + typedText.substring(cursorPos);
                cursorPos = Math.max(0, cursorPos - 1);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE && cursorPos < typedText.length()) {
                typedText = typedText.substring(0, cursorPos) + typedText.substring(cursorPos + 1);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)  { cursorPos = Math.max(0, cursorPos - 1); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { cursorPos = Math.min(typedText.length(), cursorPos + 1); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME)  { cursorPos = 0; return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_END)   { cursorPos = typedText.length(); return true; }
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (searchFocused && searchText.length() < 32) {
            searchText = searchText.substring(0, searchCursor)
                    + event.codepointAsString()
                    + searchText.substring(searchCursor);
            searchCursor++;
            applySearch();
            return true;
        }
        if (textFieldFocused && typedText.length() < 32) {
            typedText = typedText.substring(0, cursorPos)
                    + event.codepointAsString()
                    + typedText.substring(cursorPos);
            cursorPos++;
            return true;
        }
        return super.charTyped(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Insert a | cursor character at the given position. */
    private String insertCursor(String text, int pos) {
        pos = Math.max(0, Math.min(pos, text.length()));
        return text.substring(0, pos) + "|" + text.substring(pos);
    }

    private void saveCurrentSlot() {
        slotFormulas.set(currentSpellSlot, currentFormula.clone());
        slotNames.set(currentSpellSlot, typedText);
    }

    private void loadSlot(int slot) {
        GrimoireCaster caster = grimoireStack.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
        List<AbstractRune> runes = caster.getFormula(slot).getRunes();
        currentFormula = new AbstractRune[MAX_RUNES];
        for (int j = 0; j < Math.min(runes.size(), MAX_RUNES); j++)
            currentFormula[j] = runes.get(j);
        typedText  = caster.getSpellName(slot);
        cursorPos  = typedText.length();
        slotFormulas.set(slot, currentFormula.clone());
        slotNames.set(slot, typedText);
    }

    private void sendToServer() {
        GrimoireCaster current = grimoireStack.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
        GrimoireCaster updated = current.withCurrentSlot(currentSpellSlot);
        for (int i = 0; i < MAX_SPELLS; i++) {
            AbstractRune[] arr   = slotFormulas.get(i);
            List<AbstractRune> r = new ArrayList<>();
            for (AbstractRune rune : arr) if (rune != null) r.add(rune);
            updated = updated.withSlot(i, new ArcaneFormula(r), slotNames.get(i));
        }
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new UpdateGrimoirePayload(updated));
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isAugmentCompatible(AbstractRune augment) {
        List<AbstractRune> effects = new ArrayList<>();
        for (AbstractRune rune : currentFormula)
            if (rune instanceof AbstractEffectRune) effects.add(rune);
        if (effects.isEmpty()) return true;
        for (AbstractRune effect : effects)
            if (effect.getCompatibleAugments().contains(augment.getId().getPath())) return true;
        return false;
    }

    private boolean hasFormInFormula() {
        for (AbstractRune rune : currentFormula)
            if (rune instanceof AbstractFormRune) return true;
        return false;
    }

    @Override public boolean isInGameUi()    { return true;  }
    @Override public boolean isPauseScreen() { return false; }
}