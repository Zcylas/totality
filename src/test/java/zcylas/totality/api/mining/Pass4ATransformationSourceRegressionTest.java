package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block Breaking V2 Pass 4A wiring: every accepted in-place transformation is a vanilla method wrapped whole in
 * {@code BlockDamageStorage.transaction}; pairs come only from vanilla's own tables (plus the transcribed tilling and
 * reversion pairs); Pass 4B families are not registered. Behaviour is verified live (MiningVerification) and in a real
 * client world (BlockBreakingPass4AClientGameTest).
 */
class Pass4ATransformationSourceRegressionTest {

    private static String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    private static final String MIXINS = "src/main/java/zcylas/totality/mixin/mining/";

    @Test
    void everyTransformationMixinIsRegisteredAsACommonMixin() throws Exception {
        String config = read("src/main/resources/totality.mixins.json");
        int common = config.indexOf("\"mixins\""), client = config.indexOf("\"client\"");
        for (String m : new String[]{"AxeItemAccessor", "ShovelItemAccessor", "ItemUseTransformationMixin", "WeatheringTransformationMixin",
                "FarmlandReversionMixin", "DispenserHoneycombTransformationMixin"}) {
            int at = config.indexOf("\"mining." + m + "\"");
            assertTrue(at > common && at < client, m + " must be a common mixin");
        }
    }

    @Test
    void eachHookWrapsTheWholeVanillaMethodInTheTransaction() throws Exception {
        String items = read(MIXINS + "ItemUseTransformationMixin.java");
        assertTrue(items.contains("@Mixin({AxeItem.class, HoneycombItem.class, HoeItem.class, ShovelItem.class})"));
        assertTrue(items.contains("@WrapMethod(method = \"useOn\")"));
        assertTrue(items.contains("BlockDamageStorage.transaction(level, context.getClickedPos(), () -> original.call(context))"));
        assertTrue(items.contains("if (!(context.getLevel() instanceof ServerLevel level)) return original.call(context);"), "client side untouched");

        String weathering = read(MIXINS + "WeatheringTransformationMixin.java");
        assertTrue(weathering.contains("@WrapMethod(method = \"randomTick\")"));
        for (String c : new String[]{"WeatheringCopperFullBlock", "WeatheringCopperStairBlock", "WeatheringCopperSlabBlock", "WeatheringCopperDoorBlock",
                "WeatheringCopperTrapDoorBlock", "WeatheringCopperGrateBlock", "WeatheringCopperBulbBlock", "WeatheringCopperBarsBlock",
                "WeatheringCopperChainBlock", "WeatheringCopperChestBlock", "WeatheringCopperGolemStatueBlock", "WeatheringLanternBlock",
                "WeatheringLightningRodBlock"}) {
            assertTrue(weathering.contains(c + ".class"), c + " (all 13 WeatheringCopper implementations)");
        }
        assertTrue(read(MIXINS + "FarmlandReversionMixin.java").contains("@WrapMethod(method = \"turnToDirt\")"));
        String dispenser = read(MIXINS + "DispenserHoneycombTransformationMixin.java");
        assertTrue(dispenser.contains("targets = \"net.minecraft.core.dispenser.DispenseItemBehavior$12\"") && dispenser.contains("@WrapMethod(method = \"execute\")"));
    }

    @Test
    void pairsComeFromVanillaTables() throws Exception {
        // (Pass 4B now registers Concrete/Anvil/Sponge/Coral/Cauldron pairs deliberately; see Pass4BTransformationSourceRegressionTest.)
        String src = read("src/main/java/zcylas/totality/api/mining/VanillaTransformations.java");
        for (String table : new String[]{"AxeItemAccessor.totality$getStrippables()", "WeatheringCopper.NEXT_BY_BLOCK.get()",
                "WeatheringCopper.PREVIOUS_BY_BLOCK.get()", "HoneycombItem.WAXABLES.get()", "HoneycombItem.WAX_OFF_BY_BLOCK.get()",
                "ShovelItemAccessor.totality$getFlattenables()"}) {
            assertTrue(src.contains(table), table);
        }
        assertTrue(src.contains("equals(\"minecraft\")"), "vanilla ids only");
    }
}
