package zcylas.totality.api.rpg.resources.client.parity;

import java.util.Objects;

/**
 * One immutable, bounded, human-readable diagnostic line for a single Resource id — the Phase 3B-3
 * on-demand parity inspection command's per-resource output unit. Never a raw {@code toString()} of
 * any live object; {@link ClientResourceParityReportAssembler} is the only producer of this type's
 * {@code text}, and it is always built from already-bounded pieces ({@link
 * ClientResourceParitySummaryText#format}, enum names, primitive numbers).
 *
 * <p>{@code resourceId} is a plain {@link String}, not {@code net.minecraft.resources.Identifier} —
 * external-review correction: this type, like every other type in the pure report layer, must carry
 * no Minecraft/Fabric dependency at all. The impure client command boundary converts an
 * {@code Identifier} to its {@code toString()} form before any pure report type ever sees it.
 *
 * <p>Structurally cannot exceed {@link #MAX_TEXT_LENGTH} characters — the compact constructor
 * truncates and appends a fixed marker rather than trusting every call site to have bounded its own
 * input correctly. This is a second, independent bound layered on top of {@link
 * ClientResourceParitySummaryText#MAX_PARTITIONS_IN_TEXT}'s own cap, not a replacement for it.
 */
public record ClientResourceParityReportLine(String resourceId, String text) {

    /** Hard cap on one line's rendered length. Never raised merely to fit one resource's current
     *  shape — see the class Javadoc. */
    public static final int MAX_TEXT_LENGTH = 400;

    private static final String TRUNCATION_SUFFIX = "...(truncated)";

    public ClientResourceParityReportLine {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_TEXT_LENGTH) {
            int keep = MAX_TEXT_LENGTH - TRUNCATION_SUFFIX.length();
            text = text.substring(0, Math.max(0, keep)) + TRUNCATION_SUFFIX;
        }
    }
}
