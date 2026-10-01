package zcylas.totality.screen.phone;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1 VISUAL PROTOTYPE of the phone's notification shade. It is deliberately not connected to Notification
 * API V2 (V3 is being planned): it shows whatever list it is given — development test data from
 * {@link PhonePrototype}, or an empty list in normal play — and dismissing only removes entries from that list.
 *
 * <p>Layout: the shade slides down from under the status bar and covers the rest of the display; notifications
 * are listed newest first as compact cards (icon, app, relative time, dismiss "x", one message line, expand
 * chevron) or expanded cards (full message, details, centred Mark as Read); "x Clear All" sits fixed at the bottom
 * centre; an empty shade shows only "No notifications".
 *
 * <p>Interaction scaffolding (driven by {@link PhoneAppGridScreen}'s gesture routing): pull down from the status
 * bar to open, drag up past the end of the list to close, vertical drag and the mouse wheel scroll, swipe a card
 * right to expand or left to dismiss, click the chevron or an expanded card's header to collapse.
 */
public final class PhoneNotificationShade {

    /** Highest-urgency first in {@link #highest}; the status bar and cards use the same colours and glyphs. */
    public enum Severity { NORMAL, IMPORTANT, CRITICAL }

    /**
     * One notification. Its VISUAL TYPE ({@code typeColor}: icon tile, abbreviation and source name) is independent of
     * its URGENCY ({@code severity}: the left stripe and the glyph by the time), so any type can carry any urgency.
     * {@code icon} (optional) replaces the provisional abbreviation, as on the home screen.
     */
    public record Entry(String id, String app, String abbreviation, Identifier icon, int typeColor, Severity severity,
                        String message, List<String> details, long timeMillis) {}

    private static final long ANIM_NANOS = 180_000_000L;

    private final List<Entry> entries;
    private final Set<String> expanded = new HashSet<>();
    private float animFrom, animTo;
    private long animStart = -1;
    /** While a finger-style drag holds the shade, its open amount follows the drag instead of animating. */
    private float held = -1;
    private float scroll;
    private int maxScroll;
    private String swiping;
    private float swipeDx;

    /** Hit boxes from the last draw (x, y, w, h), used by the gesture code between frames. */
    /** {@code dismiss} and {@code chevron} are the controls' own hit boxes {x, y, w, h}, slightly larger than the glyphs. */
    private record Card(Entry entry, int x, int y, int w, int h, int headerH, int[] dismiss, int[] chevron) {}

    private final List<Card> cards = new ArrayList<>();
    private int[] clearAll;
    private int[] listArea;
    private int panelH = 1;

    public PhoneNotificationShade(List<Entry> entries) {
        this.entries = entries;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /** Entries newest first. */
    public List<Entry> ordered() {
        List<Entry> list = new ArrayList<>(entries);
        list.sort(Comparator.comparingLong(Entry::timeMillis).reversed());
        return list;
    }

    public int count() {
        return entries.size();
    }

    /** The highest unread urgency, or null when there is nothing unread. */
    public Severity highest() {
        Severity best = null;
        for (Entry e : entries) if (best == null || e.severity().compareTo(best) > 0) best = e.severity();
        return best;
    }

    /** Compact relative time: "now", "5m ago", "3h ago", "Yesterday", "4d ago". */
    public static String relativeTime(long ageMillis) {
        long minutes = Math.max(0, ageMillis) / 60_000L;
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        if (hours < 48) return "Yesterday";
        return hours / 24 + "d ago";
    }

    public void dismiss(Entry e) {
        entries.remove(e);
        expanded.remove(e.id());
    }

    /** Clear All: marks every displayed notification read and dismisses it. */
    public void clearAll() {
        entries.clear();
        expanded.clear();
        scroll = 0;
    }

    public boolean isExpanded(Entry e) {
        return expanded.contains(e.id());
    }

    public void setExpanded(Entry e, boolean value) {
        if (value) expanded.add(e.id());
        else expanded.remove(e.id());
    }

    // ── Open state ────────────────────────────────────────────────────────────

    /** 0 = closed, 1 = fully open. */
    public float openAmount() {
        if (held >= 0) return held;
        if (animStart < 0) return animTo;
        float t = Math.min(1f, (System.nanoTime() - animStart) / (float) ANIM_NANOS);
        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        return animFrom + (animTo - animFrom) * eased;
    }

    public boolean isOpen() {
        return openAmount() > 0f || animTo > 0f;
    }

    public void open() {
        animateTo(1f);
    }

    public void close() {
        animateTo(0f);
    }

    /** Opens or closes immediately, without the slide (for tests and the capture run). */
    public void snap(boolean open) {
        held = -1;
        animStart = -1;
        animFrom = animTo = open ? 1f : 0f;
    }

    private void animateTo(float target) {
        animFrom = openAmount();
        animTo = target;
        held = -1;
        animStart = System.nanoTime();
    }

    /** A pull from the status bar: the shade follows the pointer ({@code dy} GUI pixels down from the press). */
    public void pull(double dy) {
        held = (float) Math.max(0, Math.min(1, dy / panelH));
    }

    /** Ends a pull: opens when pulled at least a third of the way, otherwise falls back closed. */
    public void releasePull() {
        if (held < 0) return;
        float at = held;
        held = -1;
        animFrom = at;
        animStart = -1;
        animTo = at;
        animateTo(at >= 0.33f ? 1f : 0f);
    }

    // ── Scrolling and swipes ──────────────────────────────────────────────────

    public void scrollBy(double gui) {
        scroll = (float) Math.max(0, Math.min(maxScroll, scroll + gui));
    }

    /**
     * Vertical drag on the open shade ({@code dy} since the last event, GUI px). Dragging up scrolls the list;
     * once the list cannot scroll further, continuing upward pulls the shade closed.
     */
    public void dragVertical(double dy) {
        if (held >= 0) {
            held = (float) Math.max(0, Math.min(1, held + dy / panelH));
            return;
        }
        double before = scroll;
        scrollBy(-dy);
        double overflow = -dy - (scroll - before);
        if (dy < 0 && overflow > 0 && scroll >= maxScroll) held = (float) Math.max(0, 1 - overflow / panelH);
    }

    /** Ends a vertical drag: a shade dragged a third of the way up closes, otherwise it springs back open. */
    public void endVertical() {
        if (held < 0) return;
        float at = held;
        held = -1;
        animTo = at;
        animateTo(at <= 0.67f ? 0f : 1f);
    }

    public Entry cardAt(double mx, double my) {
        if (listArea == null || my < listArea[1] || my >= listArea[1] + listArea[3]) return null;
        for (Card c : cards) {
            if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h()) return c.entry();
        }
        return null;
    }

    public void beginSwipe(Entry e) {
        swiping = e.id();
        swipeDx = 0;
    }

    public void swipe(double dx) {
        swipeDx = (float) dx;
    }

    /** Ends a swipe: right past a quarter of the card expands it, left past a quarter dismisses it. */
    public void endSwipe() {
        Card card = null;
        for (Card c : cards) if (c.entry().id().equals(swiping)) card = c;
        if (card != null) {
            if (swipeDx > card.w() / 4f) setExpanded(card.entry(), true);
            else if (swipeDx < -card.w() / 4f) dismiss(card.entry());
        }
        swiping = null;
        swipeDx = 0;
    }

    /**
     * A click (press and release without a drag) on the open shade. Each control answers only inside its own box:
     * the "x" dismisses, the chevron or an expanded card's header collapses, Mark as Read dismisses, and a tap
     * anywhere else on a compact card expands it. The rest of an expanded card does nothing.
     */
    public void click(double mx, double my) {
        if (clearAll != null && in(clearAll, mx, my)) {
            clearAll();
            return;
        }
        if (listArea == null || !in(listArea, mx, my)) return;
        for (Card c : cards) {
            if (!in(new int[] {c.x(), c.y(), c.w(), c.h()}, mx, my)) continue;
            Entry e = c.entry();
            if (in(c.dismiss(), mx, my)) {
                dismiss(e);
            } else if (isExpanded(e)) {
                int[] button = markAsReadBounds(c);
                if (button != null && in(button, mx, my)) dismiss(e);
                else if (in(c.chevron(), mx, my) || my < c.y() + c.headerH()) setExpanded(e, false);
            } else {
                setExpanded(e, true);
            }
            return;
        }
    }

    /** The shade's interactive regions {x, y, w, h} from the last draw (development bounds overlay). */
    public List<int[]> interactiveBounds() {
        List<int[]> out = new ArrayList<>();
        if (clearAll != null) out.add(clearAll);
        if (listArea == null) return out;
        for (Card c : cards) {
            int top = Math.max(c.y(), listArea[1]), bottom = Math.min(c.y() + c.h(), listArea[1] + listArea[3]);
            if (bottom <= top) continue;
            if (isExpanded(c.entry())) {
                int headerBottom = Math.min(bottom, c.y() + c.headerH());
                if (headerBottom > top) out.add(new int[] {c.x(), top, c.w(), headerBottom - top});
                out.add(c.chevron());
                int[] button = markAsReadBounds(c);
                if (button != null) out.add(button);
            } else {
                out.add(new int[] {c.x(), top, c.w(), bottom - top});
            }
            out.add(c.dismiss());
        }
        return out;
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private static final String MARK_AS_READ = "Mark as Read";
    private static final String CLEAR_ALL = "Clear All";
    private static final String EMPTY = "No notifications";
    private static final int PAD = 3;
    /** Left inset of a card's content: the 2 px urgency stripe, then a 1 px gap. */
    private static final int LEFT = PAD + 2;
    private static final int GAP = 3;

    /** Mark as Read button bounds per expanded entry id, from the last draw. */
    private final Map<String, int[]> lastButtons = new HashMap<>();

    private int[] markAsReadBounds(Card c) {
        return isExpanded(c.entry()) ? lastButtons.get(c.entry().id()) : null;
    }

    /**
     * Draws the shade over the display below the status bar ({@code top} = status bar bottom). Call after the
     * home content and before the status bar.
     */
    public void draw(PhoneUi ui, PhoneFrameRenderer.Layout l, int top, int mx, int my) {
        GuiGraphicsExtractor g = ui.graphics();
        PhoneTheme t = ui.colors();
        int bottom = l.dy() + l.dh();
        panelH = bottom - top;
        float open = openAmount();
        cards.clear();
        lastButtons.clear();
        clearAll = null;
        listArea = null;
        if (open <= 0f) return;
        int py = top - Math.round((1 - open) * panelH);
        int x0 = l.dx(), w = l.dw();
        int lh = ui.lineHeight();

        g.enableScissor(x0, top, x0 + w, bottom);
        g.fill(x0, py, x0 + w, py + panelH, t.shade());
        g.fill(x0, py + panelH - 1, x0 + w, py + panelH, t.accent());

        List<Entry> list = ordered();
        if (list.isEmpty()) {
            ui.textCentered(EMPTY, x0 + w / 2, py + (panelH - lh) / 2, t.textDim());
            g.disableScissor();
            return;
        }

        int clearH = lh + 7;
        int clearW = PhoneUi.GLYPH_H + 3 + ui.width(CLEAR_ALL);
        int clearY = py + panelH - clearH - 1;
        int listTop = py + GAP, listBottom = clearY - 1;
        listArea = new int[] {x0, listTop, w, listBottom - listTop};

        // Cards, newest first.
        int cardX = x0 + 3, cardW = w - 8;
        int y = listTop - Math.round(scroll);
        int contentH = 0;
        g.enableScissor(x0, listTop, x0 + w, listBottom);
        for (Entry e : list) {
            int h = cardHeight(ui, e, cardW);
            int shift = e.id().equals(swiping) ? Math.round(swipeDx) : 0;
            if (y + h > listTop && y < listBottom) {
                drawCard(ui, e, cardX, y, cardW, h, shift, mx, my);
            }
            cards.add(card(ui, e, cardX, y, cardW, h));
            y += h + GAP;
            contentH += h + GAP;
        }
        g.disableScissor();
        maxScroll = Math.max(0, contentH - GAP - (listBottom - listTop));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            // A thin scroll indicator along the right edge.
            int trackH = listBottom - listTop;
            int thumbH = Math.max(6, trackH * trackH / (contentH - GAP));
            int thumbY = listTop + Math.round((trackH - thumbH) * (scroll / maxScroll));
            g.fill(x0 + w - 3, listTop, x0 + w - 2, listBottom, t.cardLine());
            g.fill(x0 + w - 3, thumbY, x0 + w - 2, thumbY + thumbH, t.textDim());
        }

        // "x Clear All", fixed at the bottom centre.
        int cx = x0 + (w - clearW) / 2;
        clearAll = new int[] {cx - 3, clearY, clearW + 6, clearH};
        boolean hover = in(clearAll, mx, my);
        int c = hover ? t.accentBright() : t.textDim();
        ui.cross(cx, clearY + (clearH - 5) / 2, c);
        ui.text(CLEAR_ALL, cx + PhoneUi.GLYPH_H + 3, clearY + (clearH - lh) / 2 + 1, c);
        g.disableScissor();
    }

    private static Card card(PhoneUi ui, Entry e, int x, int y, int w, int h) {
        int icon = iconSize(ui), iy = y + PAD, lh = ui.lineHeight();
        int crossX = x + w - PAD - 5;
        int by = iy + icon + 2;
        return new Card(e, x, y, w, h, PAD + icon + 1, new int[] {crossX - 2, iy - 1, 9, icon + 2},
                new int[] {x + w - PAD - 7, by - 1, 9, lh + 2});
    }

    private static int iconSize(PhoneUi ui) {
        return ui.lineHeight() + 4;
    }

    private int bodyWidth(PhoneUi ui, int cardW) {
        return cardW - LEFT - PAD - iconSize(ui) - 3 - 8;
    }

    /** Expanded text uses the card's full width (left of the chevron column). */
    private static int expandedWidth(int cardW) {
        return cardW - LEFT - PAD - 8;
    }

    private int cardHeight(PhoneUi ui, Entry e, int cardW) {
        int lh = ui.lineHeight();
        int h = PAD + iconSize(ui) + 2 + lh + PAD;
        if (isExpanded(e)) {
            int lines = wrap(ui, e.message(), expandedWidth(cardW)).size() - 1;
            for (String d : e.details()) lines += wrap(ui, d, expandedWidth(cardW)).size();
            h += lines * lh + (e.details().isEmpty() ? 0 : 3) + 4 + lh + 5;
        }
        return h;
    }

    private static List<String> wrap(PhoneUi ui, String text, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (ui.width(next) <= maxW || line.isEmpty()) {
                line = new StringBuilder(next);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void drawCard(PhoneUi ui, Entry e, int x, int y, int w, int h, int shift, int mx, int my) {
        GuiGraphicsExtractor g = ui.graphics();
        PhoneTheme t = ui.colors();
        int lh = ui.lineHeight();
        int sev = t.severity(e.severity());
        int type = e.typeColor();

        // Swipe hints revealed behind a dragged card: expand (right) and dismiss (left).
        if (shift > 0) {
            ui.roundRect(x, y, w, h, t.iconBackground());
            ui.chevronVertical(x + 4, y + (h - 3) / 2, true, t.accent());
        } else if (shift < 0) {
            ui.roundRect(x, y, w, h, mix(t.card(), t.critical(), 0.35f));
            ui.cross(x + w - 9, y + (h - 5) / 2, t.text());
        }
        x += shift;

        // Neutral card for every type and urgency; urgency = the left stripe (and the glyph by the time).
        ui.roundRect(x, y, w, h, t.card());
        ui.roundOutline(x, y, w, h, t.cardLine());
        g.fill(x + 1, y + 2, x + 3, y + h - 2, sev);

        // Header: type-coloured icon and source name, urgency glyph, relative time, dismiss control.
        int icon = iconSize(ui);
        int ix = x + LEFT, iy = y + PAD;
        ui.roundRect(ix, iy, icon, icon, mix(t.iconBackground(), type, 0.28f));
        if (e.icon() != null) ui.crispSprite(e.icon(), ix + icon / 2, iy + icon / 2, icon, 32);
        else ui.textCentered(e.abbreviation(), ix + icon / 2, iy + (icon - lh) / 2 + 1, type);
        int tx = ix + icon + 3;
        int ty = iy + (icon - lh) / 2 + 1;
        int crossX = x + w - PAD - 5;
        boolean crossHover = mx >= crossX - 2 && mx < crossX + 7 && my >= iy - 1 && my < iy + icon + 1;
        ui.cross(crossX, iy + (icon - 5) / 2, crossHover ? t.text() : t.textFaint());
        String time = relativeTime(System.currentTimeMillis() - e.timeMillis());
        int timeX = crossX - 4 - ui.width(time);
        ui.text(time, timeX, ty, t.textDim());
        int glyphX = timeX - 3 - PhoneUi.severityGlyphWidth(e.severity());
        ui.severityGlyph(glyphX, iy + (icon - PhoneUi.GLYPH_H) / 2, e.severity());
        ui.text(ui.ellipsize(e.app(), glyphX - 3 - tx), tx, ty, type);

        // Body: one line (compact) or everything (expanded), the chevron at the right of the first line.
        int bw = bodyWidth(ui, w);
        int by = iy + icon + 2;
        boolean open = isExpanded(e);
        ui.chevronVertical(x + w - PAD - 5, by + (lh - 3) / 2, !open, t.textDim());
        if (!open) {
            ui.text(ui.ellipsize(e.message(), bw), tx, by, t.textDim());
            return;
        }
        for (String line : wrap(ui, e.message(), expandedWidth(w))) {
            ui.text(line, ix, by, t.text());
            by += lh;
        }
        if (!e.details().isEmpty()) by += 3;
        for (String d : e.details()) {
            for (String line : wrap(ui, d, expandedWidth(w))) {
                ui.text(line, ix, by, t.textDim());
                by += lh;
            }
        }
        by += 4;
        int bwid = ui.width(MARK_AS_READ) + 12, bh = lh + 5;
        int bx = x + (w - bwid) / 2;
        boolean hover = mx >= bx && mx < bx + bwid && my >= by && my < by + bh;
        ui.roundRect(bx, by, bwid, bh, hover ? t.tileActive() : t.iconBackground());
        ui.roundOutline(bx, by, bwid, bh, t.accent());
        ui.textCentered(MARK_AS_READ, bx + bwid / 2, by + 3, hover ? t.accentBright() : t.accent());
        lastButtons.put(e.id(), new int[] {bx - shift, by, bwid, bh});
    }

    // ── Capture diagnostics (development) ─────────────────────────────────────

    /** Centre of an entry's card body (below its header) from the last draw, or null (for the dev capture run). */
    public double[] cardCentre(String id) {
        for (Card c : cards) {
            if (c.entry().id().equals(id)) return new double[] {c.x() + c.w() / 2.0, c.y() + c.headerH() + 3};
        }
        return null;
    }

    /** Centre of an expanded entry's Mark as Read button from the last draw, or null (for the dev capture run). */
    public double[] markAsReadCentre(String id) {
        int[] b = lastButtons.get(id);
        return b == null ? null : new double[] {b[0] + b[2] / 2.0, b[1] + b[3] / 2.0};
    }

    /** Centre of "x Clear All" from the last draw, or null (for the dev capture run). */
    public double[] clearAllCentre() {
        return clearAll == null ? null : new double[] {clearAll[0] + clearAll[2] / 2.0, clearAll[1] + clearAll[3] / 2.0};
    }

    /** Centre of the list area from the last draw, or null (for the dev capture run). */
    public double[] listCentre() {
        return listArea == null ? null : new double[] {listArea[0] + listArea[2] / 2.0, listArea[1] + listArea[3] / 2.0};
    }

    /** {@code a} blended toward {@code b} by {@code k} (opaque result). */
    static int mix(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int gr = Math.round(((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
}
