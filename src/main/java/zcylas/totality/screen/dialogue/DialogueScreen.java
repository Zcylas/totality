package zcylas.totality.screen.dialogue;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.networking.dialogue.ChoiceDisplayData;
import zcylas.totality.networking.dialogue.DialogueChoicePayload;
import zcylas.totality.networking.dialogue.ShowDialogueStatePayload;

import java.util.List;

public class DialogueScreen extends Screen {

    // ── Colors (Blabber RPG palette) ──────────────────────────────────────────
    private static final int C_NPC_NAME      = 0xFFFFFFFF;
    private static final int C_NPC_TEXT      = 0xFFE0E0E0;
    private static final int C_CHOICE        = 0xFFD0D0D0;
    private static final int C_CHOICE_SEL    = 0xFFF0F066;
    private static final int C_CHOICE_LOCKED = 0xFFA0A0A0;
    private static final int C_NUM           = 0xFFD0D0D0;
    private static final int C_NUM_SEL       = 0xFFF0F066;
    private static final int C_HINT          = 0xFF606060;
    private static final int C_LOCK_ICON     = 0xFF707070;

    // Panel background gradient — matches Blabber's 0x00101010 → 0xC0101010 → 0xD0101010
    private static final int C_PANEL_FADE_TOP    = 0x00101010;
    private static final int C_PANEL_MID         = 0xC0101010;
    private static final int C_PANEL_BOTTOM      = 0xD0101010;
    private static final int C_SEPARATOR         = 0x40FFFFFF;
    private static final int C_SEL_HIGHLIGHT     = 0x28F0F066;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CHOICE_PANEL_H_FRACTION_NUM = 2;
    private static final int CHOICE_PANEL_H_FRACTION_DEN = 5;
    private static final int CHOICE_ROW_H    = 14;
    private static final int PANEL_PAD       = 8;
    private static final int PANEL_FADE_H    = 20;
    private static final int PORTRAIT_SIZE   = 64;
    private static final int PORTRAIT_PAD    = 16;

    // ── State ─────────────────────────────────────────────────────────────────
    private int npcEntityId;
    private Component npcName;
    private Component npcText;
    private List<ChoiceDisplayData> choices;
    private boolean unskippable;
    private boolean ended;

    private int selectedIndex = 0;

    public DialogueScreen(ShowDialogueStatePayload payload) {
        super(Component.literal("Dialogue"));
        applyPayload(payload);
    }

    public void applyUpdate(ShowDialogueStatePayload payload) {
        applyPayload(payload);
        selectedIndex = 0;
    }

    private void applyPayload(ShowDialogueStatePayload payload) {
        this.npcEntityId  = payload.npcEntityId();
        this.npcName      = payload.npcName();
        this.npcText      = payload.npcText();
        this.choices      = payload.choices();
        this.unskippable  = payload.unskippable();
        this.ended        = payload.ended();
    }

    @Override public boolean shouldCloseOnEsc() { return !unskippable; }
    @Override public boolean isPauseScreen()    { return false; }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int choicePanelH = height * CHOICE_PANEL_H_FRACTION_NUM / CHOICE_PANEL_H_FRACTION_DEN;
        int choicePanelY = height - choicePanelH;

        // Full-screen fade: transparent at top → dark at bottom (Blabber style)
        g.fillGradient(0, 0, width, choicePanelY, 0x00000000, C_PANEL_MID);

