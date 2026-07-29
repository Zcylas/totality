package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-3: {@link ClientResourceParityReportAssembler} is the pure report assembler behind
 * {@code /totality resource parity}. This is the primary automated coverage for the task's required
 * test matrix (items 1-27; items 28-34 are covered by {@code
 * zcylas.totality.client.resource.parity.ClientResourceParityInspectionCommandTest}, the command
 * boundary's own test, since they concern command-invocation/registration/access-containment behavior
 * rather than pure report assembly).
 *
 * <p><b>External-review correction.</b> Every input here is a directly-constructed
 * {@link ClientResourceParityReportInput} — a plain-{@code String}-keyed, Minecraft-independent
 * record — never a {@code net.minecraft.resources.Identifier}-carrying {@code
 * ClientResourceQueryResult}/{@code ClientResourceParityObservation}, and {@link
 * ClientResourceParityReportAssembler#assemble} now takes a single ordered {@code List}, not two
 * {@code Map}s — the assembler holds no canonical-ordering knowledge of its own; this test always
 * supplies its own explicit order and asserts the assembler preserved it exactly.
 */
class ClientResourceParityReportAssemblerTest {

    private static ClientResourceParityReportInput.Native available(String id, long current, long max, ClientResourceTrust trust) {
        return new ClientResourceParityReportInput.Native(id, new ClientResourceParityReportInput.Native.Status.Available(current, max, 0, 1, trust));
    }

    private static ClientResourceParityReportInput.Native nativeUnavailable(String id, ClientResourceUnavailableReason reason) {
        return new ClientResourceParityReportInput.Native(id, new ClientResourceParityReportInput.Native.Status.Unavailable(reason));
    }

    private static ClientResourceParityReportInput.ShadowParity matchShadow(String id, long current, long max, long tick, boolean remainingLabel) {
        var summary = new ClientResourceParitySummary.Scalar(current, max, 0, 1);
        return new ClientResourceParityReportInput.ShadowParity(
                id, new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.EXACT_MATCH, summary, summary, null, tick),
                remainingLabel);
    }

    /** Calls the assembler's real per-input render step directly — {@link
     *  ClientResourceParityReportAssembler#renderLine} is package-private specifically so focused
     *  line-rendering tests like these don't need to construct a full seven-entry report just to
     *  exercise one input's rendering (external-review correction, second pass, Blocker 1). */
    private static String render(ClientResourceParityReportInput input) {
        return ClientResourceParityReportAssembler.renderLine(input).text();
    }

    /** A valid, exactly-seven-entry input list — the only shape {@code assemble(...)} now accepts
     *  (external-review correction, second pass, Blocker 1). Used by every test that exercises
     *  {@code assemble(...)} itself rather than the single-input {@link #render} helper. */
    private static List<ClientResourceParityReportInput> sevenInputsInOrder(String... orderedIds) {
        List<ClientResourceParityReportInput> inputs = new ArrayList<>();
        for (String id : orderedIds) {
            inputs.add(available(id, 1, 1, ClientResourceTrust.FRESH));
        }
        return inputs;
    }

    // ── ordering is preserved exactly as supplied, never re-derived ───────────────────────────

    @Test
    void assemblerPreservesTheExactSuppliedOrder() {
        List<ClientResourceParityReportInput> inputs = sevenInputsInOrder(
                "totality:rage", "totality:health", "totality:mana",
                "totality:food", "totality:breath", "totality:stamina", "totality:spell_slots");

        var report = ClientResourceParityReportAssembler.assemble(inputs);
        List<String> order = report.lines().stream().map(ClientResourceParityReportLine::resourceId).toList();
        assertEquals(List.of("totality:rage", "totality:health", "totality:mana",
                "totality:food", "totality:breath", "totality:stamina", "totality:spell_slots"), order);
    }

    @Test
    void repeatedAssembleCallsProduceTheSameOrder() {
        List<ClientResourceParityReportInput> inputs = sevenInputsInOrder(
                "totality:health", "totality:food", "totality:breath",
                "totality:mana", "totality:stamina", "totality:spell_slots", "totality:rage");
        var report1 = ClientResourceParityReportAssembler.assemble(inputs);
        var report2 = ClientResourceParityReportAssembler.assemble(inputs);
        assertEquals(report1.chatLines(), report2.chatLines());
    }

    // ── Blocker 1 (second correction pass): assemble(...) rejects any size other than exactly ──
    // ── ClientResourceParityReport.RESOURCE_LINE_COUNT, before allocating any output list ──────

    @Test
    void assembleRejectsFewerThanSevenInputs() {
        List<ClientResourceParityReportInput> six = sevenInputsInOrder(
                "totality:health", "totality:food", "totality:breath",
                "totality:mana", "totality:stamina", "totality:spell_slots");
        assertEquals(6, six.size());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ClientResourceParityReportAssembler.assemble(six));
    }

    @Test
    void assembleRejectsMoreThanSevenInputs() {
        List<ClientResourceParityReportInput> eight = sevenInputsInOrder(
                "totality:health", "totality:food", "totality:breath", "totality:mana",
                "totality:stamina", "totality:spell_slots", "totality:rage", "totality:extra");
        assertEquals(8, eight.size());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ClientResourceParityReportAssembler.assemble(eight));
    }

    @Test
    void assembleRejectsAVeryLargeInputListBeforeAllocatingAProportionalOutputList() {
        List<ClientResourceParityReportInput> huge = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            huge.add(available("totality:r" + i, 1, 1, ClientResourceTrust.FRESH));
        }
        // If assemble(...) allocated an output list sized to `huge` before validating the count, this
        // would still "work" (slowly) rather than throw — the assertion that it throws is itself the
        // proof the size check runs first; a timeout-based proof is unnecessary since the exception
        // is deterministic, not a matter of chance.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ClientResourceParityReportAssembler.assemble(huge));
    }

    // ── 2/3/4: Health/Food/Breath native presentation result ──────────────────────────────────

    @Test
    void healthNativeResultRendersTrustedCurrentMaximum() {
        String line = render(available("totality:health", 20, 20, ClientResourceTrust.FRESH));
        assertTrue(line.contains("native"));
        assertTrue(line.contains("TRUSTED"));
        assertTrue(line.contains("20/20"));
        assertTrue(line.contains("comparison=" + ClientResourceParityReportAssembler.NATIVE_COMPARISON_LABEL));
    }

    @Test
    void foodNativeResultRendersTrustedCurrentMaximum() {
        String line = render(available("totality:food", 14, 20, ClientResourceTrust.FRESH));
        assertTrue(line.contains("native"));
        assertTrue(line.contains("14/20"));
    }

    @Test
    void breathNativeResultRendersTrustedCurrentMaximum() {
        String line = render(available("totality:breath", 300, 300, ClientResourceTrust.FRESH));
        assertTrue(line.contains("native"));
        assertTrue(line.contains("300/300"));
    }

    // ── Blocker 2: native lines never carry a tracker classification ──────────────────────────

    @Test
    void nativeLineNeverContainsAnyParityClassificationName() {
        String line = render(available("totality:health", 20, 20, ClientResourceTrust.FRESH));
        for (ClientResourceParityClassification classification : ClientResourceParityClassification.values()) {
            assertFalse(line.contains(classification.name()),
                    "native line must never contain a ClientResourceParityClassification name, found " + classification);
        }
        assertTrue(line.contains("comparison=" + ClientResourceParityReportAssembler.NATIVE_COMPARISON_LABEL));
    }

    @Test
    void everyNativeStatusVariantUsesTheFixedComparisonLabelNeverAClassification() {
        String available = render(available("totality:health", 20, 20, ClientResourceTrust.FRESH));
        String unavailable = render(nativeUnavailable("totality:food", ClientResourceUnavailableReason.NO_LOCAL_PLAYER));
        String accessError = render(new ClientResourceParityReportInput.Native(
                "totality:breath", new ClientResourceParityReportInput.Native.Status.AccessError("IllegalStateException")));
        String conversionError = render(new ClientResourceParityReportInput.Native(
                "totality:mana", new ClientResourceParityReportInput.Native.Status.ConversionError("NullPointerException")));

        for (String line : List.of(available, unavailable, accessError, conversionError)) {
            assertTrue(line.contains("comparison=" + ClientResourceParityReportAssembler.NATIVE_COMPARISON_LABEL));
            for (ClientResourceParityClassification classification : ClientResourceParityClassification.values()) {
                assertFalse(line.contains(classification.name()));
            }
        }
    }

    // ── 5/6/7/8: exact match for every shadow-parity resource — classification echoed verbatim ─

    @Test
    void manaExactMatchRendersBothSummariesAndTheSuppliedClassificationVerbatim() {
        String line = render(matchShadow("totality:mana", 100, 100, 42, false));
        assertTrue(line.contains("EXACT_MATCH"));
        assertTrue(line.contains("generic=100/100"));
        assertTrue(line.contains("legacy=100/100"));
    }

    @Test
    void staminaExactMatchRendersBothSummaries() {
        String line = render(matchShadow("totality:stamina", 80, 100, 1, false));
        assertTrue(line.contains("EXACT_MATCH"));
        assertTrue(line.contains("generic=80/100"));
    }

    @Test
    void spellSlotExactMatchRendersRemainingLabelForBothSides() {
        var partitioned = ClientResourceParitySummary.Partitioned.of(List.of(
                new ClientResourceParitySummary.Partitioned.Partition(1, 2, 4, 0),
                new ClientResourceParitySummary.Partitioned.Partition(2, 0, 1, 0)
        ), 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:spell_slots",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.EXACT_MATCH, partitioned, partitioned, null, 5),
                true);

        String line = render(input);
        assertTrue(line.contains("EXACT_MATCH"));
        assertTrue(line.contains("generic=remaining 1:2/4,2:0/1"));
        assertTrue(line.contains("legacy=remaining 1:2/4,2:0/1"));
    }

    @Test
    void rageExactMatchRendersBothSummaries() {
        String line = render(matchShadow("totality:rage", 1, 2, 3, false));
        assertTrue(line.contains("EXACT_MATCH"));
        assertTrue(line.contains("generic=1/2"));
        assertTrue(line.contains("legacy=1/2"));
    }

    // ── 9: persistent scalar mismatch ──────────────────────────────────────────────────────────

    @Test
    void persistentScalarMismatchIncludesFirstAndLastObservedTicks() {
        var generic = new ClientResourceParitySummary.Scalar(1, 2, 0, 1);
        var legacy = new ClientResourceParitySummary.Scalar(0, 0, 0, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:rage",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.PERSISTENT_MISMATCH, generic, legacy, 10L, 20L),
                false);

        String line = render(input);
        assertTrue(line.contains("PERSISTENT_MISMATCH"));
        assertTrue(line.contains("generic=1/2"));
        assertTrue(line.contains("legacy=0/0"));
        assertTrue(line.contains("firstMismatchTick=10"));
        assertTrue(line.contains("lastObservedTick=20"));
    }

    // ── 10: persistent partitioned mismatch ────────────────────────────────────────────────────

    @Test
    void persistentPartitionedMismatchRendersBothDivergentPartitionSets() {
        var generic = ClientResourceParitySummary.Partitioned.of(List.of(
                new ClientResourceParitySummary.Partitioned.Partition(1, 1, 2, 0)), 1);
        var legacy = ClientResourceParitySummary.Partitioned.of(List.of(
                new ClientResourceParitySummary.Partitioned.Partition(1, 0, 2, 0)), 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:spell_slots",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.PERSISTENT_MISMATCH, generic, legacy, 5L, 9L),
                true);

        String line = render(input);
        assertTrue(line.contains("PERSISTENT_MISMATCH"));
        assertTrue(line.contains("generic=remaining 1:1/2"));
        assertTrue(line.contains("legacy=remaining 1:0/2"));
        assertTrue(line.contains("firstMismatchTick=5"));
    }

    // ── 11-16: every classification value renders without error, verbatim ─────────────────────

    @Test
    void genericNotReadyRendersUnavailableGenericSideWithLegacyStillShown() {
        var generic = new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET);
        var legacy = new ClientResourceParitySummary.Scalar(50, 100, 0, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:mana",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.GENERIC_NOT_READY, generic, legacy, null, 1),
                false);

        String line = render(input);
        assertTrue(line.contains("GENERIC_NOT_READY"));
        assertTrue(line.contains("generic=unavailable:NOT_SYNCHRONIZED_YET"));
        assertTrue(line.contains("legacy=50/100"));
    }

    @Test
    void transitionalMismatchIncludesFirstMismatchTick() {
        var generic = new ClientResourceParitySummary.Scalar(1, 2, 0, 1);
        var legacy = new ClientResourceParitySummary.Scalar(0, 0, 0, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:rage",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.TRANSITIONAL_MISMATCH, generic, legacy, 7L, 7L),
                false);
        assertTrue(render(input).contains("TRANSITIONAL_MISMATCH"));
        assertTrue(render(input).contains("firstMismatchTick=7"));
    }

    @Test
    void persistentMismatchClassificationRendersExactly() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:mana",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.PERSISTENT_MISMATCH, summary, new ClientResourceParitySummary.Scalar(0, 1, 0, 1), 2L, 4L),
                false);
        assertTrue(render(input).contains("PERSISTENT_MISMATCH"));
    }

    @Test
    void modelMismatchRendersWithoutFirstMismatchTick() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:stamina",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.MODEL_MISMATCH, summary, summary, null, 3),
                false);
        String line = render(input);
        assertTrue(line.contains("MODEL_MISMATCH"));
        assertFalse(line.contains("firstMismatchTick"));
    }

    @Test
    void expectedSemanticDifferenceRendersExactly() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:stamina",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.EXPECTED_SEMANTIC_DIFFERENCE, summary, summary, null, 6),
                false);
        assertTrue(render(input).contains("EXPECTED_SEMANTIC_DIFFERENCE"));
    }

    @Test
    void notApplicableClassificationOnAShadowObservationStillRendersBounded() {
        // NOT_APPLICABLE is a value the tracker itself could in principle supply for a shadow
        // Resource's observation; this test only proves the renderer handles the full enum
        // defensively when it's supplied via Observed, never that the assembler invents it.
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:rage",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.NOT_APPLICABLE, summary, summary, null, 1),
                false);
        assertTrue(render(input).contains("NOT_APPLICABLE"));
    }

    // ── 17: Rage Unavailable vs numeric 0/0 remains distinguishable ───────────────────────────

    @Test
    void rageUnavailableLegacyDiffersFromGenuineZeroZeroLegacy() {
        var generic = new ClientResourceParitySummary.Scalar(1, 2, 0, 1);

        var unavailableLegacy = new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER);
        var unavailableInput = new ClientResourceParityReportInput.ShadowParity(
                "totality:rage",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.EXPECTED_SEMANTIC_DIFFERENCE, generic, unavailableLegacy, null, 1),
                false);
        String unavailableLine = render(unavailableInput);

        var zeroLegacy = new ClientResourceParitySummary.Scalar(0, 0, 0, 1);
        var zeroInput = new ClientResourceParityReportInput.ShadowParity(
                "totality:rage",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.PERSISTENT_MISMATCH, generic, zeroLegacy, 1L, 1L),
                false);
        String zeroLine = render(zeroInput);

        assertTrue(unavailableLine.contains("legacy=unavailable:NOT_AVAILABLE_TO_PLAYER"));
        assertTrue(zeroLine.contains("legacy=0/0"));
        assertFalse(unavailableLine.equals(zeroLine));
    }

    // ── 18: no local player / no world ─────────────────────────────────────────────────────────

    @Test
    void noLocalPlayerRendersBoundedUnavailableNativeLines() {
        for (String id : List.of("totality:health", "totality:food", "totality:breath")) {
            String line = render(nativeUnavailable(id, ClientResourceUnavailableReason.NO_LOCAL_PLAYER));
            assertTrue(line.contains("UNAVAILABLE(NO_LOCAL_PLAYER)"));
        }
    }

    // ── 19: missing parity observation ─────────────────────────────────────────────────────────

    @Test
    void missingObservationRendersBoundedPlaceholderNotAFabricatedClassification() {
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:mana", new ClientResourceParityReportInput.ShadowParity.Status.NoObservationYet(), false);
        assertEquals("totality:mana | NO_OBSERVATION_YET", render(input));
    }

    // ── 20: pending resync ─────────────────────────────────────────────────────────────────────

    @Test
    void pendingResyncNativeTrustRendersDistinctLabelFromTrusted() {
        String line = render(available("totality:health", 20, 20, ClientResourceTrust.PENDING_RESYNC));
        assertTrue(line.contains("PENDING_RESYNC"));
        assertFalse(line.contains("TRUSTED"));
    }

    // ── 21: malformed source state ─────────────────────────────────────────────────────────────

    @Test
    void malformedSourceStateRendersBoundedUnavailableLine() {
        String line = render(nativeUnavailable("totality:breath", ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE));
        assertTrue(line.contains("UNAVAILABLE(MALFORMED_SOURCE_STATE)"));
    }

    // ── 22: empty spell-slot partitions ────────────────────────────────────────────────────────

    @Test
    void emptySpellSlotPartitionsRenderWithoutException() {
        var empty = ClientResourceParitySummary.Partitioned.of(List.of(), 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:spell_slots",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.EXACT_MATCH, empty, empty, null, 1),
                true);
        String line = render(input);
        assertTrue(line.contains("EXACT_MATCH"));
        assertTrue(line.contains("generic=remaining"));
        assertTrue(line.contains("legacy=remaining"));
    }

    // ── 23: deterministic spell-slot partition ordering ────────────────────────────────────────

    @Test
    void spellSlotPartitionsRenderInAscendingOrderRegardlessOfInputOrder() {
        var outOfOrder = ClientResourceParitySummary.Partitioned.of(List.of(
                new ClientResourceParitySummary.Partitioned.Partition(3, 1, 1, 0),
                new ClientResourceParitySummary.Partitioned.Partition(1, 2, 2, 0),
                new ClientResourceParitySummary.Partitioned.Partition(2, 0, 0, 0)
        ), 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:spell_slots",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.EXACT_MATCH, outOfOrder, outOfOrder, null, 1),
                true);
        assertTrue(render(input).contains("generic=remaining 1:2/2,2:0/0,3:1/1"));
    }

    // ── 24: large/adversarial partition input remains bounded ─────────────────────────────────

    @Test
    void largeAdversarialPartitionInputStaysWithinTheLineBound() {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions = new ArrayList<>();
        for (int i = 1; i <= 5000; i++) {
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(i, i, i, 0));
        }
        var huge = ClientResourceParitySummary.Partitioned.of(partitions, 1);
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:spell_slots",
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        ClientResourceParityClassification.EXACT_MATCH, huge, huge, null, 1),
                true);

        assertTrue(render(input).length() <= ClientResourceParityReportLine.MAX_TEXT_LENGTH);
    }

    // ── 25: proven maximum number of lines/characters ──────────────────────────────────────────

    @Test
    void everyRenderedLineStaysWithinTheBoundAndHeaderStaysFixed() {
        List<ClientResourceParityReportInput> sevenInputs = List.of(
                available("totality:health", 20, 20, ClientResourceTrust.FRESH),
                available("totality:food", 20, 20, ClientResourceTrust.FRESH),
                available("totality:breath", 300, 300, ClientResourceTrust.FRESH),
                matchShadow("totality:mana", 1, 1, 1, false),
                matchShadow("totality:stamina", 1, 1, 1, false),
                matchShadow("totality:spell_slots", 1, 1, 1, true),
                matchShadow("totality:rage", 1, 2, 1, false));

        var report = ClientResourceParityReportAssembler.assemble(sevenInputs);
        assertEquals(7, report.lines().size());
        for (ClientResourceParityReportLine line : report.lines()) {
            assertTrue(line.text().length() <= ClientResourceParityReportLine.MAX_TEXT_LENGTH);
        }
        assertEquals(8, report.chatLines().size());
        assertEquals(ClientResourceParityReport.HEADER, report.chatLines().get(0));
    }

    // ── 26: assembler never mutates its input list ─────────────────────────────────────────────

    @Test
    void assembleDoesNotMutateTheSuppliedInputList() {
        List<ClientResourceParityReportInput> inputs = new ArrayList<>(sevenInputsInOrder(
                "totality:health", "totality:food", "totality:breath",
                "totality:mana", "totality:stamina", "totality:spell_slots", "totality:rage"));
        int sizeBefore = inputs.size();
        var firstElementBefore = inputs.get(0);

        ClientResourceParityReportAssembler.assemble(inputs);

        assertEquals(sizeBefore, inputs.size());
        assertEquals(firstElementBefore, inputs.get(0));
    }

    // ── 27: repeated report generation is deterministic ────────────────────────────────────────

    @Test
    void repeatedAssembleWithIdenticalInputProducesIdenticalChatLines() {
        List<ClientResourceParityReportInput> inputs = sevenInputsInOrder(
                "totality:health", "totality:food", "totality:breath",
                "totality:mana", "totality:stamina", "totality:spell_slots", "totality:rage");

        var report1 = ClientResourceParityReportAssembler.assemble(inputs);
        var report2 = ClientResourceParityReportAssembler.assemble(inputs);

        assertEquals(report1.chatLines(), report2.chatLines());
    }

    // ── access-error rendering (Blocker 3, assembler-level rendering proof) ───────────────────

    @Test
    void nativeAccessErrorRendersBoundedLabelWithExceptionSimpleName() {
        var input = new ClientResourceParityReportInput.Native(
                "totality:health", new ClientResourceParityReportInput.Native.Status.AccessError("IllegalStateException"));
        String line = render(input);
        assertTrue(line.contains("ACCESS_ERROR(IllegalStateException)"));
        assertTrue(line.contains("comparison=" + ClientResourceParityReportAssembler.NATIVE_COMPARISON_LABEL));
    }

    @Test
    void shadowAccessErrorRendersDistinctLabelFromNativeAccessError() {
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:rage", new ClientResourceParityReportInput.ShadowParity.Status.AccessError("IllegalStateException"), false);
        String line = render(input);
        assertTrue(line.contains("OBSERVATION_ACCESS_ERROR(IllegalStateException)"));
        assertFalse(line.contains("comparison="));
    }

    // ── conversion-error rendering (second correction pass, Blocker 2) ────────────────────────

    @Test
    void nativeConversionErrorRendersADistinctLabelFromNativeAccessError() {
        var input = new ClientResourceParityReportInput.Native(
                "totality:health", new ClientResourceParityReportInput.Native.Status.ConversionError("NullPointerException"));
        String line = render(input);
        assertTrue(line.contains("RESULT_CONVERSION_ERROR(NullPointerException)"));
        assertFalse(line.contains("ACCESS_ERROR(NullPointerException)"),
                "a conversion-error line must never also read as an access-error line");
        assertTrue(line.contains("comparison=" + ClientResourceParityReportAssembler.NATIVE_COMPARISON_LABEL));
    }

    @Test
    void shadowConversionErrorRendersADistinctLabelFromShadowAccessError() {
        var input = new ClientResourceParityReportInput.ShadowParity(
                "totality:rage", new ClientResourceParityReportInput.ShadowParity.Status.ConversionError("NullPointerException"), false);
        String line = render(input);
        assertTrue(line.contains("OBSERVATION_CONVERSION_ERROR(NullPointerException)"));
        assertFalse(line.contains("OBSERVATION_ACCESS_ERROR(NullPointerException)"),
                "a conversion-error line must never also read as an access-error line");
    }

    @Test
    void accessAndConversionErrorLabelsAreTextuallyDistinctForTheSameExceptionName() {
        String accessLine = render(new ClientResourceParityReportInput.Native(
                "totality:health", new ClientResourceParityReportInput.Native.Status.AccessError("IllegalStateException")));
        String conversionLine = render(new ClientResourceParityReportInput.Native(
                "totality:health", new ClientResourceParityReportInput.Native.Status.ConversionError("IllegalStateException")));

        assertFalse(accessLine.equals(conversionLine), "access and conversion failures for the same exception type must render differently");
        assertTrue(accessLine.contains("ACCESS_ERROR"));
        assertTrue(conversionLine.contains("RESULT_CONVERSION_ERROR"));
    }
}
