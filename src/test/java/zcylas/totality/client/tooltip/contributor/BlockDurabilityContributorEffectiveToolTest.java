package zcylas.totality.client.tooltip.contributor;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.mining.BlockProfile.Tool;
import zcylas.totality.api.mining.BlockProfile.ToolAffinity;

import static org.junit.jupiter.api.Assertions.*;

/** Effective Tool row value of the Tooltip V2 block properties (Block Breaking V2 Pass 1 follow-up). */
class BlockDurabilityContributorEffectiveToolTest {

    @Test
    void toolNeutralOrdinaryBlockSaysAny() {
        assertEquals("Any", BlockDurabilityContributor.effectiveToolText(ToolAffinity.NEUTRAL));
    }

    @Test
    void preferredToolsAreListedInCategoryOrder() {
        assertEquals("Pickaxe", BlockDurabilityContributor.effectiveToolText(ToolAffinity.of(Tool.PICKAXE)));
        assertEquals("Pickaxe, Axe, Shovel", BlockDurabilityContributor.effectiveToolText(ToolAffinity.of(Tool.SHOVEL, Tool.AXE, Tool.PICKAXE)));
    }

    @Test
    void hoeIsAnEffectiveToolCategory() {
        assertEquals("Hoe", BlockDurabilityContributor.effectiveToolText(ToolAffinity.of(Tool.HOE)));
        assertEquals("Axe, Hoe", BlockDurabilityContributor.effectiveToolText(ToolAffinity.of(Tool.HOE, Tool.AXE)));
    }

    @Test
    void noConventionalPreferredToolShowsNoRow() {
        assertNull(BlockDurabilityContributor.effectiveToolText(ToolAffinity.NONE));
    }

    @Test
    void requiredMiningTierStaysItsOwnRowIndependentOfToolNeutrality() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/zcylas/totality/client/tooltip/contributor/BlockDurabilityContributor.java"));
        int tier = src.indexOf("\"Required Mining Tier\", String.valueOf(resolved.requiredTier())");
        int tool = src.indexOf("\"Effective Tool\", effectiveTool");
        assertTrue(tier >= 0 && tool > tier, "the tier row is emitted unconditionally, before and apart from the tool row");
    }
}
