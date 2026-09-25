package zcylas.totality.api.mining;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;

import java.util.List;
import zcylas.totality.mixin.mining.AxeItemAccessor;
import zcylas.totality.mixin.mining.ShovelItemAccessor;

/**
 * The accepted in-place physical transformations (Block Breaking V2, Pass 4A), registered as explicit
 * {@link BlockProfiles#transformation} pairs taken from vanilla's OWN transformation tables, vanilla ids only. A pair is
 * only ever consulted inside {@link BlockDamageStorage#transaction}, which the transformation mixins wrap around the
 * exact vanilla mutation (never by a lazy id comparison):
 * <ul>
 *   <li>wood stripping ({@code AxeItem.STRIPPABLES}, Overworld and Nether);</li>
 *   <li>copper weathering and axe scraping ({@code WeatheringCopper.NEXT_BY_BLOCK} / {@code PREVIOUS_BY_BLOCK}), honeycomb
 *       waxing and axe wax removal ({@code HoneycombItem.WAXABLES} / {@code WAX_OFF_BY_BLOCK}) for every copper form;</li>
 *   <li>soil: flattening ({@code ShovelItem.FLATTENABLES}), tilling (HoeItem's {@code TILLABLES}; its targets live in
 *       opaque lambdas, so they are transcribed from the verified 26.2 definition), and Farmland/Path reversion to Dirt
 *       ({@code FarmlandBlock.turnToDirt}).</li>
 * </ul>
 * <p>Pass 4B adds: Concrete Powder solidifying in place ({@code ConcretePowderBlock.updateShape}), Anvil use
 * degradation ({@code AnvilMenu.onTake}; {@code AnvilBlock.damage}), Sponge absorbing water ({@code SpongeBlock.tryAbsorbWater}),
 * full Coral Blocks dying ({@code CoralBlock.tick}), Cauldron contents (empty / water / lava / powder snow), lightning
 * deoxidation ({@code WeatheringCopper.getFirst} on the struck block), and Grass/Mycelium decay to and spread onto Dirt.
 */
public final class VanillaTransformations {

    private static int registered;

    private VanillaTransformations() {}

    /** Number of pairs registered (verification). */
    public static int registeredPairs() { return registered; }

    public static void register() {
        AxeItemAccessor.totality$getStrippables().forEach(VanillaTransformations::pair);
        WeatheringCopper.NEXT_BY_BLOCK.get().forEach(VanillaTransformations::pair);
        WeatheringCopper.PREVIOUS_BY_BLOCK.get().forEach(VanillaTransformations::pair);
        HoneycombItem.WAXABLES.get().forEach(VanillaTransformations::pair);
        HoneycombItem.WAX_OFF_BY_BLOCK.get().forEach(VanillaTransformations::pair);
        ShovelItemAccessor.totality$getFlattenables().forEach((from, to) -> pair(from, to.getBlock()));
        // HoeItem.TILLABLES (26.2): grass/path/dirt -> farmland, coarse dirt -> dirt, rooted dirt -> dirt (+ hanging roots drop).
        pair(Blocks.GRASS_BLOCK, Blocks.FARMLAND);
        pair(Blocks.DIRT_PATH, Blocks.FARMLAND);
        pair(Blocks.DIRT, Blocks.FARMLAND);
        pair(Blocks.COARSE_DIRT, Blocks.DIRT);
        pair(Blocks.ROOTED_DIRT, Blocks.DIRT);
        // FarmlandBlock.turnToDirt: trampled/dried/covered Farmland and covered Dirt Path revert to Dirt.
        pair(Blocks.FARMLAND, Blocks.DIRT);
        pair(Blocks.DIRT_PATH, Blocks.DIRT);

        // ---- Pass 4B ----
        for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
            pair(Blocks.CONCRETE_POWDER.pick(color), Blocks.CONCRETE.pick(color));                 // in-place solidification
        }
        pair(Blocks.ANVIL, Blocks.CHIPPED_ANVIL);                                                    // AnvilBlock.damage
        pair(Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL);
        pair(Blocks.SPONGE, Blocks.WET_SPONGE);                                                      // SpongeBlock.tryAbsorbWater
        for (String coral : new String[]{"tube", "brain", "bubble", "fire", "horn"}) {             // CoralBlock.tick
            pair(block(coral + "_coral_block"), block("dead_" + coral + "_coral_block"));
        }
        List<Block> cauldrons = List.of(Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.LAVA_CAULDRON, Blocks.POWDER_SNOW_CAULDRON);
        for (Block from : cauldrons) for (Block to : cauldrons) if (from != to) pair(from, to);     // contents, same 150 max
        for (Block weathered : WeatheringCopper.PREVIOUS_BY_BLOCK.get().keySet()) {                 // lightning: straight to unaffected
            pair(weathered, WeatheringCopper.getFirst(weathered));
        }
        pair(Blocks.GRASS_BLOCK, Blocks.DIRT);                                                       // SpreadingSnowyBlock decay
        pair(Blocks.MYCELIUM, Blocks.DIRT);
        pair(Blocks.DIRT, Blocks.GRASS_BLOCK);                                                       // spread onto Dirt
        pair(Blocks.DIRT, Blocks.MYCELIUM);
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));
    }

    private static void pair(Block from, Block to) {
        if (from == to) return;
        if (!BuiltInRegistries.BLOCK.getKey(from).getNamespace().equals("minecraft")
                || !BuiltInRegistries.BLOCK.getKey(to).getNamespace().equals("minecraft")) return;
        if (!BlockProfiles.transformsTo(from, to)) registered++;
        BlockProfiles.transformation(from, to);
    }
}
