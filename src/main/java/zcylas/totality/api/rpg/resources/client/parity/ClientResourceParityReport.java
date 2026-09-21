package zcylas.totality.api.rpg.resources.client.parity;

import java.util.List;
import java.util.Objects;

/**
 * The complete, bounded, deterministic output of one Phase 3B-3 on-demand parity inspection —
 * one fixed header plus exactly {@link #RESOURCE_LINE_COUNT} {@link ClientResourceParityReportLine}s,
 * one per production Resource (Health, Food, Breath, Mana, Stamina, Standard Spell Slots, Rage), in
 * the order {@link ClientResourceParityReportAssembler} assembled them. Immutable by construction:
 * the line list is defensively copied, and every line it contains is itself an immutable record.
 *
 * <p><b>External-review correction — bounded-header invariant (first correction pass).</b> The header
 * is not an arbitrary caller-supplied {@code String}: {@link #HEADER} is a fixed, internal constant
 * and is not part of this record's own state at all — there is no constructor parameter for one.
 *
 * <p><b>External-review correction — exact report-size invariant (second correction pass).</b> A
 * bounded header and bounded individual lines are not, by themselves, a bounded <i>report</i>: the
 * first correction pass left {@code lines} accepting any size at all, including zero or one entry —
 * both explicitly (and wrongly) exercised by the first correction's own test suite. The compact
 * constructor now rejects any {@code lines} whose size is not exactly {@link #RESOURCE_LINE_COUNT}
 * — deterministically, via an {@link IllegalArgumentException}, before the input is ever copied, so
 * an adversarial thousands-entry list is rejected at an O(1) size check rather than being copied
 * proportionally to its size first. This makes the complete report's maximum possible output size a
 * provable, static fact: {@link #HEADER}'s fixed length, plus exactly {@link #RESOURCE_LINE_COUNT}
 * lines each capped at {@link ClientResourceParityReportLine#MAX_TEXT_LENGTH} — never a function of
 * how many entries some caller happened to pass in.
 *
 * <p>Deliberately carries no Minecraft/Fabric/command dependency — {@link #chatLines()} returns
 * plain {@link String}s; wrapping each into a chat {@code Component} is the impure client-command
 * boundary's own job (see {@code ClientResourceParityInspectionCommand}), not this pure report's.
 */
public record ClientResourceParityReport(List<ClientResourceParityReportLine> lines) {

    /** The one, fixed report header. Never configurable, never derived from caller input. */
    public static final String HEADER = "[Resource Parity]";

    /** The exact, fixed number of production Resources this command ever inspects — Health, Food,
     *  Breath, Mana, Stamina, Standard Spell Slots, Rage. Not a minimum or a maximum: {@link #lines}
     *  must contain <i>exactly</i> this many entries, or construction fails. */
    public static final int RESOURCE_LINE_COUNT = 7;

    /** The exact, fixed number of strings {@link #chatLines()} ever returns: the one fixed
     *  {@link #HEADER} plus exactly {@link #RESOURCE_LINE_COUNT} Resource lines. */
    public static final int CHAT_LINE_COUNT = RESOURCE_LINE_COUNT + 1;

    public ClientResourceParityReport {
        Objects.requireNonNull(lines, "lines");
        if (lines.size() != RESOURCE_LINE_COUNT) {
            throw new IllegalArgumentException(
                    "lines must contain exactly " + RESOURCE_LINE_COUNT + " entries (one per production "
                            + "Resource), got " + lines.size());
        }
        lines = List.copyOf(lines);
    }

    /** The fixed {@link #HEADER} followed by each line's rendered text, in order — always exactly
     *  {@link #CHAT_LINE_COUNT} strings, exactly what the client-only command boundary sends to chat
     *  feedback, one bounded string per list entry. */
    public List<String> chatLines() {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(HEADER),
                lines.stream().map(ClientResourceParityReportLine::text)
        ).toList();
    }
}
