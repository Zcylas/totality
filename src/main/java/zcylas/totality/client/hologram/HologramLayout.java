package zcylas.totality.client.hologram;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where everything sits on a hologram panel, in panel units with the origin at the top-left corner
 * (+x right, +y down). Pure arithmetic over the font's metrics, shared by drawing and by hit-testing
 * so a button is exactly where it is drawn.
 */
final class HologramLayout {

    static final float PAD = 16;
    static final float TAB_TOP = 11;
    static final float TAB_H = 18;
    static final float TAB_ICON = 12;
    static final float HEADER_SCALE = 0.75f;
    static final float HEADER_SPACING = 1.25f;
    static final float TITLE_SCALE = 1.35f;
    static final float BODY_LINE = 10.5f;
    static final float FOOT_SCALE = 0.8f;
    static final float BUTTON_H = 18;
    static final float BUTTON_LABEL_SCALE = 0.8f;
    static final float BUTTON_SPACING = 1.0f;
    static final float BUTTON_MIN_W = 84;
    static final float BUTTON_GAP = 10;
    static final float VOICE_SCALE = 0.7f;

    record Line(FormattedCharSequence text, float x, float y, float scale, float width) {}

    record Button(HologramAction action, float x0, float y0, float x1, float y1, String label, float labelWidth) {}

    final float width;
    final float height;
    final float tabX0, tabX1, tabY0, tabY1;
    final String headerText;
    final float headerWidth;
    final float dividerY;
    final List<Line> title = new ArrayList<>();
    final float titleWidth;
    final List<Line> body = new ArrayList<>();
    final List<Line> footnote = new ArrayList<>();
    final float progressY;
    final List<Button> buttons = new ArrayList<>();
    /** Baseline of the voice hint/feedback line, or -1 when the hologram takes no voice commands. */
    final float voiceY;

    HologramLayout(HologramSpec spec, Font font) {
        width = spec.size().width;
        float inner = width - 2 * PAD;

        headerText = spec.header().getString().toUpperCase(Locale.ROOT);
        headerWidth = spacedWidth(font, headerText, HEADER_SCALE, HEADER_SPACING);
        float tabW = 5 + TAB_ICON + 5 + headerWidth + 9;
        tabX0 = (width - tabW) / 2;
        tabX1 = tabX0 + tabW;
        tabY0 = TAB_TOP;
        tabY1 = TAB_TOP + TAB_H;
        dividerY = tabY1 + 7;

        float y = dividerY + 8;
        float titleMax = 0;
        for (FormattedCharSequence seq : font.split(spec.title(), (int) (inner / TITLE_SCALE))) {
            float w = font.width(seq) * TITLE_SCALE;
            titleMax = Math.max(titleMax, w);
            title.add(new Line(seq, (width - w) / 2, y, TITLE_SCALE, w));
            y += 9 * TITLE_SCALE + 1;
        }
        titleWidth = titleMax;

        if (!spec.body().isEmpty()) y += 5;
        for (Component paragraph : spec.body()) {
            for (FormattedCharSequence seq : font.split(paragraph, (int) inner)) {
                float w = font.width(seq);
                body.add(new Line(seq, (width - w) / 2, y, 1, w));
                y += BODY_LINE;
            }
        }

        if (spec.footnote() != null) {
            y += 4;
            for (FormattedCharSequence seq : font.split(spec.footnote(), (int) (inner / FOOT_SCALE))) {
                float w = font.width(seq) * FOOT_SCALE;
                footnote.add(new Line(seq, (width - w) / 2, y, FOOT_SCALE, w));
                y += 9 * FOOT_SCALE + 1;
            }
        }

        if (!HologramVoice.commands(spec).isEmpty()) {
            // Above the buttons: the push-to-talk meter of the Voice HUD sits just under the crosshair.
            y += 6;
            voiceY = y;
            y += 9 * VOICE_SCALE;
        } else {
            voiceY = -1;
        }

        if (spec.indeterminateProgress()) {
            y += 8;
            progressY = y;
            y += 3;
        } else {
            progressY = -1;
        }

        if (!spec.actions().isEmpty()) {
            y += 11;
            List<Button> row = new ArrayList<>();
            float total = 0;
            for (HologramAction action : spec.actions()) {
                String label = action.label().getString().toUpperCase(Locale.ROOT);
                float lw = spacedWidth(font, label, BUTTON_LABEL_SCALE, BUTTON_SPACING);
                float w = Math.max(BUTTON_MIN_W, lw + 28);
                row.add(new Button(action, 0, y, w, y + BUTTON_H, label, lw));
                total += w;
            }
            total += BUTTON_GAP * (row.size() - 1);
            float x = (width - total) / 2;
            for (Button b : row) {
                float w = b.x1() - b.x0();
                buttons.add(new Button(b.action(), x, b.y0(), x + w, b.y1(), b.label(), b.labelWidth()));
                x += w + BUTTON_GAP;
            }
            y += BUTTON_H;
        }
        // Generous bottom margin: at rest the crosshair sits here, below the buttons.
        height = y + (buttons.isEmpty() ? 14 : 20);
    }

    /** Width of {@code text} drawn one character at a time with extra spacing (letter-spaced caps). */
    static float spacedWidth(Font font, String text, float scale, float spacing) {
        float w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += font.width(new String(Character.toChars(cp))) * scale + spacing;
            i += Character.charCount(cp);
        }
        return text.isEmpty() ? 0 : w - spacing;
    }
}
