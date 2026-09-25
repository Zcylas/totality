package zcylas.totality.api.mining;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.mining.BlockProfile.Classification;
import zcylas.totality.api.mining.BlockProfile.Layer;
import zcylas.totality.api.mining.BlockProfile.Ownership;
import zcylas.totality.api.mining.BlockProfile.ToolAffinity;
import zcylas.totality.api.mining.BlockCoverageReport.Category;

import static org.junit.jupiter.api.Assertions.*;

/** Pass 3 coverage categories: unresolved design is never reported as accepted, and fallback is never "zero remaining". */
class BlockCoverageCategoryTest {

    private static BlockProfile.Resolved p(Classification c, Layer durability, Layer classification) {
        return new BlockProfile.Resolved(null, null, c, 100f, ToolAffinity.NONE, 1, Ownership.POSITION, durability, classification, Layer.FALLBACK);
    }

    private static final Identifier VANILLA = Identifier.withDefaultNamespace("some_block");
    private static final Identifier TOTALITY = Identifier.fromNamespaceAndPath("totality", "some_block");

    @Test
    void authoredOrdinaryIsAcceptedAndFallbackOrdinaryIsUnresolvedForVanilla() {
        assertEquals(Category.ACCEPTED_AUTHORED, BlockCoverageReport.categorize(VANILLA, p(Classification.ORDINARY, Layer.MATERIAL_FORM, Layer.MATERIAL), false));
        assertEquals(Category.UNRESOLVED_DESIGN, BlockCoverageReport.categorize(VANILLA, p(Classification.ORDINARY, Layer.FALLBACK, Layer.FALLBACK), false));
        assertEquals(Category.COMPAT_FALLBACK, BlockCoverageReport.categorize(TOTALITY, p(Classification.ORDINARY, Layer.FALLBACK, Layer.FALLBACK), false),
                "non-vanilla fallback is deferred to Totality Core, reported separately");
    }

    @Test
    void onlyExplicitSpecialCountsAsSpecial() {
        assertEquals(Category.SPECIAL, BlockCoverageReport.categorize(VANILLA, p(Classification.SPECIAL, Layer.FALLBACK, Layer.BLOCK), false));
        assertEquals(Category.UNRESOLVED_DESIGN, BlockCoverageReport.categorize(VANILLA, p(Classification.SPECIAL, Layer.FALLBACK, Layer.FALLBACK), false),
                "hardness-0 SPECIAL from the fallback is not an accepted design decision");
    }

    @Test
    void unbreakableAndNotApplicableAreTheirOwnCategories() {
        assertEquals(Category.UNBREAKABLE, BlockCoverageReport.categorize(VANILLA, p(Classification.UNBREAKABLE, Layer.FALLBACK, Layer.BLOCK), false));
        assertEquals(Category.NA, BlockCoverageReport.categorize(VANILLA, p(Classification.NOT_APPLICABLE, Layer.FALLBACK, Layer.FALLBACK), false));
    }

    @Test
    void ledgerReviewFlagForcesUnresolvedEvenWithAValue() {
        assertEquals(Category.UNRESOLVED_DESIGN, BlockCoverageReport.categorize(VANILLA, p(Classification.ORDINARY, Layer.BLOCK, Layer.BLOCK), true));
    }
}
