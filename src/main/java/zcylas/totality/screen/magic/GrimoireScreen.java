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

public class GrimoireScreen extends Screen {

    private static final int GUI_WIDTH  = 248;
    private static final int GUI_HEIGHT = 200;
    private static final int SLOT_W     = 22;
    private static final int SLOT_H     = 20;
    private static final int MAX_RUNES  = 10;
    private static final int TAB_W      = 16;
    private static final int TAB_H      = 18;
    private static final int MAX_SPELLS = 10;
    private static final int BAR_H      = 24;
    private static final int RUNE_SIZE  = 16;

    private final ItemStack grimoireStack;
    private final GrimoireItem grimoireItem;

    private AbstractRune[] currentFormula = new AbstractRune[MAX_RUNES];
    private int currentSpellSlot = 0;

    private final List<AbstractRune[]> slotFormulas = new ArrayList<>();
    private final List<String> slotNames = new ArrayList<>();

    private String typedText = "";
    private boolean textFieldFocused = false;

    private int guiLeft, guiTop;
    private int slotRowY;
    private int textFieldX, textFieldY, textFieldW;
    private int clearBtnX, clearBtnY, clearBtnW;
    private int createBtnX, createBtnY, createBtnW;

    private List<AbstractRune> formRunes    = new ArrayList<>();
    private List<AbstractRune> effectRunes  = new ArrayList<>();
    private List<AbstractRune> augmentRunes = new ArrayList<>();

    public GrimoireScreen(ItemStack grimoireStack) {
        super(Component.literal("Grimoire"));
        this.grimoireStack = grimoireStack;
        this.grimoireItem  = (GrimoireItem) grimoireStack.getItem();

        GrimoireCaster caster = grimoireStack.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
        this.currentSpellSlot = caster.currentSlot();

        // Load all slots from the item
        for (int i = 0; i < MAX_SPELLS; i++) {
            AbstractRune[] arr   = new AbstractRune[MAX_RUNES];
            List<AbstractRune> r = caster.getFormula(i).getRunes();
            for (int j = 0; j < Math.min(r.size(), MAX_RUNES); j++) arr[j] = r.get(j);
            slotFormulas.add(arr);
            slotNames.add(caster.getSpellName(i));
        }
        this.currentFormula = slotFormulas.get(currentSpellSlot).clone();
        this.typedText      = slotNames.get(currentSpellSlot);

    }

    @Override
    protected void init() {
        super.init();

        guiLeft  = (width  - GUI_WIDTH)  / 2;
        guiTop   = (height - GUI_HEIGHT) / 2;
        slotRowY = guiTop + GUI_HEIGHT - BAR_H - SLOT_H - 8;

        int barY = guiTop + GUI_HEIGHT - BAR_H + 4;
        textFieldW  = 80;
        textFieldX  = guiLeft + 8;
        textFieldY  = barY;
        clearBtnW   = 44;
        clearBtnX   = guiLeft + GUI_WIDTH / 2 + 4;
        clearBtnY   = barY;
        createBtnW  = 50;
        createBtnX  = guiLeft + GUI_WIDTH / 2 + clearBtnW + 12;
        createBtnY  = barY;

        formRunes.clear(); effectRunes.clear(); augmentRunes.clear();
        for (AbstractRune rune : RuneRegistry.getAll()) {
            if (rune instanceof AbstractFormRune)         formRunes.add(rune);
            else if (rune instanceof AbstractEffectRune)  effectRunes.add(rune);
            else if (rune instanceof AbstractAugmentRune) augmentRunes.add(rune);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.GRIMOIRE_BACKGROUND,
                guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        drawSpellTabs(graphics, mouseX, mouseY);
        drawAvailableRunes(graphics, mouseX, mouseY);
        drawFormulaSlots(graphics, mouseX, mouseY);
        drawBottomBar(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
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

    private void drawAvailableRunes(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x       = guiLeft + 6;
        int maxTier = grimoireItem.getMaxTier();

        graphics.text(font, Component.literal("Forms"),
                x, guiTop + 10, 0xFF9966ff, true);
        drawRuneRow(graphics, formRunes,    x, guiTop + 20,  maxTier, mouseX, mouseY, false);
        graphics.text(font, Component.literal("Effects"),
                x, guiTop + 50, 0xFF9966ff, true);
        drawRuneRow(graphics, effectRunes,  x, guiTop + 60,  maxTier, mouseX, mouseY, false);
        graphics.text(font, Component.literal("Augments"),
                x, guiTop + 90, 0xFF9966ff, true);
        drawRuneRow(graphics, augmentRunes, x, guiTop + 100, maxTier, mouseX, mouseY, true);    }

    private void drawRuneRow(GuiGraphicsExtractor graphics, List<AbstractRune> runes,
                             int x, int y, int maxTier, int mouseX, int mouseY,
                             boolean checkAugmentCompat) {
        for (int i = 0; i < runes.size(); i++) {
            AbstractRune rune = runes.get(i);
            int rx = x + i * (RUNE_SIZE + 2);
            int ry = y;

            boolean locked       = rune.getTier() > maxTier;
            boolean incompatible = checkAugmentCompat && !isAugmentCompatible(rune);
            boolean blocked      = locked || incompatible;
            boolean hovered      = !blocked
                    && mouseX >= rx && mouseX <= rx + RUNE_SIZE
                    && mouseY >= ry && mouseY <= ry + RUNE_SIZE;

            if (hovered)
                graphics.fill(rx - 1, ry - 1, rx + RUNE_SIZE + 1, ry + RUNE_SIZE + 1, 0x40FFFFFF);

            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, rune.getIcon(), rx, ry, RUNE_SIZE, RUNE_SIZE);

            if (locked)
                graphics.fill(rx, ry, rx + RUNE_SIZE, ry + RUNE_SIZE, 0xAA222222);
            else if (incompatible)
                graphics.fill(rx, ry, rx + RUNE_SIZE, ry + RUNE_SIZE, 0x88222222);
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
                AbstractRune rune   = currentFormula[i];
                int iconX           = sx + (SLOT_W - RUNE_SIZE) / 2;
                int iconY           = sy + (SLOT_H - RUNE_SIZE) / 2;
                boolean hovered     = mouseX >= sx && mouseX <= sx + SLOT_W
                        && mouseY >= sy && mouseY <= sy + SLOT_H;

                if (hovered)
                    graphics.fill(sx, sy, sx + SLOT_W, sy + SLOT_H, 0x40FF4444);

                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        rune.getIcon(), iconX, iconY, RUNE_SIZE, RUNE_SIZE);
            }
        }
    }

