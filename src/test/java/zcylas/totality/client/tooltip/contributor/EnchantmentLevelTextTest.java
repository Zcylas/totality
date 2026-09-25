package zcylas.totality.client.tooltip.contributor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tooltip V2 final correction: single-level enchantments show no redundant level numeral, in DEFAULT and SHIFT alike.
 * Multi-level numerals come from the localized {@code enchantment.level.N} keys, which need a bootstrapped client, so they
 * are verified in a real client by BlockBreakingFinalCorrectionClientGameTest.
 */
class EnchantmentLevelTextTest {

    @Test
    void singleLevelEnchantmentAtItsOnlyLevelShowsNoNumeralInEitherView() {
        assertEquals("", EnchantmentsContributor.value(1, 1, false), "Mending / Silk Touch / curses: no I");
        assertEquals("", EnchantmentsContributor.value(1, 1, true), "and no I / I under SHIFT");
    }
}
