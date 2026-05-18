package zcylas.totality.api.rpg.skills.mining;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Defines which gem items can drop when Prospector mastery is active.
 *
 * Each entry maps a block (or tag) to a gem item with a drop chance (0.0–1.0).
 * Multiple gems can be registered for the same block — all are rolled independently.
 *
 * To add a new gem in the future:
 *   gemDrop(Blocks.YOUR_ORE, ModItems.YOUR_GEM, 0.05f);
 *   or
 *   tagDrop(ModTags.YOUR_ORE_TAG, ModItems.YOUR_GEM, 0.10f);
 *
 * Drop chance reference:
 *   0.05f =  5% — ultra rare (custom gems, special ores)
 *   0.10f = 10% — rare      (diamond, emerald)
 *   0.20f = 20% — uncommon  (vanilla ores)
 *   0.35f = 35% — common    (stone-type blocks)
 */
public final class ProspectorGemTable {

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Random RNG = new Random();

    static {
        // ── Diamond ore → Diamond ─────────────────────────────────────────────
        gemDrop(Blocks.DIAMOND_ORE,           Items.DIAMOND,  0.10f);
        gemDrop(Blocks.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND,  0.10f);

        // ── Emerald ore → Emerald ─────────────────────────────────────────────
        gemDrop(Blocks.EMERALD_ORE,           Items.EMERALD,  0.10f);
        gemDrop(Blocks.DEEPSLATE_EMERALD_ORE, Items.EMERALD,  0.10f);

        // ── Amethyst → Amethyst Shard ─────────────────────────────────────────
        gemDrop(Blocks.AMETHYST_CLUSTER,      Items.AMETHYST_SHARD, 0.20f);

        // ── Any diamond ore (catches modded) ──────────────────────────────────
        tagDrop(BlockTags.DIAMOND_ORES,       Items.DIAMOND,  0.10f);
        tagDrop(BlockTags.EMERALD_ORES,       Items.EMERALD,  0.10f);

        // ── Custom gems — add yours here as they are registered ───────────────
        // Example:
        // gemDrop(ModBlocks.RUBY_ORE,        ModItems.RUBY,  0.07f);
        // gemDrop(ModBlocks.SAPPHIRE_ORE,    ModItems.SAPPHIRE, 0.07f);
        //
        // You can also add vanilla items as "gem-like" drops from stone:
        // gemDrop(Blocks.STONE,              Items.QUARTZ,   0.02f);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Rolls all matching entries for this block state and returns a list of
     * gem items that should be dropped. Empty list = no bonus drops.
     *
     * Call this server-side in MiningSkillEvents when Prospector is active.
     */
    public static List<Item> rollDrops(BlockState state) {
        List<Item> drops = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.matches(state) && RNG.nextFloat() < entry.chance) {
                drops.add(entry.gem);
            }
        }
        return drops;
    }

    // ── Entry ─────────────────────────────────────────────────────────────────

    private static final class Entry {
        private final Block         block;  // null if tag-based
        private final TagKey<Block> tag;    // null if block-based
        private final Item          gem;
        private final float         chance; // 0.0 – 1.0

        Entry(Block block, TagKey<Block> tag, Item gem, float chance) {
            this.block  = block;
            this.tag    = tag;
            this.gem    = gem;
            this.chance = chance;
        }

        boolean matches(BlockState state) {
            if (block != null) return state.is(block);
            if (tag   != null) return state.is(tag);
            return false;
        }
    }

    private static void gemDrop(Block block, Item gem, float chance) {
        ENTRIES.add(new Entry(block, null, gem, chance));
    }

    private static void tagDrop(TagKey<Block> tag, Item gem, float chance) {
        ENTRIES.add(new Entry(null, tag, gem, chance));
    }

    private ProspectorGemTable() {}
}