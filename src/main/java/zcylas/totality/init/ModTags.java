package zcylas.totality.init;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public class ModTags {

    // ── Weapon tags ───────────────────────────────────────────────────────────
    public static final TagKey<Item> ONE_HANDED_WEAPONS = item("one_handed_weapons");
    public static final TagKey<Item> TWO_HANDED_WEAPONS = item("two_handed_weapons");
    public static final TagKey<Item> THROWN_WEAPONS = item("thrown_weapons");
    public static final TagKey<Item> TOOLS = item("tools");
    public static final TagKey<Item> BOWS       = item("bows");
    public static final TagKey<Item> CROSSBOWS  = item("crossbows");
    public static final TagKey<Item> POTIONS  = item("potions");
    public static final TagKey<Item> SPECIAL  = item("special");
    //Block Tags
    public static final TagKey<Block> HARVESTABLE = block("harvestable");
    public static final TagKey<Block> VEINMINABLE = block("veinminable");
    // Biome Tags
    /** Forward-looking, not narrowed to "forest" — Flooded Forest is the first member, future flooded biomes share it. */
    public static final TagKey<Biome> IS_FLOODED_BIOME = biome("is_flooded_biome");

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("totality", path));
    }
    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("totality", path));
    }
    private static TagKey<Biome> biome(String path) {
        return TagKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath("totality", path));
    }
    private ModTags() {}
}