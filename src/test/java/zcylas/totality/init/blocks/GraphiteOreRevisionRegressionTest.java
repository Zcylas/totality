package zcylas.totality.init.blocks;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creative Test E1, the Graphite Ore visual revision: only the two block textures change; the blocks stay ordinary
 * full cubes (cube_all, one texture on all six faces) with their existing registration, drops and world generation.
 */
class GraphiteOreRevisionRegressionTest {

    private static final Path RES = Path.of("src/main/resources/assets/totality");
    private static final Path GEN = Path.of("src/main/generated");

    private static BufferedImage texture(String name) throws Exception {
        return ImageIO.read(RES.resolve("textures/block/" + name + ".png").toFile());
    }

    @Test
    void bothVariantsAreSixteenPixelOpaqueCubeAllTextures() throws Exception {
        for (String name : new String[]{"graphite_ore", "deepslate_graphite_ore"}) {
            BufferedImage im = texture(name);
            assertEquals(16, im.getWidth(), name);
            assertEquals(16, im.getHeight(), name);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) assertEquals(0xFF, im.getRGB(x, y) >>> 24, name + " opaque");
            String model = Files.readString(GEN.resolve("assets/totality/models/block/" + name + ".json"));
            assertTrue(model.contains("\"parent\": \"minecraft:block/cube_all\"") && model.contains("\"all\": \"totality:block/" + name + "\""));
        }
    }

    @Test
    void theDeepslateVariantIsNotAUniformlyDarkenedCopy() throws Exception {
        BufferedImage stone = texture("graphite_ore");
        BufferedImage deep = texture("deepslate_graphite_ore");
        int deposits = 0, sameDeposit = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int s = stone.getRGB(x, y) & 0xFFFFFF;
                if (luminance(s) < 0x40) {
                    deposits++;
                    if (luminance(deep.getRGB(x, y) & 0xFFFFFF) < 0x40 || luminance(deep.getRGB(x, y) & 0xFFFFFF) > 0x50) sameDeposit++;
                }
            }
        }
        assertTrue(deposits >= 40, "the reference's two large deposits plus flecks: " + deposits);
        assertEquals(deposits, sameDeposit, "deposits share one layout, each still distinct from the deepslate base");
        assertNotEquals(stone.getRGB(0, 0), deep.getRGB(0, 0));
    }

    @Test
    void gameplayRegistrationDropsAndWorldgenAreUnchanged() throws Exception {
        String ores = Files.readString(Path.of("src/main/java/zcylas/totality/init/blocks/OreBlocks.java"));
        assertTrue(ores.contains("registerBlock(\"graphite_ore\", Block::new") && ores.contains("registerBlock(\"deepslate_graphite_ore\", Block::new"));
        assertTrue(Files.readString(GEN.resolve("data/totality/loot_table/blocks/graphite_ore.json")).contains("\"totality:graphite\""));
        assertTrue(Files.readString(GEN.resolve("data/totality/worldgen/configured_feature/graphite_ore.json")).contains("totality:deepslate_graphite_ore"));
    }

    private static int luminance(int rgb) {
        return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
    }
}
