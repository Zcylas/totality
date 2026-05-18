package zcylas.totality.api.rpg.skills.mining;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines how much Mining XP each block gives when broken.
 *
 * Priority-ordered — first matching entry wins.
 * Specific blocks listed before tags so they can override tag defaults.
 *
 * To add a new block: add a block() or tag() call in the static block.
 * Order matters — put more specific entries before broader ones.
 */
public final class MiningXpTable {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        // ── Specific blocks (highest priority) ───────────────────────────────
        block(Blocks.ANCIENT_DEBRIS,            50);

        block(Blocks.DIAMOND_ORE,               30);
        block(Blocks.DEEPSLATE_DIAMOND_ORE,     30);
        block(Blocks.EMERALD_ORE,               30);
        block(Blocks.DEEPSLATE_EMERALD_ORE,     30);

        block(Blocks.GOLD_ORE,                  20);
        block(Blocks.DEEPSLATE_GOLD_ORE,        20);
        block(Blocks.NETHER_GOLD_ORE,           20);
        block(Blocks.REDSTONE_ORE,              20);
        block(Blocks.DEEPSLATE_REDSTONE_ORE,    20);
        block(Blocks.LAPIS_ORE,                 20);
        block(Blocks.DEEPSLATE_LAPIS_ORE,       20);

        block(Blocks.IRON_ORE,                  15);
        block(Blocks.DEEPSLATE_IRON_ORE,        15);
        block(Blocks.COPPER_ORE,                15);
        block(Blocks.DEEPSLATE_COPPER_ORE,      15);

        block(Blocks.COAL_ORE,                  10);
        block(Blocks.DEEPSLATE_COAL_ORE,        10);
        block(Blocks.NETHER_QUARTZ_ORE,         10);
        block(Blocks.AMETHYST_CLUSTER,          10);
        block(Blocks.LARGE_AMETHYST_BUD,         7);
        block(Blocks.MEDIUM_AMETHYST_BUD,        5);
        block(Blocks.SMALL_AMETHYST_BUD,         3);

        // ── Tag-based catch-alls for modded ores ──────────────────────────────
        tag(BlockTags.DIAMOND_ORES,             30);
        tag(BlockTags.EMERALD_ORES,             30);
        tag(BlockTags.GOLD_ORES,                20);
        tag(BlockTags.REDSTONE_ORES,            20);
        tag(BlockTags.LAPIS_ORES,               20);
        tag(BlockTags.IRON_ORES,                15);
        tag(BlockTags.COPPER_ORES,              15);
        tag(BlockTags.COAL_ORES,                10);
        tag(BlockTags.STONE_ORE_REPLACEABLES,    5);
        tag(BlockTags.DEEPSLATE_ORE_REPLACEABLES, 5);

        // ── Stone-type blocks ─────────────────────────────────────────────────
        block(Blocks.STONE,                      3);
        block(Blocks.COBBLESTONE,                2);
        block(Blocks.MOSSY_COBBLESTONE,          2);
        block(Blocks.DEEPSLATE,                  3);
        block(Blocks.COBBLED_DEEPSLATE,          2);
        block(Blocks.BLACKSTONE,                 3);
        block(Blocks.BASALT,                     2);
        block(Blocks.SMOOTH_BASALT,              2);
        block(Blocks.GRANITE,                    3);
        block(Blocks.DIORITE,                    3);
        block(Blocks.ANDESITE,                   3);
        block(Blocks.CALCITE,                    2);
        block(Blocks.TUFF,                       2);
        block(Blocks.DRIPSTONE_BLOCK,            2);
        block(Blocks.POINTED_DRIPSTONE,          2);
        block(Blocks.SANDSTONE,                  2);
        block(Blocks.RED_SANDSTONE,              2);
        block(Blocks.GRAVEL,                     1);
        block(Blocks.NETHERRACK,                 2);
        block(Blocks.END_STONE,                  3);
        block(Blocks.OBSIDIAN,                   5);
        block(Blocks.CRYING_OBSIDIAN,            5);

        tag(BlockTags.BASE_STONE_OVERWORLD,      3);
        tag(BlockTags.BASE_STONE_NETHER,         2);

        // ── Dirt/grass — 1 XP each ────────────────────────────────────────────
        // Remove these if you prefer dirt/grass to give 0 XP,
        // or move them to a future Farming/Nature skill.
        block(Blocks.DIRT,                       1);
        block(Blocks.GRASS_BLOCK,                1);
        block(Blocks.ROOTED_DIRT,                1);
        block(Blocks.PODZOL,                     1);
        block(Blocks.MYCELIUM,                   1);
        block(Blocks.COARSE_DIRT,                1);
        block(Blocks.MUD,                        1);
        block(Blocks.CLAY,                       2);
        block(Blocks.SAND,                       1);
        block(Blocks.RED_SAND,                   1);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the Mining XP for breaking this block state.
     * Returns 0 if the block is not in the table.
     */
    public static int getXp(BlockState state) {
        for (Entry entry : ENTRIES) {
            if (entry.matches(state)) return entry.getXp();
        }
        return 0;
    }

    // ── Internal entry class ──────────────────────────────────────────────────

    private static final class Entry {
        private final Block         block; // null if tag-based
        private final TagKey<Block> tag;   // null if block-based
        private final int           xp;

        private Entry(Block block, TagKey<Block> tag, int xp) {
            this.block = block;
            this.tag   = tag;
            this.xp    = xp;
        }

        boolean matches(BlockState state) {
            if (block != null) return state.is(block);
            if (tag   != null) return state.is(tag);
            return false;
        }

        int getXp() { return xp; }
    }

    private static void block(Block block, int xp) {
        ENTRIES.add(new Entry(block, null, xp));
    }

    private static void tag(TagKey<Block> tag, int xp) {
        ENTRIES.add(new Entry(null, tag, xp));
    }

    private MiningXpTable() {}
}