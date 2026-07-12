package zcylas.totality.screen.rest;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.rpg.rest.RestManager;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.api.rpg.rest.ShortRestActivity;
import zcylas.totality.api.rpg.rest.ShortRestLength;
import zcylas.totality.client.rest.ClientRestManager;
import zcylas.totality.networking.rest.CancelRestPayload;
import zcylas.totality.networking.rest.RequestRestPayload;
import zcylas.totality.networking.rest.ResumeRestPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Short/Long Rest choice popup — shared by the Rest ability and bed right-clicks
 * (the only difference between the two triggers is whether bedPos is null).
 * If a rest is already in progress (re-opened via the ability/bed while resting),
 * shows a Cancel-only view instead of the normal choice menu.
 */
public class RestChoiceScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int ROW_H = 22;
    private static final int PAD = 10;
    private static final int TITLE_H = 16;

    private static final int C_BG          = 0xE0101010;
    private static final int C_BORDER      = 0xFF8A5A2B; // copper, matches the mod's UI language
    private static final int C_TITLE       = 0xFFF0F066;
    private static final int C_ROW         = 0xFFD0D0D0;
    private static final int C_ROW_HOVER   = 0xFFF0F066;
    private static final int C_ROW_BG_HOVER = 0x40F0F066;
    private static final int C_ROW_DISABLED = 0xFF707070;

    private enum Step { MAIN, SHORT_DURATION, SHORT_ACTIVITY }

    @Nullable private final BlockPos bedPos;
    private final int shortRestRemaining;
    private Step step = Step.MAIN;
    @Nullable private ShortRestLength chosenLength;

    private final List<Row> rows = new ArrayList<>();

    private record Row(String label, Runnable action, boolean enabled) {
        Row(String label, Runnable action) { this(label, action, true); }
    }

    public RestChoiceScreen(@Nullable BlockPos bedPos, int shortRestRemaining) {
        super(Component.literal("Rest"));
        this.bedPos = bedPos;
        this.shortRestRemaining = shortRestRemaining;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        if (ClientRestManager.isActive()) {
            rows.add(new Row("Cancel Rest", this::cancelActiveRest));
            rows.add(new Row("Keep Resting", this::resumeActiveRest));
            return;
        }

        switch (step) {
            case MAIN -> {
                rows.add(new Row("Short Rest  " + pips(shortRestRemaining, RestManager.SHORT_REST_CAP),
                        () -> step = Step.SHORT_DURATION, shortRestRemaining > 0));
                rows.add(new Row("Long Rest", () -> confirm(RestType.LONG, null, null)));
                rows.add(new Row("Cancel", this::onClose));
            }
            case SHORT_DURATION -> {
                rows.add(new Row("30 Minutes", () -> pickLength(ShortRestLength.THIRTY_MIN)));
                rows.add(new Row("1 Hour", () -> pickLength(ShortRestLength.ONE_HOUR)));
                rows.add(new Row("2 Hours", () -> pickLength(ShortRestLength.TWO_HOURS)));
                rows.add(new Row("Back", () -> step = Step.MAIN));
            }
            case SHORT_ACTIVITY -> {
                rows.add(new Row("Nap", () -> confirm(RestType.SHORT, chosenLength, ShortRestActivity.NAP)));
                rows.add(new Row("Read", () -> confirm(RestType.SHORT, chosenLength, ShortRestActivity.READ)));
                rows.add(new Row("Meditate", () -> confirm(RestType.SHORT, chosenLength, ShortRestActivity.MEDITATE)));
                rows.add(new Row("Back", () -> step = Step.SHORT_DURATION));
            }
        }
    }

    /** e.g. "■ ■" (both available), "■ □" (one used), "□ □" (none left). */
    private static String pips(int remaining, int cap) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cap; i++) {
            if (i > 0) sb.append(' ');
            sb.append(i < remaining ? '■' : '□');
        }
        return sb.toString();
    }

    private void pickLength(ShortRestLength length) {
        chosenLength = length;
        step = Step.SHORT_ACTIVITY;
    }

    private void confirm(RestType type, @Nullable ShortRestLength length, @Nullable ShortRestActivity activity) {
        ClientPlayNetworking.send(new RequestRestPayload(type, length, bedPos, activity));
        onClose();
    }

    private void cancelActiveRest() {
        ClientPlayNetworking.send(new CancelRestPayload());
        onClose();
    }

    private void resumeActiveRest() {
        ClientPlayNetworking.send(new ResumeRestPayload());
        onClose();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x80000000);

        int panelH = PAD * 2 + TITLE_H + rows.size() * ROW_H;
        int x = (width - PANEL_W) / 2;
        int y = (height - panelH) / 2;

        g.fill(x, y, x + PANEL_W, y + panelH, C_BG);
        g.fill(x, y, x + PANEL_W, y + 1, C_BORDER);
        g.fill(x, y + panelH - 1, x + PANEL_W, y + panelH, C_BORDER);
        g.fill(x, y, x + 1, y + panelH, C_BORDER);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + panelH, C_BORDER);

        String title;
        if (ClientRestManager.isActive()) {
            title = "Resting...";
        } else if (step == Step.SHORT_DURATION) {
            title = "Short Rest — Duration";
        } else if (step == Step.SHORT_ACTIVITY) {
            title = "Short Rest — Activity";
        } else {
            title = "Rest";
        }
        g.text(font, Component.literal(title), x + (PANEL_W - font.width(title)) / 2, y + PAD, C_TITLE, false);

        int rowY = y + PAD + TITLE_H;
        for (Row row : rows) {
            boolean hovered = row.enabled() && mx >= x && mx <= x + PANEL_W && my >= rowY && my <= rowY + ROW_H;
            if (hovered) g.fill(x + 4, rowY, x + PANEL_W - 4, rowY + ROW_H, C_ROW_BG_HOVER);
            int color = !row.enabled() ? C_ROW_DISABLED : hovered ? C_ROW_HOVER : C_ROW;
            g.text(font, Component.literal(row.label()), x + (PANEL_W - font.width(row.label())) / 2,
                    rowY + (ROW_H - font.lineHeight) / 2, color, false);
            rowY += ROW_H;
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        int panelH = PAD * 2 + TITLE_H + rows.size() * ROW_H;
        int x = (width - PANEL_W) / 2;
        int y = (height - panelH) / 2;
        int rowY = y + PAD + TITLE_H;

        for (Row row : rows) {
            if (row.enabled() && mx >= x && mx <= x + PANEL_W && my >= rowY && my <= rowY + ROW_H) {
                row.action().run();
                rebuildRows();
                return true;
            }
            rowY += ROW_H;
        }
        return super.mouseClicked(mouse, dc);
    }
}