    private void drawBottomBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int btnH = 16;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.GRIMOIRE_TEXT_FIELD,
                textFieldX, textFieldY, textFieldW, btnH);

        String display  = typedText.isEmpty() && !textFieldFocused ? "Spell Name..." : typedText;
        int textColor   = typedText.isEmpty() && !textFieldFocused ? 0xFF555555 : 0xFFFFFFFF;
        int maxTW       = textFieldW - 8;
        int start       = 0;
        while (start < display.length() && font.width(display.substring(start)) > maxTW)
            start++;
        String truncated  = display.substring(start);
        String withCursor = textFieldFocused && (System.currentTimeMillis() / 500) % 2 == 0
                ? truncated + "|" : truncated;
        graphics.text(font, Component.literal(withCursor),
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

        checkRuneRowTooltip(graphics, formRunes,    x, guiTop + 20,  maxTier, mouseX, mouseY, false);
        checkRuneRowTooltip(graphics, effectRunes,  x, guiTop + 60,  maxTier, mouseX, mouseY, false);
        checkRuneRowTooltip(graphics, augmentRunes, x, guiTop + 100, maxTier, mouseX, mouseY, true);

        float spacing = (GUI_WIDTH - MAX_RUNES * SLOT_W) / (float)(MAX_RUNES + 1);
        for (int i = 0; i < MAX_RUNES; i++) {
            if (currentFormula[i] == null) continue;
            int sx = guiLeft + Math.round(spacing + i * (SLOT_W + spacing));
            if (mouseX >= sx && mouseX <= sx + SLOT_W
                    && mouseY >= slotRowY && mouseY <= slotRowY + SLOT_H) {
                List<Component> slotTooltip = new ArrayList<>();
                slotTooltip.add(Component.literal(currentFormula[i].getName())
                        .withStyle(s -> s.withColor(0xFF9966FF).withBold(true)));
                slotTooltip.add(Component.literal(currentFormula[i].getManaCost() + " mana")
                        .withStyle(s -> s.withColor(0xFFAAAAAA)));
                if (!currentFormula[i].getDescription().isEmpty()) {
                    slotTooltip.add(Component.literal(currentFormula[i].getDescription())
                            .withStyle(s -> s.withColor(0xFFCCCCCC).withItalic(true)));
                }
                graphics.setComponentTooltipForNextFrame(font, slotTooltip, mouseX, mouseY);
            }
        }
    }

    private void checkRuneRowTooltip(GuiGraphicsExtractor graphics, List<AbstractRune> runes,
                                     int x, int y, int maxTier, int mouseX, int mouseY,
                                     boolean checkAugmentCompat) {
        for (int i = 0; i < runes.size(); i++) {
            AbstractRune rune = runes.get(i);
            int rx = x + i * (RUNE_SIZE + 2);
            int ry = y;

            if (mouseX >= rx && mouseX <= rx + RUNE_SIZE
                    && mouseY >= ry && mouseY <= ry + RUNE_SIZE) {
                boolean locked       = rune.getTier() > maxTier;
                boolean incompatible = checkAugmentCompat && !isAugmentCompatible(rune);
                String tierName = switch (rune.getTier()) {
                    case 1 -> "Novice";
                    case 2 -> "Apprentice";
                    case 3 -> "Archmage";
                    default -> "Unknown";
                };

                // Find effect-specific augment description
                String augDesc = "";
                if (checkAugmentCompat) {
                    AbstractRune lastEffect = null;
                    for (AbstractRune r : currentFormula) {
                        if (r instanceof AbstractEffectRune) lastEffect = r;
                    }
                    if (lastEffect != null) {
                        augDesc = lastEffect.getAugmentDescription(rune.getId().getPath());
                    }
                }

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(rune.getName())
                        .withStyle(s -> s.withColor(0xFF9966FF).withBold(true)));
                tooltip.add(Component.literal("Tier: " + tierName + " | " + rune.getManaCost() + " mana")
                        .withStyle(s -> s.withColor(0xFFAAAAAA)));
                if (!augDesc.isEmpty()) {
                    tooltip.add(Component.literal(augDesc)
                            .withStyle(s -> s.withColor(0xFFCCCCCC).withItalic(true)));
                } else if (!rune.getDescription().isEmpty()) {
                    tooltip.add(Component.literal(rune.getDescription())
                            .withStyle(s -> s.withColor(0xFFCCCCCC).withItalic(true)));
                }
                if (locked) {
                    tooltip.add(Component.literal("Requires " + tierName + " Grimoire")
                            .withStyle(s -> s.withColor(0xFFFF4444)));
                } else if (incompatible) {
                    tooltip.add(Component.literal("Incompatible with current spell")
                            .withStyle(s -> s.withColor(0xFFFF4444)));
                }

                graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        double mouseX = mouse.x();
        double mouseY = mouse.y();
        int maxTier   = grimoireItem.getMaxTier();
        int x         = guiLeft + 6;

        if (mouseX >= textFieldX && mouseX <= textFieldX + textFieldW
                && mouseY >= textFieldY && mouseY <= textFieldY + 16) {
            textFieldFocused = true;
            return true;
        } else {
            textFieldFocused = false;
        }

        int tabX = guiLeft + GUI_WIDTH;
        for (int i = 0; i < MAX_SPELLS; i++) {
            int tabY = guiTop + 4 + i * (TAB_H + 2);
            if (mouseX >= tabX && mouseX <= tabX + TAB_W
                    && mouseY >= tabY && mouseY <= tabY + TAB_H) {
                saveCurrentSlot();
                // Save current slot to server before switching
                sendToServer();
                // Then switch
                currentSpellSlot = i;
                loadSlot(i);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new zcylas.totality.networking.magic.grimoire.SwitchGrimoireSlotPayload(i));
                playClick();
                return true;
            }
        }

        if (clickRuneRow(formRunes,    x, guiTop + 20,  maxTier, mouseX, mouseY, false)) return true;
        if (clickRuneRow(effectRunes,  x, guiTop + 60,  maxTier, mouseX, mouseY, false)) return true;
        if (clickRuneRow(augmentRunes, x, guiTop + 100, maxTier, mouseX, mouseY, true))  return true;

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

        if (mouseX >= clearBtnX && mouseX <= clearBtnX + clearBtnW
                && mouseY >= clearBtnY && mouseY <= clearBtnY + 16) {
            Arrays.fill(currentFormula, null);
            typedText = "";
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

    private boolean clickRuneRow(List<AbstractRune> runes, int x, int y,
                                 int maxTier, double mouseX, double mouseY,
                                 boolean checkAugmentCompat) {
        for (int i = 0; i < runes.size(); i++) {
            AbstractRune rune = runes.get(i);
            int rx = x + i * (RUNE_SIZE + 2);
            int ry = y;

            if (mouseX >= rx && mouseX <= rx + RUNE_SIZE
                    && mouseY >= ry && mouseY <= ry + RUNE_SIZE) {
                if (rune.getTier() > maxTier) return true;
                if (checkAugmentCompat && !isAugmentCompatible(rune)) return true;
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
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (textFieldFocused) {
            if (event.isEscape()) { textFieldFocused = false; return true; }
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !typedText.isEmpty()) {
                typedText = typedText.substring(0, typedText.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (textFieldFocused && typedText.length() < 32) {
            typedText += event.codepointAsString();
            return true;
        }
        return super.charTyped(event);
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
        for (int j = 0; j < Math.min(runes.size(), MAX_RUNES); j++) {
            currentFormula[j] = runes.get(j);
        }
        typedText = caster.getSpellName(slot);
        // Also update local cache
        slotFormulas.set(slot, currentFormula.clone());
        slotNames.set(slot, typedText);
    }

    private void sendToServer() {
        // Build full updated GrimoireCaster from local slot data
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
        // Find the last effect rune in the current formula
        AbstractRune lastEffect = null;
        for (AbstractRune rune : currentFormula) {
            if (rune instanceof AbstractEffectRune) lastEffect = rune;
        }
        // If no effect yet, show all augments as available
        if (lastEffect == null) return true;
        // Check if this augment is in the effect's compatible set
        return lastEffect.getCompatibleAugments().contains(augment.getId().getPath());
    }
    @Override public boolean isInGameUi()    { return true;  }
    @Override public boolean isPauseScreen() { return false; }
}