package zcylas.totality.screen.quest;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.networking.quest.QuestEntryDisplayData;
import zcylas.totality.networking.quest.ShowQuestStatePayload;
import zcylas.totality.networking.quest.TrackQuestPayload;
import zcylas.totality.screen.phone.PhoneAppGridScreen;
import zcylas.totality.screen.phone.PhoneFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Quests app — matches the layout of {@code Context/quest_mockup.png} (BACK + title header,
 * ACTIVE/DAILY/COMPLETED tabs, left quest list / right detail panel, bottom TRACK-ABANDON-
 * CONTINUE bar). Full-screen like other phone apps (Character, Inventory) — not phone-framed,
 * matching the mockup itself and the "apps open full screen, phone frame only wraps the grid"
 * convention from the planning doc.
 */
public class QuestScreen extends Screen {

    private static final int COLOR_BG          = 0xFF05070A;
    private static final int COLOR_PANEL_BG    = 0xFF0A0A0A;
    private static final int COLOR_CELL_BG     = 0xFF101010;
    private static final int COLOR_CELL_HOV    = 0xFF1A1A1A;
    private static final int COLOR_COPPER        = 0xFFB87A1A;
    private static final int COLOR_COPPER_BRIGHT = 0xFFD4A030;
    private static final int COLOR_COPPER_DIM     = 0xFF7A5010;
    private static final int COLOR_ACCENT      = 0xFF00CCFF;
    private static final int COLOR_LABEL       = 0xFFCCCCCC;
    private static final int COLOR_DIM         = 0xFF888888;
    private static final int COLOR_GREEN       = 0xFF44CC44;

    private static final int PAD = 8;
    private static final int HEADER_H = 24;
    private static final int TAB_H = 16;
    private static final int BOTTOM_H = 22;
    private static final int DETAIL_INSET = 4;

    private enum Tab { ACTIVE, DAILY, COMPLETED }

    private final PhoneFrame frame;
    private ShowQuestStatePayload state;
    private Tab tab = Tab.ACTIVE;
    private int selected = -1;

    // Detail panel scroll state — content (objectives + rewards) can exceed the
    // panel's fixed height, so it scrolls with the mouse wheel instead of overflowing.
    private int detailScroll = 0;
    private int detailContentHeight = 0;
    private int detailBoxX, detailBoxY, detailBoxW, detailBoxH;

    public QuestScreen(ShowQuestStatePayload initial, PhoneFrame frame) {
        super(Component.literal("Quests"));
        this.state = initial;
        this.frame = frame;
    }

    public void applyUpdate(ShowQuestStatePayload update) {
        this.state = update;
        List<QuestEntryDisplayData> list = currentTabQuests();
        if (selected >= list.size()) selected = -1;
    }

    private List<QuestEntryDisplayData> currentTabQuests() {
        List<QuestEntryDisplayData> result = new ArrayList<>();
        for (QuestEntryDisplayData q : state.quests()) {
            boolean isDaily = q.type() == zcylas.totality.api.quest.QuestType.DAILY;
            switch (tab) {
                case ACTIVE -> { if (!q.completed()) result.add(q); }
                case DAILY -> { if (isDaily && !q.completed()) result.add(q); }
                // Daily quests never show in Completed — they reset, a permanent
                // "completed" entry for them wouldn't mean anything (explicit user requirement).
                case COMPLETED -> { if (q.completed() && !isDaily) result.add(q); }
            }
        }
        return result;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, COLOR_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        int px = width / 14, py = height / 10;
        int pw = width - px * 2, ph = height - py * 2;

        g.fill(px, py, px + pw, py + ph, COLOR_PANEL_BG);
        drawFrame(g, px, py, pw, ph, 2, COLOR_COPPER);

        drawHeader(g, px, py, pw, mx, my);

        int tabY = py + HEADER_H;
        drawTabs(g, px, tabY, pw, mx, my);

        int contentY = tabY + TAB_H + PAD;
        int contentH = py + ph - BOTTOM_H - PAD - contentY;
        int listW = (int) (pw * 0.36f);

        List<QuestEntryDisplayData> quests = currentTabQuests();
        drawList(g, px + PAD, contentY, listW - PAD, contentH, quests, mx, my);
        drawDetail(g, px + listW + PAD, contentY, pw - listW - PAD * 2, contentH, quests);

        drawBottomBar(g, px, py + ph - BOTTOM_H, pw, BOTTOM_H, quests, mx, my);
    }

