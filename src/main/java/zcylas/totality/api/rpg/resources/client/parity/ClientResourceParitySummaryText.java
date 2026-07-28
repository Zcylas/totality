package zcylas.totality.api.rpg.resources.client.parity;

import java.util.Map;

/**
 * Pure, bounded, deterministic text rendering of a {@link ClientResourceParitySummary} for Phase
 * 3B-2C's diagnostic logging — never a raw dump of any field, never unbounded regardless of input
 * shape. No Minecraft/Fabric/logging dependency; directly unit-testable.
 *
 * <p>{@link ClientResourceParitySummary.Scalar} always renders to a fixed, small number of tokens.
 * {@link ClientResourceParitySummary.Partitioned} is capped at {@link #MAX_PARTITIONS_IN_TEXT}
 * entries (already-ascending per {@link ClientResourceParitySummary.Partitioned}'s own compact
 * constructor) with a bounded "+N more" suffix for anything beyond the cap — the four production
 * parity resources never exceed this (standard spell slots use exactly 10 partitions), but this
 * bound holds regardless of how many partitions a future resource or an adversarial input supplies.
 */
public final class ClientResourceParitySummaryText {

    /** Hard cap on how many partition entries this formatter will ever render — see the class
     *  Javadoc. Never raised merely to fit one resource's current shape. */
    public static final int MAX_PARTITIONS_IN_TEXT = 16;

    public static String format(ClientResourceParitySummary summary) {
        if (summary instanceof ClientResourceParitySummary.Scalar scalar) {
            return formatScalar(scalar);
        }
        if (summary instanceof ClientResourceParitySummary.Partitioned partitioned) {
            return formatPartitioned(partitioned);
        }
        if (summary instanceof ClientResourceParitySummary.Unavailable unavailable) {
            return "unavailable:" + unavailable.reason();
        }
        // ClientResourceParitySummary is sealed to exactly these three permitted types — unreachable,
        // but defensive rather than an unchecked cast if a future permitted subtype is ever added.
        return "unknown-summary-shape";
    }

    private static String formatScalar(ClientResourceParitySummary.Scalar scalar) {
        StringBuilder text = new StringBuilder();
        text.append(scalar.currentUnits()).append('/').append(scalar.maximumUnits());
        if (scalar.overflowUnits() != 0) {
            text.append("+overflow").append(scalar.overflowUnits());
        }
        if (scalar.unitScale() != 1) {
            text.append("@scale").append(scalar.unitScale());
        }
        return text.toString();
    }

    private static String formatPartitioned(ClientResourceParitySummary.Partitioned partitioned) {
        StringBuilder text = new StringBuilder();
        int totalPartitions = partitioned.partitions().size();
        int rendered = 0;
        for (Map.Entry<Integer, ClientResourceParitySummary.Partitioned.Partition> entry : partitioned.partitions().entrySet()) {
            if (rendered >= MAX_PARTITIONS_IN_TEXT) {
                break;
            }
            if (rendered > 0) {
                text.append(',');
            }
            ClientResourceParitySummary.Partitioned.Partition partition = entry.getValue();
            text.append(entry.getKey()).append(':').append(partition.currentUnits()).append('/').append(partition.maximumUnits());
            rendered++;
        }
        if (totalPartitions > MAX_PARTITIONS_IN_TEXT) {
            text.append(",...(+").append(totalPartitions - MAX_PARTITIONS_IN_TEXT).append(" more)");
        }
        if (partitioned.unitScale() != 1) {
            text.append("@scale").append(partitioned.unitScale());
        }
        return text.toString();
    }

    private ClientResourceParitySummaryText() {}
}
