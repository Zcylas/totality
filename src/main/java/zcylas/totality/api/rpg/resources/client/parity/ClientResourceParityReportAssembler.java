package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, bounded, deterministic assembler for the Phase 3B-3 on-demand parity inspection report. No
 * Minecraft/Fabric/command/logging/legacy-manager/networking dependency of any kind — confirmed by
 * this package's own import-scan regression test. Takes an already-ordered, already-converted
 * {@link ClientResourceParityReportInput} list from the impure client command boundary
 * ({@code ClientResourceParityInspectionCommand}) and renders each entry to one
 * {@link ClientResourceParityReportLine}, preserving the caller's order exactly — this class holds no
 * canonical Resource-id list or ordering knowledge of its own; ordering is entirely the command
 * boundary's responsibility (it is the only code with access to {@code PlayerResourceIds}).
 *
 * <p><b>No second parity algorithm.</b> This class never compares a generic value against a legacy
 * value itself — {@link ClientResourceParityReportInput.ShadowParity.Status.Observed} already carries
 * the tracker's own final {@link ClientResourceParityClassification}, echoed verbatim into the
 * rendered line; the assembler cannot independently classify a shadow Resource because no code path
 * here ever constructs a {@link ClientResourceParityClassification} value — it only ever reads one
 * already supplied by the caller. Text rendering for either side's numeric summary reuses
 * {@link ClientResourceParitySummaryText#format} verbatim — no duplicate formatter.
 *
 * <p><b>Native vs shadow-parity distinction — external-review correction (Blocker 2).</b>
 * {@link ClientResourceParityReportInput.Native} lines never carry, import, or reference
 * {@link ClientResourceParityClassification} at all — they render the fixed presentation-only token
 * {@link #NATIVE_COMPARISON_LABEL} ({@value #NATIVE_COMPARISON_LABEL}), which is plain text, not an
 * enum constant, and can never collide with any of {@code EXACT_MATCH}/{@code GENERIC_NOT_READY}/
 * {@code TRANSITIONAL_MISMATCH}/{@code PERSISTENT_MISMATCH}/{@code MODEL_MISMATCH}/
 * {@code EXPECTED_SEMANTIC_DIFFERENCE}/{@code NOT_APPLICABLE}. {@link ClientResourceParityReportInput.ShadowParity}
 * lines render the tracker's own classification exactly as supplied.
 *
 * <p><b>Standard spell slots — remaining, never used.</b> {@code StandardSpellSlotsResourceAdapter}
 * stores {@code current = maximum - used} (see that class's {@code resolve} method) — the wire/façade/
 * summary {@code currentUnits} for this Resource is already "remaining slots," not "used slots." The
 * command boundary sets {@link ClientResourceParityReportInput.ShadowParity#remainingLabel()} to
 * {@code true} only for this Resource; this assembler then prefixes both summary strings with the
 * literal label {@code "remaining "} so a reader can never misread the bare number as a used-count.
 *
 * <p><b>Access vs. conversion failure — external-review correction (second pass, Blocker 2).</b> An
 * {@link ClientResourceParityReportInput.Native.Status.AccessError}/
 * {@link ClientResourceParityReportInput.ShadowParity.Status.AccessError} entry means the command
 * boundary's own call into the trusted façade/parity observation accessor itself threw. A
 * {@link ClientResourceParityReportInput.Native.Status.ConversionError}/
 * {@link ClientResourceParityReportInput.ShadowParity.Status.ConversionError} entry means that
 * accessor call succeeded, but converting its returned result into this pure input vocabulary threw
 * instead (e.g. the accessor returned a null result/lookup). These are two distinct catch sites in
 * the command boundary, carried here as two distinct status variants — never conflated — and each
 * renders as a differently-worded, still-bounded line. Either way, this assembler only ever renders
 * the bounded exception simple-class name it was handed; it never sees the original exception,
 * message, or stack trace.
 *
 * <p><b>Exact report-size invariant — external-review correction (second pass, Blocker 1).</b>
 * {@link #assemble} validates that {@code inputs} contains exactly
 * {@link ClientResourceParityReport#RESOURCE_LINE_COUNT} entries <i>before</i> allocating any output
 * list — an adversarial list of thousands of inputs is rejected at an O(1) size check, never copied
 * or iterated proportionally to its size. This assembler still does not know or care <i>which</i>
 * seven Resources those are (no {@code Identifier}/{@code PlayerResourceIds} knowledge here, per the
 * first correction pass) — it only enforces the fixed count, which is a pure-report-layer concern;
 * the client command boundary remains solely responsible for which seven ids populate that count and
 * in what order.
 */
public final class ClientResourceParityReportAssembler {

    /** The fixed, non-classification presentation label every native line carries in place of a
     *  parity verdict. Deliberately plain text, never a {@link ClientResourceParityClassification}
     *  constant. */
    public static final String NATIVE_COMPARISON_LABEL = "native-only";

    private static final String NO_OBSERVATION_LABEL = "NO_OBSERVATION_YET";
    private static final String REMAINING_PREFIX = "remaining ";

    private ClientResourceParityReportAssembler() {}

    /** Builds the complete bounded report from an already-ordered, already-converted input list.
     *  Never mutates {@code inputs}; never reorders it. Rejects any size other than exactly
     *  {@link ClientResourceParityReport#RESOURCE_LINE_COUNT} before allocating the output list —
     *  see the class Javadoc's "exact report-size invariant" section. */
    public static ClientResourceParityReport assemble(List<ClientResourceParityReportInput> inputs) {
        java.util.Objects.requireNonNull(inputs, "inputs");
        int expected = ClientResourceParityReport.RESOURCE_LINE_COUNT;
        if (inputs.size() != expected) {
            throw new IllegalArgumentException(
                    "inputs must contain exactly " + expected + " entries (one per production Resource), got "
                            + inputs.size());
        }
        List<ClientResourceParityReportLine> lines = new ArrayList<>(expected);
        for (ClientResourceParityReportInput input : inputs) {
            lines.add(renderLine(input));
        }
        return new ClientResourceParityReport(lines);
    }

    /** The real per-input render step {@link #assemble} itself calls for every entry — package-
     *  private (not a test-only production API) so this package's own focused line-rendering tests
     *  can exercise exactly this method without needing to construct a full seven-entry report each
     *  time. */
    static ClientResourceParityReportLine renderLine(ClientResourceParityReportInput input) {
        try {
            return switch (input) {
                case ClientResourceParityReportInput.Native nativeInput -> renderNative(nativeInput);
                case ClientResourceParityReportInput.ShadowParity shadowInput -> renderShadow(shadowInput);
            };
        } catch (RuntimeException formatterFailure) {
            // Diagnostic tooling must never crash the client — a formatting failure for one resource
            // renders as a bounded, explicit fallback line rather than propagating. Distinct from an
            // AccessError input: this catches a failure inside rendering itself, not inside gathering.
            return new ClientResourceParityReportLine(input.resourceId(), input.resourceId() + " | FORMAT_ERROR");
        }
    }

    private static ClientResourceParityReportLine renderNative(ClientResourceParityReportInput.Native input) {
        String id = input.resourceId();
        return switch (input.status()) {
            case ClientResourceParityReportInput.Native.Status.Available available -> new ClientResourceParityReportLine(id,
                    id + " | native | " + trustLabel(available.trust()) + " | "
                            + ClientResourceParitySummaryText.format(toSummary(available))
                            + " | comparison=" + NATIVE_COMPARISON_LABEL);
            case ClientResourceParityReportInput.Native.Status.Unavailable unavailable -> new ClientResourceParityReportLine(id,
                    id + " | native | UNAVAILABLE(" + unavailable.reason() + ") | comparison=" + NATIVE_COMPARISON_LABEL);
            case ClientResourceParityReportInput.Native.Status.AccessError accessError -> new ClientResourceParityReportLine(id,
                    id + " | native | ACCESS_ERROR(" + accessError.exceptionSimpleName() + ") | comparison=" + NATIVE_COMPARISON_LABEL);
            case ClientResourceParityReportInput.Native.Status.ConversionError conversionError -> new ClientResourceParityReportLine(id,
                    id + " | native | RESULT_CONVERSION_ERROR(" + conversionError.exceptionSimpleName() + ") | comparison=" + NATIVE_COMPARISON_LABEL);
        };
    }

    private static ClientResourceParityReportLine renderShadow(ClientResourceParityReportInput.ShadowParity input) {
        String id = input.resourceId();
        return switch (input.status()) {
            case ClientResourceParityReportInput.ShadowParity.Status.Observed observed -> renderObserved(id, input.remainingLabel(), observed);
            case ClientResourceParityReportInput.ShadowParity.Status.NoObservationYet ignored -> new ClientResourceParityReportLine(id,
                    id + " | " + NO_OBSERVATION_LABEL);
            case ClientResourceParityReportInput.ShadowParity.Status.AccessError accessError -> new ClientResourceParityReportLine(id,
                    id + " | OBSERVATION_ACCESS_ERROR(" + accessError.exceptionSimpleName() + ")");
            case ClientResourceParityReportInput.ShadowParity.Status.ConversionError conversionError -> new ClientResourceParityReportLine(id,
                    id + " | OBSERVATION_CONVERSION_ERROR(" + conversionError.exceptionSimpleName() + ")");
        };
    }

    private static ClientResourceParityReportLine renderObserved(
            String id, boolean remainingLabel, ClientResourceParityReportInput.ShadowParity.Status.Observed observed) {
        String label = remainingLabel ? REMAINING_PREFIX : "";
        String genericText = label + ClientResourceParitySummaryText.format(observed.genericSummary());
        String legacyText = label + ClientResourceParitySummaryText.format(observed.legacySummary());

        StringBuilder text = new StringBuilder();
        text.append(id).append(" | ").append(observed.classification())
                .append(" | generic=").append(genericText)
                .append(" | legacy=").append(legacyText);
        if (observed.firstMismatchTick() != null) {
            text.append(" | firstMismatchTick=").append(observed.firstMismatchTick());
        }
        text.append(" | lastObservedTick=").append(observed.lastObservedTick());
        return new ClientResourceParityReportLine(id, text.toString());
    }

    private static String trustLabel(ClientResourceTrust trust) {
        return switch (trust) {
            case FRESH -> "TRUSTED";
            case PENDING_RESYNC -> "PENDING_RESYNC";
        };
    }

    /** Adapts a native façade's already-converted scalar values into the parity engine's own summary
     *  shape purely so {@link ClientResourceParitySummaryText#format} — already bounded, already
     *  tested — can render it too, without a third, native-only text formatter. */
    private static ClientResourceParitySummary toSummary(ClientResourceParityReportInput.Native.Status.Available available) {
        return new ClientResourceParitySummary.Scalar(
                available.currentUnits(), available.maximumUnits(), available.overflowUnits(), available.unitScale());
    }
}
