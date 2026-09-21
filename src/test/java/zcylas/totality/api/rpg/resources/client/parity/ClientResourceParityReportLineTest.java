package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-3: {@link ClientResourceParityReportLine} is the pure, bounded per-resource line type the
 * on-demand parity inspection report is built from. Covers the required-coverage item proving the
 * report has a hard maximum line length under the selected bounded-output policy.
 *
 * <p>External-review correction: {@code resourceId} is a plain {@link String}, not
 * {@code net.minecraft.resources.Identifier} — this test uses literal id strings (e.g.
 * {@code "totality:mana"}) rather than {@code PlayerResourceIds}, since this pure type must not
 * depend on anything Minecraft-related, including in its own test's imports.
 */
class ClientResourceParityReportLineTest {

    @Test
    void shortTextIsPreservedExactly() {
        var line = new ClientResourceParityReportLine("totality:mana", "totality:mana | EXACT_MATCH | generic=100/100 | legacy=100/100");
        assertEquals("totality:mana | EXACT_MATCH | generic=100/100 | legacy=100/100", line.text());
    }

    @Test
    void overlongTextIsTruncatedToTheBound() {
        String overlong = "x".repeat(ClientResourceParityReportLine.MAX_TEXT_LENGTH * 3);
        var line = new ClientResourceParityReportLine("totality:rage", overlong);

        assertTrue(line.text().length() <= ClientResourceParityReportLine.MAX_TEXT_LENGTH,
                "line text must never exceed MAX_TEXT_LENGTH regardless of input length");
        assertTrue(line.text().endsWith("...(truncated)"));
    }

    @Test
    void textAtExactlyTheBoundIsNotTruncated() {
        String exact = "x".repeat(ClientResourceParityReportLine.MAX_TEXT_LENGTH);
        var line = new ClientResourceParityReportLine("totality:stamina", exact);
        assertEquals(exact, line.text());
    }
}
