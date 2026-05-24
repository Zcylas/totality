package zcylas.totality.screen.magic;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.magic.grimoire.GrimoireCaster;
import zcylas.totality.api.magic.grimoire.MagicComponents;
import zcylas.totality.api.magic.grimoire.rune.AbstractFormRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;
import zcylas.totality.init.ModKeybinds;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.networking.magic.grimoire.SwitchGrimoireSlotPayload;

import java.util.List;

public class GrimoireRadialScreen extends Screen {

    private static final int   MAX_SLOTS   = 10;
    private static final float RADIUS_IN   = 40f;
    private static final float RADIUS_OUT  = 90f;
    private static final float PRECISION   = 3.0f;

    private final ItemStack      grimoireStack;
    private final GrimoireItem   grimoireItem;
    private final AbstractRune[][] slotFormulas = new AbstractRune[MAX_SLOTS][];
    private final String[]         slotNames    = new String[MAX_SLOTS];

    private int selectedSlot = -1;
    private int currentSlot  = 0;

    public GrimoireRadialScreen(ItemStack grimoireStack) {
        super(Component.literal(""));
        this.grimoireStack = grimoireStack;
        this.grimoireItem  = (GrimoireItem) grimoireStack.getItem();

        GrimoireCaster caster = grimoireStack.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
        this.currentSlot = caster.currentSlot();

        for (int i = 0; i < MAX_SLOTS; i++) {
            List<AbstractRune> runes = caster.getFormula(i).getRunes();
            slotFormulas[i] = runes.toArray(new AbstractRune[0]);
            String name     = caster.getSpellName(i);
            slotNames[i]    = name.isEmpty() ? "" : name;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY, float a) {
        // Transparent — game world renders behind
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        int cx = width  / 2;
        int cy = height / 2;

        double dx   = mouseX - cx;
        double dy   = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        selectedSlot = -1;
        if (dist >= RADIUS_IN && dist <= RADIUS_OUT) {
            double angle    = Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 360;
            double adjusted = (angle + 90) % 360;
            selectedSlot    = (int)(adjusted / 360.0 * MAX_SLOTS) % MAX_SLOTS;
        }

        for (int i = 0; i < MAX_SLOTS; i++) {
            float halfSlice = 360f / MAX_SLOTS / 2f;
            float centerDeg = -90f + i * (360f / MAX_SLOTS);
            float startDeg  = centerDeg - halfSlice;
            float endDeg    = centerDeg + halfSlice;

            boolean sel    = i == selectedSlot;
            boolean active = i == currentSlot;

            int r     = sel ? 63  : (active ? 40  : 0);
            int g     = sel ? 161 : (active ? 40  : 0);
            int b     = sel ? 191 : (active ? 120 : 0);
            int alpha = sel ? 180 : 100;

            drawSlice(graphics, cx, cy, startDeg, endDeg, r, g, b, alpha);
        }

        float itemRadius = (RADIUS_IN + RADIUS_OUT) * 0.5f;
        for (int i = 0; i < MAX_SLOTS; i++) {
            float centerDeg = -90f + i * (360f / MAX_SLOTS);
            float rad       = (float) Math.toRadians(centerDeg);
            int ix          = (int)(cx + itemRadius * Math.cos(rad));
            int iy          = (int)(cy + itemRadius * Math.sin(rad));

            String num     = String.valueOf(i + 1);
            int numColor   = i == selectedSlot ? 0xFFFFFFFF
                    : i == currentSlot  ? 0xFF88DDFF
                    : 0xFFAAAAAA;
            graphics.text(font, Component.literal(num),
                    ix - font.width(num) / 2, iy - 12, numColor, true);

            String name = slotNames[i];
            if (!name.isEmpty()) {
                int nameColor = i == selectedSlot ? 0xFFFFFFFF : 0xFF888888;
                graphics.text(font, Component.literal(name),
                        ix - font.width(name) / 2, iy - 2, nameColor, true);
            }

            if (slotFormulas[i].length > 0
                    && slotFormulas[i][0] instanceof AbstractFormRune form) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        form.getIcon(), ix - 8, iy + 8, 16, 16);
            }
        }

        if (selectedSlot != -1) {
            String centerText = "Slot " + (selectedSlot + 1);
            graphics.text(font, Component.literal(centerText),
                    cx - font.width(centerText) / 2, cy - 4,
                    0xFFFFFFFF, true);
        }
    }

    private void drawSlice(GuiGraphicsExtractor graphics, int cx, int cy,
                           float startDeg, float endDeg,
                           int r, int g, int b, int alpha) {
        float start    = (float) Math.toRadians(startDeg);
        float end      = (float) Math.toRadians(endDeg);
        float range    = end - start;
        int sections   = Math.max(1, (int) Math.ceil(
                Math.abs(Math.toDegrees(range)) / PRECISION));
        int color      = (alpha << 24) | (r << 16) | (g << 8) | b;

        for (int i = 0; i < sections; i++) {
            float a1 = start + (i       / (float) sections) * range;
            float a2 = start + ((i + 1) / (float) sections) * range;

            int steps = (int)(RADIUS_OUT - RADIUS_IN);
            for (int s = 0; s <= steps; s++) {
                float t   = s / (float) steps;
                float rad = RADIUS_IN + t * (RADIUS_OUT - RADIUS_IN);
                int lx1   = cx + (int)(rad * Mth.cos(a1));
                int ly1   = cy + (int)(rad * Mth.sin(a1));
                int lx2   = cx + (int)(rad * Mth.cos(a2));
                int ly2   = cy + (int)(rad * Mth.sin(a2));
                graphics.fill(Math.min(lx1, lx2), Math.min(ly1, ly2),
                        Math.max(lx1, lx2) + 1, Math.max(ly1, ly2) + 1,
                        color);
            }
        }
    }

    @Override
    public void tick() {
        Window window = Minecraft.getInstance().getWindow();
        boolean vDown = InputConstants.isKeyDown(
                window, ModKeybinds.OPEN_RADIAL.getDefaultKey().getValue());
        if (!vDown) {
            if (selectedSlot != -1 && selectedSlot != currentSlot) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new SwitchGrimoireSlotPayload(selectedSlot));
            }
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi()    { return true;  }
}