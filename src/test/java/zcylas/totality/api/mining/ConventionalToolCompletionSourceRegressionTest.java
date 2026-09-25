package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block Breaking V2 Pass 2 — conventional tool completion. The authored material table and its wiring use
 * Minecraft registries (no JUnit bootstrap), so they are pinned here at source/data level; live values are asserted
 * by {@code MiningVerification}.
 */
class ConventionalToolCompletionSourceRegressionTest {

    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    private static final String PROFILE = "src/main/java/zcylas/totality/api/mining/MiningSourceProfile.java";

    @Test
    void goldHasTheAcceptedRow() throws Exception {
        assertTrue(read(PROFILE).contains(
                "new Material(\"Gold\", Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE, 25f, 3.0f)"),
                "Gold: Mining Damage 25, Mining Speed 3.0/s, Pickaxe/Axe/Shovel/Hoe");
    }

    @Test
    void everyMaterialRowCarriesItsHoeWithTheSameBaseline() throws Exception {
        String src = read(PROFILE);
        List<String> rows = List.of(
                "Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, 100f, 2.5f",
                "Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, 75f, 2.4f",
                "Items.IRON_SHOVEL, Items.IRON_HOE, 50f, 2.25f",
                "Items.COPPER_SHOVEL, Items.COPPER_HOE, 40f, 2.1f",
                "Items.STONE_SHOVEL, Items.STONE_HOE, 35f, 2.0f",
                "Items.WOODEN_SHOVEL, Items.WOODEN_HOE, 20f, 1.5f");
        for (String row : rows) assertTrue(src.contains(row), row);
        assertTrue(src.contains("map.put(m.hoe(), m);") && src.contains("items.add(m.hoe());"),
                "hoes resolve and participate (Impact tag) through the same single table");
    }

    @Test
    void generatedImpactTagContainsGoldAndHoesButNoSpecializedSources() throws Exception {
        String tag = read("src/main/generated/data/totality/tags/item/enchantable/mining_damage.json");
        for (String item : List.of("golden_pickaxe", "golden_axe", "golden_shovel", "golden_hoe", "wooden_hoe",
                "stone_hoe", "copper_hoe", "iron_hoe", "diamond_hoe", "netherite_hoe")) {
            assertTrue(tag.contains("\"minecraft:" + item + "\""), item);
        }
        assertEquals(28, tag.split("\"minecraft:").length - 1, "exactly 7 materials x Pickaxe/Axe/Shovel/Hoe");
    }

    @Test
    void specializedSourcesAreNotProfiledAndUseTheSpecializedTier() throws Exception {
        String src = read(PROFILE);
        assertFalse(src.contains("SWORD") || src.contains("SHEARS"), "swords and shears get no conventional profile");
        String power = read("src/main/java/zcylas/totality/api/mining/PlayerMiningPower.java");
        assertTrue(power.contains("? MiningTier.specializedTier(held, state)"));
        String tier = read("src/main/java/zcylas/totality/api/mining/MiningTier.java");
        assertTrue(tier.contains("return tool.isCorrectToolForDrops(state) ? Math.max(probed, BlockProfiles.resolveStatic(state).requiredTier()) : probed;"),
                "only the blocks a specialized tool's own vanilla rules are correct for lift its Tier gate");
    }

    @Test
    void hoeEffectivenessUsesTheSemanticHoeTags() throws Exception {
        String src = read("src/main/java/zcylas/totality/api/mining/TargetEffectiveness.java");
        assertTrue(src.contains("ItemTags.HOES") && src.contains("BlockTags.MINEABLE_WITH_HOE"));
        assertTrue(BlockProfile.ToolAffinity.of(BlockProfile.Tool.HOE).effective(BlockProfile.Tool.HOE));
        assertFalse(BlockProfile.ToolAffinity.of(BlockProfile.Tool.HOE).effective(BlockProfile.Tool.PICKAXE));
    }

    @Test
    void tierProgressionKeepsDiamondAndNetheriteAtFourAndReservesFive() throws Exception {
        String tuning = read("src/main/java/zcylas/totality/api/mining/MiningTuning.java");
        assertTrue(tuning.contains("public static final int TIER_MAX = 5;"));
        String tier = read("src/main/java/zcylas/totality/api/mining/MiningTier.java");
        assertTrue(tier.contains("if (!tool.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) return 3;\n        return 4;"),
                "the vanilla-derived probe tops out at 4 for both Diamond and Netherite; 5 stays reserved");
    }
}
