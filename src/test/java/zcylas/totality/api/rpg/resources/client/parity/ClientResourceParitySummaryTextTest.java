package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-2C: {@link ClientResourceParitySummaryText} is the pure, bounded formatter every
 * diagnostic message renders its generic/legacy content through. Covers the task's required-coverage
 * items 17-20 (deterministic scalar/partitioned content, an unbounded/adversarial partition input
 * cannot escape the bound, and formatting never mutates its input).
 */
class ClientResourceParitySummaryTextTest {

    // ── 17: bounded scalar diagnostic content is deterministic ────────────────────────────────

    @Test
    void scalarFormatIsExactAndDeterministic() {
        var summary = new ClientResourceParitySummary.Scalar(40, 100, 0, 1);
        assertEquals("40/100", ClientResourceParitySummaryText.format(summary));
        assertEquals(ClientResourceParitySummaryText.format(summary), ClientResourceParitySummaryText.format(summary));
    }

    @Test
    void scalarFormatIncludesNonZeroOverflow() {
        var summary = new ClientResourceParitySummary.Scalar(40, 100, 5, 1);
        assertEquals("40/100+overflow5", ClientResourceParitySummaryText.format(summary));
    }

    @Test
    void scalarFormatIncludesNonCanonicalScale() {
        var summary = new ClientResourceParitySummary.Scalar(40, 100, 0, 1000);
        assertEquals("40/100@scale1000", ClientResourceParitySummaryText.format(summary));
    }

    @Test
    void unavailableFormatIncludesReason() {
        var summary = new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET);
        assertEquals("unavailable:NOT_SYNCHRONIZED_YET", ClientResourceParitySummaryText.format(summary));
    }

    // ── 18: bounded partitioned diagnostic content is deterministic ───────────────────────────

    @Test
    void partitionedFormatIsAscendingRegardlessOfInputOrder() {
        var summary = ClientResourceParitySummary.Partitioned.of(List.of(
                new ClientResourceParitySummary.Partitioned.Partition(3, 1, 2, 0),
                new ClientResourceParitySummary.Partitioned.Partition(1, 5, 5, 0),
                new ClientResourceParitySummary.Partitioned.Partition(2, 0, 1, 0)
        ), 1);
        assertEquals("1:5/5,2:0/1,3:1/2", ClientResourceParitySummaryText.format(summary));
    }

    @Test
    void partitionedFormatIsDeterministicAcrossRepeatedCalls() {
        var summary = ClientResourceParitySummary.Partitioned.of(List.of(
                new ClientResourceParitySummary.Partitioned.Partition(1, 2, 4, 0)
        ), 1);
        assertEquals(ClientResourceParitySummaryText.format(summary), ClientResourceParitySummaryText.format(summary));
    }

    // ── 19: large/adversarial partition input cannot create unbounded output ──────────────────

    @Test
    void largePartitionInputProducesBoundedOutput() {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions = new ArrayList<>();
        for (int i = 1; i <= 5000; i++) {
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(i, i, i, 0));
        }
        var summary = ClientResourceParitySummary.Partitioned.of(partitions, 1);

        String text = ClientResourceParitySummaryText.format(summary);

        // Only MAX_PARTITIONS_IN_TEXT entries are ever rendered, regardless of how many partitions
        // the input actually contains — proves the cap, not merely that this one input happens to
        // produce a short string.
        long renderedEntrySeparators = text.chars().filter(c -> c == ',').count();
        assertTrue(renderedEntrySeparators <= ClientResourceParitySummaryText.MAX_PARTITIONS_IN_TEXT,
                "rendered entry count must never exceed the cap, got separator count " + renderedEntrySeparators);
        assertTrue(text.contains("+" + (5000 - ClientResourceParitySummaryText.MAX_PARTITIONS_IN_TEXT) + " more"),
                "expected a bounded '+N more' suffix naming exactly how many partitions were omitted");
        assertTrue(text.length() < 500, "formatted text must stay short even for 5000 input partitions");
    }

    @Test
    void partitionCountAtExactlyTheCapProducesNoMoreSuffix() {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions = new ArrayList<>();
        for (int i = 1; i <= ClientResourceParitySummaryText.MAX_PARTITIONS_IN_TEXT; i++) {
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(i, 0, 0, 0));
        }
        var summary = ClientResourceParitySummary.Partitioned.of(partitions, 1);
        assertFalse(ClientResourceParitySummaryText.format(summary).contains("more"));
    }

    // ── 20: formatting never mutates the input summary ────────────────────────────────────────

    @Test
    void formattingDoesNotMutatePartitionedInput() {
        var summary = ClientResourceParitySummary.Partitioned.of(List.of(
                new ClientResourceParitySummary.Partitioned.Partition(1, 3, 10, 0),
                new ClientResourceParitySummary.Partitioned.Partition(2, 7, 10, 0)
        ), 1);

        String before = summary.toString();
        ClientResourceParitySummaryText.format(summary);
        String after = summary.toString();

        assertEquals(before, after, "formatting a Partitioned summary must never mutate it");
        assertEquals(2, summary.partitions().size());
    }
}