        drawUpperArea(g, 0, 0, width, choicePanelY);
        drawChoicePanel(g, 0, choicePanelY, width, choicePanelH);
        drawHint(g, height - 10);
    }

    private void drawUpperArea(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        boolean hasPortrait = npcEntityId != -1 && minecraft != null && minecraft.level != null;
        LivingEntity npcEntity = null;
        if (hasPortrait) {
            var entity = minecraft.level.getEntity(npcEntityId);
            if (entity instanceof LivingEntity living) npcEntity = living;
        }

        int textX, textW;
        if (npcEntity != null) {
            int portraitX = PORTRAIT_PAD;
            int portraitY = y + (h - PORTRAIT_SIZE) / 2;
            drawPortrait(g, npcEntity, portraitX, portraitY, PORTRAIT_SIZE);
            textX = PORTRAIT_PAD * 2 + PORTRAIT_SIZE;
            textW = w - textX - PANEL_PAD;
        } else {
            textX = w / 6;
            textW = w * 2 / 3;
        }

        int nameY = y + h / 3 - font.lineHeight - 4;
        int textY  = y + h / 3;

        if (!npcName.getString().isEmpty()) {
            drawCentered(g, npcName.getString(), textX + textW / 2, nameY, C_NPC_NAME, 1.0f);
        }
        g.textWithWordWrap(font, npcText, textX, textY, textW, C_NPC_TEXT, false);
    }

    private void drawPortrait(GuiGraphicsExtractor g, LivingEntity entity, int x, int y, int size) {
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x, y, x + size, y + size,
                    size / 2, 0.0625f, x + size / 2f, y + size / 4f, entity);
        } catch (Exception ignored) {}
    }

    private void drawChoicePanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        // Fade-in gradient at the top of the panel, then solid dark background
        g.fillGradient(x, y - PANEL_FADE_H, x + w, y, C_PANEL_FADE_TOP, C_PANEL_MID);
        g.fill(x, y, x + w, y + h, C_PANEL_BOTTOM);

        // Thin separator line
        g.fill(x, y, x + w, y + 1, C_SEPARATOR);

        // Choices — when ended/empty, panel is intentionally blank (hint at bottom is enough)
        if (ended || choices.isEmpty()) return;

        int cx = x + PANEL_PAD;
        int cy = y + PANEL_PAD;

        for (int i = 0; i < choices.size(); i++) {
            ChoiceDisplayData choice = choices.get(i);
            boolean selected = i == selectedIndex;
            boolean locked   = choice.locked();

            int numColor  = selected ? C_NUM_SEL  : C_NUM;
            int textColor = locked   ? C_CHOICE_LOCKED : selected ? C_CHOICE_SEL : C_CHOICE;

            String numLabel = (i + 1) + ". ";
            int numW = (int)(font.width(numLabel) * 0.65f);

            if (selected && !locked) {
                g.fill(cx - 2, cy - 1, x + w - PANEL_PAD, cy + (int)(font.lineHeight * 0.65f) + 1, C_SEL_HIGHLIGHT);
            }

            if (locked) {
                drawSmall(g, "✗ " + numLabel, cx, cy, C_LOCK_ICON);
                drawSmall(g, choice.text().getString(), cx + numW + 10, cy, textColor);
                if (selected && !choice.lockReason().isEmpty()) {
                    g.setTooltipForNextFrame(font, Component.literal(choice.lockReason()), cx, cy);
                }
            } else {
                drawSmall(g, numLabel, cx, cy, numColor);
                drawSmall(g, choice.text().getString(), cx + numW, cy, textColor);
            }

            cy += CHOICE_ROW_H;
            if (cy + CHOICE_ROW_H > y + h - PANEL_PAD) break;
        }
    }

    private void drawHint(GuiGraphicsExtractor g, int y) {
        String hint = ended || choices.isEmpty()
                ? "[E: Continue]"
                : "[W/S: Navigate]  [E: Select]  [1-9: Quick Select]";
        drawSmall(g, hint, (width - (int)(font.width(hint) * 0.65f)) / 2, y, C_HINT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawCentered(GuiGraphicsExtractor g, String text, int cx, int y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.text(font, Component.literal(text),
                Math.round(cx / scale) - font.width(text) / 2,
                Math.round(y / scale), color, false);
        g.pose().popMatrix();
    }

    private void drawSmall(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().scale(0.65f, 0.65f);
        g.text(font, Component.literal(text),
                Math.round(x / 0.65f),
                Math.round(y / 0.65f), color, false);
        g.pose().popMatrix();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent input) {
        var options = Minecraft.getInstance().options;
        int key = input.key();

        if (options.keyDown.matches(input)) { scrollSelection(1);  return true; }
        if (options.keyUp.matches(input))   { scrollSelection(-1); return true; }

        if (options.keyInventory.matches(input) || key == GLFW.GLFW_KEY_ENTER) {
            if (ended || choices.isEmpty()) { onClose(); return true; }
            confirmSelection(selectedIndex);
            return true;
        }

        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int idx = key - GLFW.GLFW_KEY_1;
            if (idx < choices.size()) { selectedIndex = idx; confirmSelection(idx); }
            return true;
        }

        return super.keyPressed(input);
    }

    private void scrollSelection(int delta) {
        if (choices.isEmpty()) return;
        selectedIndex = Mth.clamp(selectedIndex + delta, 0, choices.size() - 1);
    }

    private void confirmSelection(int index) {
        if (index < 0 || index >= choices.size()) return;
        if (choices.get(index).locked()) return;
        ClientPlayNetworking.send(new DialogueChoicePayload(index));
    }
}
