package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-3 external-review correction: {@link ClientResourceParityReport}'s header must be
 * structurally bounded, not an arbitrary caller-supplied {@code String} (first correction pass), and
 * the complete report must accept <b>exactly</b> {@link ClientResourceParityReport#RESOURCE_LINE_COUNT}
 * lines, never fewer, never more (second correction pass, Blocker 1) — a bounded header and bounded
 * individual lines do not by themselves bound the whole report if the line list itself can be any
 * size, including zero.
 *
 * <p>The prior version of this test file asserted that an empty {@code lines} list was valid
 * (<code>emptyLinesStillProducesExactlyOneHeaderLine</code>) — that assertion pinned down exactly the
 * defect this correction fixes and has been removed, not merely weakened; every test below now
 * constructs a full seven-line report or explicitly proves an invalid size is rejected.
 */
class ClientResourceParityReportTest {

    private static List<ClientResourceParityReportLine> sevenLines() {
        List<ClientResourceParityReportLine> lines = new ArrayList<>();
        for (int i = 0; i < ClientResourceParityReport.RESOURCE_LINE_COUNT; i++) {
            lines.add(new ClientResourceParityReportLine("totality:resource" + i, "totality:resource" + i + " | line " + i));
        }
        return lines;
    }

    @Test
    void headerIsNotARecordComponentAtAll() {
        // The adversarial case the first correction pass flagged is a caller passing an unbounded/
        // arbitrary header string. The chosen fix removes that possibility structurally: there is no
        // constructor parameter — and therefore no record component — for a header at all.
        RecordComponent[] components = ClientResourceParityReport.class.getRecordComponents();
        assertEquals(1, components.length, "expected exactly one record component (lines)");
        assertEquals("lines", components[0].getName());
    }

    @Test
    void headerConstantIsFixedAndShort() {
        assertEquals("[Resource Parity]", ClientResourceParityReport.HEADER);
        assertTrue(ClientResourceParityReport.HEADER.length() <= 64, "header must stay short");
    }

    @Test
    void resourceLineCountConstantIsExactlySeven() {
        assertEquals(7, ClientResourceParityReport.RESOURCE_LINE_COUNT);
    }

    @Test
    void chatLineCountConstantIsHeaderPlusResourceLineCount() {
        assertEquals(ClientResourceParityReport.RESOURCE_LINE_COUNT + 1, ClientResourceParityReport.CHAT_LINE_COUNT);
        assertEquals(8, ClientResourceParityReport.CHAT_LINE_COUNT);
    }

    // ── 1: exactly seven lines are accepted ────────────────────────────────────────────────────

    @Test
    void exactlySevenLinesIsAccepted() {
        var report = new ClientResourceParityReport(sevenLines());
        assertEquals(7, report.lines().size());
    }

    // ── 2: chatLines() returns exactly eight entries ───────────────────────────────────────────

    @Test
    void chatLinesAlwaysBeginsWithTheFixedHeaderRegardlessOfLineContentAndReturnsExactlyEight() {
        var report = new ClientResourceParityReport(sevenLines());
        assertEquals(ClientResourceParityReport.CHAT_LINE_COUNT, report.chatLines().size());
        assertEquals(ClientResourceParityReport.HEADER, report.chatLines().get(0));
    }

    // ── 3-6: invalid sizes are rejected deterministically, never silently truncated ───────────

    @Test
    void zeroLinesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReport(List.of()));
    }

    @Test
    void oneLineIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReport(
                List.of(new ClientResourceParityReportLine("totality:health", "x"))));
    }

    @Test
    void sixLinesIsRejected() {
        List<ClientResourceParityReportLine> six = new ArrayList<>(sevenLines());
        six.remove(six.size() - 1);
        assertEquals(6, six.size());
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReport(six));
    }

    @Test
    void eightLinesIsRejected() {
        List<ClientResourceParityReportLine> eight = new ArrayList<>(sevenLines());
        eight.add(new ClientResourceParityReportLine("totality:extra", "x"));
        assertEquals(8, eight.size());
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReport(eight));
    }

    // ── 7: a very large line list is rejected ──────────────────────────────────────────────────

    @Test
    void veryLargeLineListIsRejected() {
        List<ClientResourceParityReportLine> huge = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            huge.add(new ClientResourceParityReportLine("totality:r" + i, "x"));
        }
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReport(huge));
    }

    // ── 13: the total rendered character bound is provable from the fixed pieces ──────────────

    @Test
    void totalRenderedCharacterBoundIsProvableFromFixedHeaderAndSevenCappedLines() {
        var report = new ClientResourceParityReport(sevenLines());
        int maxPossibleTotal = ClientResourceParityReport.HEADER.length()
                + ClientResourceParityReport.RESOURCE_LINE_COUNT * ClientResourceParityReportLine.MAX_TEXT_LENGTH;

        int actualTotal = report.chatLines().stream().mapToInt(String::length).sum();

        assertTrue(actualTotal <= maxPossibleTotal,
                "actual rendered total (" + actualTotal + ") must never exceed the provable static maximum ("
                        + maxPossibleTotal + ")");
    }

    // ── 14: the list remains defensively copied and immutable ─────────────────────────────────

    @Test
    void linesListIsDefensivelyCopiedAndImmutable() {
        List<ClientResourceParityReportLine> mutable = new ArrayList<>(sevenLines());
        var report = new ClientResourceParityReport(mutable);

        mutable.clear();

        assertEquals(7, report.lines().size(), "the report must not observe later mutation of the caller's list");
        assertThrows(UnsupportedOperationException.class,
                () -> report.lines().add(new ClientResourceParityReportLine("totality:extra", "x")));
    }
}
