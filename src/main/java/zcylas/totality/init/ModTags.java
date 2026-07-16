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
    /** Ordinary general goods the first Provisioner-style merchant will buy from a player
     *  (Economy Pricing and Provisioner doc, Section 5) — deliberately excludes tools, weapons,
     *  armor, potions, enchanted equipment, and anything meant for a future Blacksmith. */
    public static final TagKey<Item> PROVISIONER_BUYS = item("provisioner_buys");
    //Block Tags
    public static final TagKey<Block> HARVESTABLE = block("harvestable");
    public static final TagKey<Block> VEINMINABLE = block("veinminable");
    // Biome Tags
    /** Forward-looking, not narrowed to "forest" — Flooded Forest is the first member, future flooded biomes share it. */
    public static final TagKey<Biome> IS_FLOODED_BIOME = biome("is_flooded_biome");

    /** MC 26.2 removed these as BlockTags constants, but the vanilla data tags (data/minecraft/tags/block/*.json)
     *  still ship in the jar — reference them directly by id instead of hardcoding block lists. */
    public static final TagKey<Block> VANILLA_COAL_ORES      = vanillaBlock("coal_ores");
    public static final TagKey<Block> VANILLA_DIAMOND_ORES   = vanillaBlock("diamond_ores");
    public static final TagKey<Block> VANILLA_EMERALD_ORES    = vanillaBlock("emerald_ores");
    public static final TagKey<Block> VANILLA_LAPIS_ORES      = vanillaBlock("lapis_ores");
    public static final TagKey<Block> VANILLA_REDSTONE_ORES   = vanillaBlock("redstone_ores");
    public static final TagKey<Block> VANILLA_LOGS_THAT_BURN  = vanillaBlock("logs_that_burn");

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("totality", path));
    }
    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("totality", path));
    }
    private static TagKey<Block> vanillaBlock(String path) {
        return TagKey.create(Registries.BLOCK,
                Identifier.withDefaultNamespace(path));
    }
    private static TagKey<Biome> biome(String path) {
        return TagKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath("totality", path));
    }
    private ModTags() {}
}