    private void drawHeader(GuiGraphicsExtractor g, int px, int py, int pw, int mx, int my) {
        String back = "< BACK";
        int[] b = backBounds(px, py);
        boolean hovered = inB(mx, my, b[0], b[1], b[2], b[3]);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hovered ? COLOR_CELL_HOV : COLOR_CELL_BG);
        drawFrame(g, b[0], b[1], b[2], b[3], 1, hovered ? COLOR_ACCENT : COLOR_COPPER_DIM);
        g.text(font, Component.literal(back), b[0] + 4, b[1] + (b[3] - 8) / 2, hovered ? COLOR_ACCENT : COLOR_LABEL, false);

        String title = "QUESTS";
        g.text(font, Component.literal(title), px + pw / 2 - font.width(title) / 2, py + 4, COLOR_ACCENT, true);
        g.fill(px + PAD, py + HEADER_H - 1, px + pw - PAD, py + HEADER_H, COLOR_COPPER_DIM);
    }

    private int[] backBounds(int px, int py) {
        return new int[]{ px + PAD, py + 4, 44, 14 };
    }

    private void drawTabs(GuiGraphicsExtractor g, int px, int tabY, int pw, int mx, int my) {
        Tab[] tabs = Tab.values();
        int tabW = (pw - PAD * 2) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = px + PAD + i * tabW;
            boolean active = tabs[i] == tab;
            boolean hovered = inB(mx, my, tx, tabY, tabW, TAB_H);
            int bg = active ? 0xFF14283A : (hovered ? COLOR_CELL_HOV : COLOR_CELL_BG);
            int border = active ? COLOR_ACCENT : COLOR_COPPER_DIM;
            g.fill(tx, tabY, tx + tabW - 2, tabY + TAB_H, bg);
            drawFrame(g, tx, tabY, tabW - 2, TAB_H, 1, border);
            String label = tabs[i].name();
            int c = active ? COLOR_ACCENT : COLOR_LABEL;
            g.text(font, Component.literal(label), tx + (tabW - 2) / 2 - font.width(label) / 2, tabY + (TAB_H - 8) / 2, c, false);
        }
    }

    private void drawList(GuiGraphicsExtractor g, int x, int y, int w, int h, List<QuestEntryDisplayData> quests, int mx, int my) {
        if (quests.isEmpty()) {
            String hint = "No quests here.";
            g.text(font, Component.literal(hint), x + w / 2 - font.width(hint) / 2, y + h / 2 - 4, COLOR_DIM, false);
            return;
        }
        int rowH = 34, gap = 3;
        for (int i = 0; i < quests.size(); i++) {
            int ry = y + i * (rowH + gap);
            if (ry + rowH > y + h) break;
            drawListRow(g, quests.get(i), i, x, ry, w, rowH, mx, my);
        }
    }

    private void drawListRow(GuiGraphicsExtractor g, QuestEntryDisplayData q, int index,
                              int x, int y, int w, int h, int mx, int my) {
        boolean isSelected = index == selected;
        boolean hovered = inB(mx, my, x, y, w, h);
        int bg = isSelected ? 0xFF14283A : (hovered ? COLOR_CELL_HOV : COLOR_CELL_BG);
        int border = isSelected ? COLOR_ACCENT : COLOR_COPPER_DIM;
        g.fill(x, y, x + w, y + h, bg);
        drawFrame(g, x, y, w, h, isSelected ? 2 : 1, border);

        g.text(font, Component.literal(q.name()), x + 6, y + 6, isSelected ? COLOR_ACCENT : COLOR_LABEL, false);
        String sub = q.completed() ? "Completed" : (q.ready() ? "Ready to turn in!" : q.type().label());
        g.text(font, Component.literal(sub), x + 6, y + h - 12, q.ready() ? COLOR_GREEN : COLOR_DIM, false);

        if (q.tracked()) {
            String mark = "◆"; // diamond, matches mockup's tracked indicator
            g.text(font, Component.literal(mark), x + w - 12, y + h / 2 - 4, COLOR_ACCENT, false);
        }
    }

    private void drawDetail(GuiGraphicsExtractor g, int x, int y, int w, int h, List<QuestEntryDisplayData> quests) {
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_COPPER_DIM);
        detailBoxX = x; detailBoxY = y; detailBoxW = w; detailBoxH = h;

        if (selected < 0 || selected >= quests.size()) {
            String hint = "Select a quest";
            g.text(font, Component.literal(hint), x + w / 2 - font.width(hint) / 2, y + h / 2 - 4, COLOR_DIM, false);
            detailContentHeight = 0;
            return;
        }

        QuestEntryDisplayData q = quests.get(selected);

        // Clip a few px in from the box's top/bottom edges (not just at scroll extremes) so
        // scrolled content is always cut off with a margin before it — never rendered flush
        // against the border line itself.
        try (var scissor = new zcylas.totality.api.client.util.CloseableScissor(
                g, x, y + DETAIL_INSET, w, h - DETAIL_INSET * 2)) {
            // cy is the unscrolled content-relative offset; every draw call subtracts
            // detailScroll so the whole block can be scrolled with the mouse wheel.
            int cy = PAD;

            g.text(font, Component.literal(q.name()), x + PAD, y + cy - detailScroll, COLOR_ACCENT, true);
            cy += 12;

            String badge = q.type().label().toUpperCase();
            int by = y + cy - detailScroll;
            g.fill(x + PAD, by, x + PAD + font.width(badge) + 8, by + 10, 0xFF2A1F0A);
            drawFrame(g, x + PAD, by, font.width(badge) + 8, 10, 1, COLOR_COPPER_DIM);
            g.text(font, Component.literal(badge), x + PAD + 4, by + 1, COLOR_COPPER_BRIGHT, false);
            int badgeW = font.width(badge) + 8;

            if (q.tracked()) {
                String tracked = "TRACKED";
                int tx = x + PAD + badgeW + 4;
                g.fill(tx, by, tx + font.width(tracked) + 8, by + 10, 0xFF0A2A3A);
                drawFrame(g, tx, by, font.width(tracked) + 8, 10, 1, COLOR_ACCENT);
                g.text(font, Component.literal(tracked), tx + 4, by + 1, COLOR_ACCENT, false);
            }
            cy += 16;

            g.fill(x + PAD, y + cy - detailScroll, x + w - PAD, y + cy - detailScroll + 1, COLOR_COPPER_DIM);
            cy += 6;

            for (String line : wrap(q.description(), w - PAD * 2)) {
                g.text(font, Component.literal(line), x + PAD, y + cy - detailScroll, COLOR_LABEL, false);
                cy += 10;
            }
            cy += 4;

            String objHdr = "OBJECTIVES";
            g.text(font, Component.literal(objHdr), x + PAD, y + cy - detailScroll, COLOR_ACCENT, false);
            cy += 10;

            for (int i = 0; i < q.objectives().size(); i++) {
                boolean done = i < q.objectivesDone().length && q.objectivesDone()[i];
                int boxSize = 9;
                int oy = y + cy - detailScroll;
                g.fill(x + PAD, oy, x + PAD + boxSize, oy + boxSize, done ? 0xFF0A2A3A : 0xFF141414);
                drawFrame(g, x + PAD, oy, boxSize, boxSize, 1, done ? COLOR_ACCENT : COLOR_DIM);
                if (done) g.text(font, Component.literal("✓"), x + PAD + 1, oy, COLOR_ACCENT, false);

                boolean first = true;
                for (String line : wrap(q.objectives().get(i), w - PAD * 2 - boxSize - 4)) {
                    int ly = y + cy - detailScroll;
                    g.text(font, Component.literal(line), x + PAD + boxSize + 4, ly, done ? COLOR_DIM : COLOR_LABEL, false);
                    cy += 10;
                    first = false;
                }
                if (first) cy += 10; // objective text was empty — still reserve a line
                cy += 2;
            }
            cy += 4;

            if (q.ready()) {
                String readyMsg = "★ All objectives complete — turn in below!";
                g.text(font, Component.literal(readyMsg), x + PAD, y + cy - detailScroll, COLOR_GREEN, false);
                cy += 12;
            }

            g.fill(x + PAD, y + cy - detailScroll, x + w - PAD, y + cy - detailScroll + 1, COLOR_COPPER_DIM);
            cy += 6;

            String rewHdr = "REWARDS";
            g.text(font, Component.literal(rewHdr), x + PAD, y + cy - detailScroll, COLOR_ACCENT, false);
            cy += 10;

            int rx = x + PAD;
            int ry = y + cy - detailScroll;
            if (q.rewardCredits() > 0) {
                String r = "₵ " + q.rewardCredits();
                g.text(font, Component.literal(r), rx, ry, COLOR_ACCENT, false);
                rx += font.width(r) + 12;
            }
            if (q.rewardXp() > 0) {
                String r = q.rewardXp() + " XP";
                g.text(font, Component.literal(r), rx, ry, COLOR_GREEN, false);
            }
            cy += 10;

            detailContentHeight = cy;
            int visibleH = h - DETAIL_INSET * 2;
            detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, detailContentHeight - visibleH)));
        }

        drawScrollArrows(g, x, y, w, h);
    }

    /** Small up/down indicators drawn on top of the (already-clipped) detail box so the
     *  player knows more content exists above/below the current scroll position — added
     *  because a hard content cutoff with no affordance read as a bug, not "scroll for more". */
    private void drawScrollArrows(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int maxScroll = Math.max(0, detailContentHeight - (h - DETAIL_INSET * 2));
        if (maxScroll <= 0) return;

        if (detailScroll > 0) {
            String up = "▲";
            g.text(font, Component.literal(up), x + w - PAD - font.width(up), y + 2, COLOR_COPPER_BRIGHT, true);
        }
        if (detailScroll < maxScroll) {
            String down = "▼";
            g.text(font, Component.literal(down), x + w - PAD - font.width(down), y + h - 10, COLOR_COPPER_BRIGHT, true);
        }
    }

    private void drawBottomBar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                List<QuestEntryDisplayData> quests, int mx, int my) {
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        g.fill(x, y, x + w, y + 1, COLOR_COPPER_DIM);

        boolean hasSelection = selected >= 0 && selected < quests.size();
        QuestEntryDisplayData q = hasSelection ? quests.get(selected) : null;
        boolean canTrack = hasSelection && !q.completed();
        boolean canAbandon = hasSelection && !q.completed() && q.type() != zcylas.totality.api.quest.QuestType.STARTER;
        boolean canFinish = hasSelection && q.ready();

        int btnW = 90, btnH = h - 6, gap = 4, closeW = 100;
        int trackX = x + PAD;
        int abandonX = trackX + btnW + gap;
        int closeX = x + w - PAD - closeW;
        int finishX = abandonX + btnW + gap;
        // Sized to exactly fill the gap between ABANDON and CLOSE so all four buttons end up
        // the same distance apart, rather than a fixed width that crowds CLOSE on wide panels.
        int finishW = Math.max(70, closeX - gap - finishX);

        drawBottomButton(g, canTrack && q.tracked() ? "◆ TRACKED" : "◆ TRACK", trackX, y + 3, btnW, btnH,
                canTrack, COLOR_ACCENT, mx, my);
        drawBottomButton(g, "⊘ ABANDON", abandonX, y + 3, btnW, btnH, canAbandon, 0xFFCC4444, mx, my);
        if (canFinish) {
            drawBottomButton(g, "✓ FINISH QUEST", finishX, y + 3, finishW, btnH, true, COLOR_GREEN, mx, my);
        }

        drawBottomButton(g, "CLOSE >", closeX, y + 3, closeW, btnH, true, COLOR_ACCENT, mx, my);
    }

    private void drawBottomButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                                   boolean enabled, int color, int mx, int my) {
        boolean hovered = enabled && inB(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hovered ? 0xFF1A1A1A : 0xFF0A0A0A);
        drawFrame(g, x, y, w, h, 1, enabled ? (hovered ? color : COLOR_COPPER_DIM) : 0xFF333333);
        g.text(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2,
                enabled ? (hovered ? color : COLOR_LABEL) : COLOR_DIM, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        int px = width / 14, py = height / 10;
        int pw = width - px * 2, ph = height - py * 2;

        int[] b = backBounds(px, py);
        if (inB(mx, my, b[0], b[1], b[2], b[3])) {
            click();
            Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(frame));
            return true;
        }

        int tabY = py + HEADER_H;
        Tab[] tabs = Tab.values();
        int tabW = (pw - PAD * 2) / tabs.length;
        if (inB(mx, my, px + PAD, tabY, pw - PAD * 2, TAB_H)) {
            int idx = (mx - px - PAD) / tabW;
            if (idx >= 0 && idx < tabs.length) {
                click();
                tab = tabs[idx];
                selected = -1;
                detailScroll = 0;
                return true;
            }
        }

        int contentY = tabY + TAB_H + PAD;
        int contentH = py + ph - BOTTOM_H - PAD - contentY;
        int listW = (int) (pw * 0.36f);
        int listX = px + PAD;
        List<QuestEntryDisplayData> quests = currentTabQuests();

        int rowH = 34, gap = 3;
        for (int i = 0; i < quests.size(); i++) {
            int ry = contentY + i * (rowH + gap);
            if (ry + rowH > contentY + contentH) break;
            if (inB(mx, my, listX, ry, listW - PAD, rowH)) {
                click();
                selected = i;
                detailScroll = 0;
                return true;
            }
        }

        int barY = py + ph - BOTTOM_H;
        boolean hasSelection = selected >= 0 && selected < quests.size();
        QuestEntryDisplayData q = hasSelection ? quests.get(selected) : null;
        boolean canTrack = hasSelection && !q.completed();
        boolean canFinish = hasSelection && q.ready();

        int btnW = 90, btnH = BOTTOM_H - 6, btnGap = 4, closeW = 100;
        int trackX = px + PAD;
        int abandonX = trackX + btnW + btnGap;
        int closeX = px + pw - PAD - closeW;
        int finishX = abandonX + btnW + btnGap;
        int finishW = Math.max(70, closeX - btnGap - finishX);

        if (canTrack && inB(mx, my, trackX, barY + 3, btnW, btnH)) {
            click();
            ClientPlayNetworking.send(new TrackQuestPayload(q.id(), !q.tracked()));
            return true;
        }
        if (canFinish && inB(mx, my, finishX, barY + 3, finishW, btnH)) {
            click();
            ClientPlayNetworking.send(new zcylas.totality.networking.quest.FinishQuestPayload(q.id()));
            return true;
        }
        if (inB(mx, my, closeX, barY + 3, closeW, btnH)) {
            click();
            Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(frame));
            return true;
        }
        // ABANDON: no quest currently qualifies (First Signal is STARTER) — intentionally
        // a visible-but-inert button for now, matching the mockup's greyed state exactly.
        if (inB(mx, my, abandonX, barY + 3, btnW, btnH)) { return true; }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (inB((int) mx, (int) my, detailBoxX, detailBoxY, detailBoxW, detailBoxH)) {
            detailScroll = Math.max(0, Math.min(
                    detailScroll - (int) (v * 10),
                    Math.max(0, detailContentHeight - (detailBoxH - DETAIL_INSET * 2))));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_TAB) {
            long window = Minecraft.getInstance().getWindow().handle();
            boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            Tab[] tabs = Tab.values();
            int idx = tab.ordinal();
            idx = (idx + (shift ? -1 : 1) + tabs.length) % tabs.length;
            tab = tabs[idx];
            selected = -1;
            click();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(frame));
            return true;
        }
        return super.keyPressed(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y, x + t, y + h, color);
        g.fill(x + w - t, y, x + w, y + h, color);
    }

    private boolean inB(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private List<String> wrap(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(test) > maxW) {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else cur = new StringBuilder(test);
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
