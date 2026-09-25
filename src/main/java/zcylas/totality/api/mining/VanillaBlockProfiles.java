package zcylas.totality.api.mining;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.mining.BlockProfile.Classification;
import zcylas.totality.api.mining.BlockProfile.Form;
import zcylas.totality.api.mining.BlockProfile.Material;
import zcylas.totality.api.mining.BlockProfile.Tool;
import zcylas.totality.api.mining.BlockProfile.ToolAffinity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The accepted Block Breaking V2 vanilla Block/Material Profile dataset (Pass 3), authored from
 * {@code Context/References/TOTALITY_BLOCK_BREAKING_V2_ACCEPTED_VANILLA_LEDGER_2026-09-25.md} plus the later accepted
 * reconciliation delta for the 176 ids that ledger left unresolved ({@link #reconciliation}). Every number here is a
 * ledger value; nothing is derived from vanilla hardness or a geometric form formula. Membership is explicit registry
 * ids (colour/species/oxidation variants only where the ledger row states them) or a vanilla tag whose membership
 * exactly matches the ledger wording. Every id is resolved against the live registry; a missing id is recorded in
 * {@link #missingIds()} and fails verification instead of being skipped silently.
 *
 * <p>Anything the ledger does not cover unambiguously is NOT authored: it keeps the compatibility fallback and is
 * reported {@code UNRESOLVED_DESIGN} by the coverage report ({@link #reviewNote} marks the entries the ledger
 * explicitly leaves for review). Required Mining Tier is never authored here: it stays vanilla-derived.
 */
public final class VanillaBlockProfiles {

    private VanillaBlockProfiles() {}

    // ── authored forms (values are per material; never scaled from the full block) ────────────
    public static final Form SLAB = new Form("slab");
    public static final Form DOUBLE_SLAB = new Form("double_slab");
    public static final Form STAIRS = new Form("stairs");
    public static final Form WALL = new Form("wall");
    public static final Form FENCE = new Form("fence");
    public static final Form FENCE_GATE = new Form("fence_gate");
    public static final Form DOOR = new Form("door");
    public static final Form TRAPDOOR = new Form("trapdoor");
    public static final Form PANE = new Form("pane");
    public static final Form SNOW_LAYER = new Form("snow_layer");
    public static final Form SHELF = new Form("shelf");

    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    /** Ordinary overworld wood species (Crimson/Warped are Nether stems and Bamboo is its own ledger row). */
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "cherry", "dark_oak", "pale_oak", "mangrove");
    private static final List<String> OXIDATION = List.of("", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_",
            "waxed_weathered_", "waxed_oxidized_");

    private static final ToolAffinity PICKAXE = ToolAffinity.of(Tool.PICKAXE);
    private static final ToolAffinity AXE = ToolAffinity.of(Tool.AXE);
    private static final ToolAffinity SHOVEL = ToolAffinity.of(Tool.SHOVEL);
    private static final ToolAffinity HOE = ToolAffinity.of(Tool.HOE);
    private static final ToolAffinity NEUTRAL = ToolAffinity.NEUTRAL;

    // Materials shared between the Pass 3 dataset and its reconciliation delta.
    private static Material crafted_bamboo;
    private static Material sandstoneMaterial;

    private static final List<String> MISSING = new ArrayList<>();
    private static final Map<Block, String> REVIEW = new HashMap<>();
    private static final Map<Block, String> NOTES = new HashMap<>();

    /** Ledger ids that do not exist in the live registry (must stay empty). */
    public static List<String> missingIds() { return Collections.unmodifiableList(MISSING); }

    /** Design note for a block explicitly parked for review (reported UNRESOLVED_DESIGN), or null. None are parked after the Pass 3 reconciliation. */
    @Nullable
    static String reviewNote(Block block) { return REVIEW.get(block); }

    /** Caveat recorded against an authored block (reported in the coverage issue column), or null. */
    @Nullable
    static String note(Block block) { return NOTES.get(block); }

    public static void register() {
        stone();
        wood();
        earthAndSediment();
        masonry();
        glassIceSnow();
        nether();
        endAndPurpur();
        ores();
        metalAndUtility();
        workstationsAndContainers();
        lightingAndCircuitry();
        organic();
        concreteQuartzPrismarine();
        caveAndDeep();
        functionalAndEncounter();
        special();
        reconciliation();
        unbreakable();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    @Nullable
    private static Block block(String id) {
        Identifier key = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
        var block = BuiltInRegistries.BLOCK.getOptional(key);
        if (block.isEmpty()) {
            MISSING.add(key.toString());
            return null;
        }
        return block.get();
    }

    private static Material material(String id, ToolAffinity tools, float full) {
        Material m = new Material("totality:" + id);
        BlockProfiles.material(m, BlockProfile.EMPTY.withClassification(Classification.ORDINARY)
                .withMaxDurability(full).withTools(tools));
        BlockProfiles.materialForm(m, Form.FULL_BLOCK, BlockProfile.durability(full));
        BlockProfiles.materialForm(m, DOUBLE_SLAB, BlockProfile.durability(full));
        return m;
    }

    /** Material with no ledger tool of its own: its tools stay vanilla-derived (fallback). */
    private static Material materialVanillaTool(String id, float full) {
        Material m = new Material("totality:" + id);
        BlockProfiles.material(m, BlockProfile.EMPTY.withClassification(Classification.ORDINARY).withMaxDurability(full));
        BlockProfiles.materialForm(m, Form.FULL_BLOCK, BlockProfile.durability(full));
        BlockProfiles.materialForm(m, DOUBLE_SLAB, BlockProfile.durability(full));
        return m;
    }

    private static void form(Material m, Form form, float value) { BlockProfiles.materialForm(m, form, BlockProfile.durability(value)); }

    private static void members(Material m, Form form, String... ids) {
        for (String id : ids) {
            Block b = block(id);
            if (b != null) BlockProfiles.assign(b, m, form);
        }
    }

    private static void members(Material m, Form form, List<String> ids) { members(m, form, ids.toArray(String[]::new)); }

    /** Single slabs of {@code m}; their double state switches to the material's authored double-slab value. */
    private static void slabs(Material m, String... ids) {
        for (String id : ids) {
            Block b = block(id);
            if (b == null) continue;
            BlockProfiles.assign(b, m, SLAB);
            BlockProfiles.stateOverride(b, s -> s.hasProperty(SlabBlock.TYPE) && s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
                    BlockProfile.EMPTY.withMaterial(m, DOUBLE_SLAB));
        }
    }

    private static void slabs(Material m, List<String> ids) { slabs(m, ids.toArray(String[]::new)); }

    private static void tag(Material m, Form form, TagKey<Block> tag) { BlockProfiles.assign(tag, m, form); }

    /** Exact block profile (exceptions and single-block ledger entries). */
    private static void exact(BlockProfile profile, String... ids) {
        for (String id : ids) {
            Block b = block(id);
            if (b != null) BlockProfiles.block(b, profile);
        }
    }

    private static void exact(BlockProfile profile, List<String> ids) { exact(profile, ids.toArray(String[]::new)); }

    private static BlockProfile ordinary(float hp, @Nullable ToolAffinity tools) {
        BlockProfile p = BlockProfile.EMPTY.withClassification(Classification.ORDINARY).withMaxDurability(hp);
        return tools == null ? p : p.withTools(tools);
    }

    private static void special(String... ids) { exact(BlockProfile.EMPTY.withClassification(Classification.SPECIAL), ids); }

    private static void special(List<String> ids) { special(ids.toArray(String[]::new)); }

    /**
     * Tag-backed SPECIAL group. Tags are not bound at mod initialisation, so membership is a lazily evaluated group
     * assignment to a SPECIAL-classified material (exact block profiles still take precedence).
     */
    private static void specialTag(TagKey<Block> tag) {
        Material m = new Material("totality:special/" + tag.location().getPath());
        BlockProfiles.material(m, BlockProfile.EMPTY.withClassification(Classification.SPECIAL));
        BlockProfiles.assign(tag, m, Form.FULL_BLOCK);
    }

    private static void note(String note, String... ids) {
        for (String id : ids) {
            Block b = block(id);
            if (b != null) NOTES.put(b, note);
        }
    }


    private static List<String> each(List<String> variants, Function<String, String> id) { return variants.stream().map(id).toList(); }

    // ── ledger rows ──────────────────────────────────────────────────────────────────────────

    /** Ordinary Stone: full 100; slab 50 / double 100; stairs & walls 75; Cracked Stone Bricks 75. Pickaxe. */
    private static void stone() {
        Material stone = material("ordinary_stone", PICKAXE, 100f);
        form(stone, SLAB, 50f);
        form(stone, STAIRS, 75f);
        form(stone, WALL, 75f);
        members(stone, Form.FULL_BLOCK, "stone", "cobblestone", "mossy_cobblestone", "andesite", "polished_andesite", "diorite",
                "polished_diorite", "granite", "polished_granite", "smooth_stone", "stone_bricks", "mossy_stone_bricks",
                "chiseled_stone_bricks", "cracked_stone_bricks");
        exact(ordinary(75f, null), "cracked_stone_bricks");
        slabs(stone, "stone_slab", "smooth_stone_slab", "cobblestone_slab", "mossy_cobblestone_slab", "stone_brick_slab",
                "mossy_stone_brick_slab", "andesite_slab", "polished_andesite_slab", "diorite_slab", "polished_diorite_slab",
                "granite_slab", "polished_granite_slab");
        members(stone, STAIRS, "stone_stairs", "cobblestone_stairs", "mossy_cobblestone_stairs", "stone_brick_stairs",
                "mossy_stone_brick_stairs", "andesite_stairs", "polished_andesite_stairs", "diorite_stairs",
                "polished_diorite_stairs", "granite_stairs", "polished_granite_stairs");
        members(stone, WALL, "cobblestone_wall", "mossy_cobblestone_wall", "stone_brick_wall", "mossy_stone_brick_wall",
                "andesite_wall", "diorite_wall", "granite_wall");

        // Infested host variants: host values; Silverfish release and loot stay vanilla.
        exact(ordinary(100f, PICKAXE), "infested_stone", "infested_cobblestone", "infested_stone_bricks",
                "infested_mossy_stone_bricks", "infested_chiseled_stone_bricks");
        exact(ordinary(75f, PICKAXE), "infested_cracked_stone_bricks");
        exact(ordinary(150f, PICKAXE), "infested_deepslate");

        // Deepslate: full 150; slab 75 / double 150; stairs & walls 115; cracked bricks/tiles 115. Pickaxe.
        Material deepslate = material("deepslate", PICKAXE, 150f);
        form(deepslate, SLAB, 75f);
        form(deepslate, STAIRS, 115f);
        form(deepslate, WALL, 115f);
        members(deepslate, Form.FULL_BLOCK, "deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks",
                "deepslate_tiles", "chiseled_deepslate", "cracked_deepslate_bricks", "cracked_deepslate_tiles");
        exact(ordinary(115f, null), "cracked_deepslate_bricks", "cracked_deepslate_tiles");
        slabs(deepslate, "cobbled_deepslate_slab", "polished_deepslate_slab", "deepslate_brick_slab", "deepslate_tile_slab");
        members(deepslate, STAIRS, "cobbled_deepslate_stairs", "polished_deepslate_stairs", "deepslate_brick_stairs", "deepslate_tile_stairs");
        members(deepslate, WALL, "cobbled_deepslate_wall", "polished_deepslate_wall", "deepslate_brick_wall", "deepslate_tile_wall");

        // Tuff: full 120; slab 60 / double 120; stairs & walls 90. Pickaxe.
        Material tuff = material("tuff", PICKAXE, 120f);
        form(tuff, SLAB, 60f);
        form(tuff, STAIRS, 90f);
        form(tuff, WALL, 90f);
        members(tuff, Form.FULL_BLOCK, "tuff", "polished_tuff", "chiseled_tuff", "tuff_bricks", "chiseled_tuff_bricks");
        slabs(tuff, "tuff_slab", "polished_tuff_slab", "tuff_brick_slab");
        members(tuff, STAIRS, "tuff_stairs", "polished_tuff_stairs", "tuff_brick_stairs");
        members(tuff, WALL, "tuff_wall", "polished_tuff_wall", "tuff_brick_wall");

        // Basalt 150; Calcite / Dripstone Block 100. Pickaxe.
        members(material("basalt", PICKAXE, 150f), Form.FULL_BLOCK, "basalt", "polished_basalt", "smooth_basalt");
        members(material("calcite_dripstone", PICKAXE, 100f), Form.FULL_BLOCK, "calcite", "dripstone_block");
        exact(ordinary(1000f, PICKAXE), "reinforced_deepslate");
        note("destructible but non-obtainable (vanilla loot)", "reinforced_deepslate");
    }

    /** Ordinary overworld Wood and Crafted Bamboo: Axe. */
    private static void wood() {
        Material wood = BlockDurabilityDefinitions.OVERWORLD_LOG;   // logs/wood/stripped (#logs_that_burn) + building forms
        BlockProfiles.material(wood, BlockProfile.EMPTY.withClassification(Classification.ORDINARY).withMaxDurability(100f).withTools(AXE));
        BlockProfiles.materialForm(wood, Form.FULL_BLOCK, BlockProfile.durability(100f));
        BlockProfiles.materialForm(wood, DOUBLE_SLAB, BlockProfile.durability(100f));
        form(wood, SLAB, 50f);
        form(wood, STAIRS, 75f);
        form(wood, FENCE, 50f);
        form(wood, FENCE_GATE, 75f);
        form(wood, DOOR, 100f);          // whole 2-block door, one shared owner record
        form(wood, TRAPDOOR, 50f);
        members(wood, Form.FULL_BLOCK, each(WOODS, w -> w + "_planks"));
        slabs(wood, each(WOODS, w -> w + "_slab"));
        members(wood, STAIRS, each(WOODS, w -> w + "_stairs"));
        members(wood, FENCE, each(WOODS, w -> w + "_fence"));
        members(wood, FENCE_GATE, each(WOODS, w -> w + "_fence_gate"));
        members(wood, DOOR, each(WOODS, w -> w + "_door"));
        members(wood, TRAPDOOR, each(WOODS, w -> w + "_trapdoor"));

        Material bamboo = material("crafted_bamboo", AXE, 100f);
        crafted_bamboo = bamboo;
        form(bamboo, SLAB, 50f);
        form(bamboo, STAIRS, 75f);
        form(bamboo, FENCE, 50f);
        form(bamboo, FENCE_GATE, 75f);
        form(bamboo, DOOR, 100f);
        form(bamboo, TRAPDOOR, 50f);
        members(bamboo, Form.FULL_BLOCK, "bamboo_block", "stripped_bamboo_block", "bamboo_planks", "bamboo_mosaic");
        slabs(bamboo, "bamboo_slab", "bamboo_mosaic_slab");
        members(bamboo, STAIRS, "bamboo_stairs", "bamboo_mosaic_stairs");
        members(bamboo, FENCE, "bamboo_fence");
        members(bamboo, FENCE_GATE, "bamboo_fence_gate");
        members(bamboo, DOOR, "bamboo_door");
        members(bamboo, TRAPDOOR, "bamboo_trapdoor");

        // Hanging / wall hanging signs 25 (all species); standing/wall signs are SPECIAL (see special()).
        Material hanging = materialVanillaTool("hanging_sign", 25f);
        tag(hanging, Form.FULL_BLOCK, BlockTags.CEILING_HANGING_SIGNS);
        tag(hanging, Form.FULL_BLOCK, BlockTags.WALL_HANGING_SIGNS);
        exact(ordinary(25f, AXE), "ladder");
        // Giant mushrooms: caps 50, stems 75. Axe.
        exact(ordinary(50f, AXE), "brown_mushroom_block", "red_mushroom_block");
        exact(ordinary(75f, AXE), "mushroom_stem");
    }

    private static void earthAndSediment() {
        members(material("ordinary_dirt", SHOVEL, 100f), Form.FULL_BLOCK, "dirt", "grass_block");
        members(material("sand_gravel", SHOVEL, 75f), Form.FULL_BLOCK, "sand", "red_sand", "gravel");
        members(material("clay_mud", SHOVEL, 100f), Form.FULL_BLOCK, "clay", "mud");

        Material mudBrick = material("packed_mud_bricks", PICKAXE, 120f);
        form(mudBrick, SLAB, 60f);
        form(mudBrick, STAIRS, 90f);
        form(mudBrick, WALL, 90f);
        members(mudBrick, Form.FULL_BLOCK, "packed_mud", "mud_bricks");
        slabs(mudBrick, "mud_brick_slab");
        members(mudBrick, STAIRS, "mud_brick_stairs");
        members(mudBrick, WALL, "mud_brick_wall");

        exact(ordinary(75f, SHOVEL), "soul_sand");
        exact(ordinary(100f, SHOVEL), "soul_soil");
    }

    private static void masonry() {
        // Sandstone / Red Sandstone (normal, smooth, cut, chiseled): full 100; slab 50 / double 100; stairs 75. Pickaxe.
        Material sandstone = material("sandstone", PICKAXE, 100f);
        sandstoneMaterial = sandstone;
        form(sandstone, SLAB, 50f);
        form(sandstone, STAIRS, 75f);
        members(sandstone, Form.FULL_BLOCK, "sandstone", "smooth_sandstone", "cut_sandstone", "chiseled_sandstone",
                "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone", "chiseled_red_sandstone");
        slabs(sandstone, "sandstone_slab", "smooth_sandstone_slab", "cut_sandstone_slab", "red_sandstone_slab",
                "smooth_red_sandstone_slab", "cut_red_sandstone_slab");
        members(sandstone, STAIRS, "sandstone_stairs", "smooth_sandstone_stairs", "red_sandstone_stairs", "smooth_red_sandstone_stairs");

        // Bricks, Terracotta (plain, dyed, glazed): full 150; Brick slab 75 / double 150; stairs & wall 115. Pickaxe.
        Material brick = material("brick_terracotta", PICKAXE, 150f);
        form(brick, SLAB, 75f);
        form(brick, STAIRS, 115f);
        form(brick, WALL, 115f);
        members(brick, Form.FULL_BLOCK, "bricks", "terracotta");
        members(brick, Form.FULL_BLOCK, each(COLORS, c -> c + "_terracotta"));
        members(brick, Form.FULL_BLOCK, each(COLORS, c -> c + "_glazed_terracotta"));
        slabs(brick, "brick_slab");
        members(brick, STAIRS, "brick_stairs");
        members(brick, WALL, "brick_wall");
    }

    private static void glassIceSnow() {
        Material glass = material("glass", NEUTRAL, 50f);
        form(glass, PANE, 25f);
        members(glass, Form.FULL_BLOCK, "glass", "tinted_glass");
        members(glass, Form.FULL_BLOCK, each(COLORS, c -> c + "_stained_glass"));
        members(glass, PANE, "glass_pane");
        members(glass, PANE, each(COLORS, c -> c + "_stained_glass_pane"));

        exact(ordinary(50f, NEUTRAL), "ice");
        exact(ordinary(100f, NEUTRAL), "packed_ice");
        exact(ordinary(150f, NEUTRAL), "blue_ice");

        exact(ordinary(50f, SHOVEL), "snow_block");
        Block layer = block("snow");
        if (layer != null) {
            Material snow = new Material("totality:snow_layer");
            BlockProfiles.assign(layer, snow, SNOW_LAYER);
            BlockProfiles.block(layer, BlockProfile.EMPTY.withClassification(Classification.ORDINARY).withTools(SHOVEL));
            for (int layers = 1; layers <= 8; layers++) {
                int n = layers;
                BlockProfiles.stateOverride(layer, s -> s.getValue(SnowLayerBlock.LAYERS) == n, BlockProfile.durability(5f * n));
            }
        }
        note("ledger 'Shovel; no tier': Required Mining Tier kept vanilla-derived (1, needs a correct tool for drops); "
                + "an explicit Tier 0 would also make bare-hand strikes harvest-eligible via HarvestGrant - confirm", "snow", "snow_block");
    }

    private static void nether() {
        Material netherrack = materialVanillaTool("netherrack_nylium", 50f);
        members(netherrack, Form.FULL_BLOCK, "netherrack", "crimson_nylium", "warped_nylium");
        exact(ordinary(50f, PICKAXE), "netherrack");

        Material blackstone = material("blackstone", PICKAXE, 120f);
        form(blackstone, SLAB, 60f);
        form(blackstone, STAIRS, 90f);
        form(blackstone, WALL, 90f);
        members(blackstone, Form.FULL_BLOCK, "blackstone", "gilded_blackstone", "polished_blackstone", "polished_blackstone_bricks",
                "chiseled_polished_blackstone", "cracked_polished_blackstone_bricks");
        exact(ordinary(90f, null), "cracked_polished_blackstone_bricks");
        slabs(blackstone, "blackstone_slab", "polished_blackstone_slab", "polished_blackstone_brick_slab");
        members(blackstone, STAIRS, "blackstone_stairs", "polished_blackstone_stairs", "polished_blackstone_brick_stairs");
        members(blackstone, WALL, "blackstone_wall", "polished_blackstone_wall", "polished_blackstone_brick_wall");

        Material netherBrick = material("nether_bricks", PICKAXE, 150f);
        form(netherBrick, SLAB, 75f);
        form(netherBrick, STAIRS, 115f);
        form(netherBrick, WALL, 115f);
        form(netherBrick, FENCE, 75f);
        members(netherBrick, Form.FULL_BLOCK, "nether_bricks", "red_nether_bricks", "chiseled_nether_bricks", "cracked_nether_bricks");
        exact(ordinary(115f, null), "cracked_nether_bricks");
        slabs(netherBrick, "nether_brick_slab", "red_nether_brick_slab");
        members(netherBrick, STAIRS, "nether_brick_stairs", "red_nether_brick_stairs");
        members(netherBrick, WALL, "nether_brick_wall", "red_nether_brick_wall");
        members(netherBrick, FENCE, "nether_brick_fence");

        exact(ordinary(150f, PICKAXE), "magma_block");
    }

    private static void endAndPurpur() {
        Material endStone = material("end_stone", PICKAXE, 200f);
        form(endStone, SLAB, 100f);
        form(endStone, STAIRS, 150f);
        form(endStone, WALL, 150f);
        members(endStone, Form.FULL_BLOCK, "end_stone", "end_stone_bricks");
        slabs(endStone, "end_stone_brick_slab");
        members(endStone, STAIRS, "end_stone_brick_stairs");
        members(endStone, WALL, "end_stone_brick_wall");

        Material purpur = material("purpur", PICKAXE, 150f);
        form(purpur, SLAB, 75f);
        form(purpur, STAIRS, 115f);
        members(purpur, Form.FULL_BLOCK, "purpur_block", "purpur_pillar");
        slabs(purpur, "purpur_slab");
        members(purpur, STAIRS, "purpur_stairs");
    }

    /** Ordinary ores = host + 20 (Stone 120, Deepslate 170, Netherrack 70); Ancient Debris 300. Vanilla tiers/loot. */
    private static void ores() {
        List<String> minerals = List.of("coal", "iron", "copper", "gold", "redstone", "emerald", "lapis", "diamond");
        members(material("stone_hosted_ore", PICKAXE, 120f), Form.FULL_BLOCK, each(minerals, m -> m + "_ore"));
        members(material("deepslate_hosted_ore", PICKAXE, 170f), Form.FULL_BLOCK, each(minerals, m -> "deepslate_" + m + "_ore"));
        members(material("netherrack_hosted_ore", PICKAXE, 70f), Form.FULL_BLOCK, "nether_gold_ore", "nether_quartz_ore");
        exact(ordinary(300f, PICKAXE), "ancient_debris");
        // Ruby: accepted PROVISIONALLY (ledger); its Diamond-level harvest requirement stays as authored by its tags.
        exact(ordinary(120f, PICKAXE), "totality:ruby_ore");
        exact(ordinary(170f, PICKAXE), "totality:deepslate_ruby_ore");
        note("provisional ledger value (Ruby host baseline); Totality Core may revise", "totality:ruby_ore", "totality:deepslate_ruby_ore");

        // Mineral storage blocks (vanilla-derived tiers).
        exact(ordinary(100f, PICKAXE), "coal_block");
        exact(ordinary(150f, PICKAXE), "raw_copper_block", "raw_iron_block", "raw_gold_block", "gold_block", "lapis_block", "redstone_block");
        exact(ordinary(200f, PICKAXE), "iron_block", "emerald_block");
        exact(ordinary(250f, PICKAXE), "diamond_block");
        exact(ordinary(400f, PICKAXE), "netherite_block");
        exact(ordinary(600f, PICKAXE), "obsidian", "crying_obsidian");
    }

    private static void metalAndUtility() {
        // Copper construction (all oxidation and wax states; no HP change between them). Tool: vanilla-appropriate.
        Material copper = materialVanillaTool("copper_construction", 150f);
        form(copper, SLAB, 75f);
        form(copper, STAIRS, 115f);
        form(copper, DOOR, 100f);
        form(copper, TRAPDOOR, 75f);
        Form grate = new Form("grate");
        form(copper, grate, 75f);
        members(copper, Form.FULL_BLOCK, each(OXIDATION, o -> o.isEmpty() ? "copper_block" : o.equals("waxed_") ? "waxed_copper_block" : o + "copper"));
        members(copper, Form.FULL_BLOCK, each(OXIDATION, o -> o + "cut_copper"));
        members(copper, Form.FULL_BLOCK, each(OXIDATION, o -> o + "chiseled_copper"));
        slabs(copper, each(OXIDATION, o -> o + "cut_copper_slab"));
        members(copper, STAIRS, each(OXIDATION, o -> o + "cut_copper_stairs"));
        members(copper, grate, each(OXIDATION, o -> o + "copper_grate"));
        members(copper, DOOR, each(OXIDATION, o -> o + "copper_door"));
        members(copper, TRAPDOOR, each(OXIDATION, o -> o + "copper_trapdoor"));

        // Iron details. Pickaxe.
        exact(ordinary(100f, PICKAXE), "iron_bars", "iron_trapdoor");
        exact(ordinary(150f, PICKAXE), "iron_door");
        exact(ordinary(50f, PICKAXE), "iron_chain");
        // Anvil (degradation currently clears damage: transformation deferred), Hopper, Cauldrons.
        exact(ordinary(400f, PICKAXE), "anvil");
        exact(ordinary(300f, PICKAXE), "chipped_anvil");
        exact(ordinary(200f, PICKAXE), "damaged_anvil");
        exact(ordinary(150f, PICKAXE), "hopper", "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron");
        note("changing the cauldron contents changes the block id; damage is currently cleared (transformation deferred)",
                "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron");
        note("degradation changes the block id; damage is currently cleared (transformation deferred)", "anvil", "chipped_anvil", "damaged_anvil");
        // Other metal utility (base ids only: the ledger states no oxidation/wax variants for these rows).
        exact(ordinary(75f, PICKAXE), "lightning_rod");
        exact(ordinary(150f, PICKAXE), "copper_bulb");
        exact(ordinary(75f, null), "heavy_weighted_pressure_plate");
        exact(ordinary(60f, null), "light_weighted_pressure_plate");
        // Furnace family; lit/unlit is a state change and keeps damage.
        exact(ordinary(150f, PICKAXE), "furnace", "smoker");
        exact(ordinary(250f, PICKAXE), "blast_furnace");
        // Mechanisms; the piston head shares its extended base's record.
        exact(ordinary(150f, PICKAXE), "dispenser", "dropper", "observer");
        exact(ordinary(200f, PICKAXE), "piston", "sticky_piston", "piston_head");
    }

    private static void workstationsAndContainers() {
        exact(ordinary(100f, AXE), "crafting_table", "cartography_table", "fletching_table", "lectern");
        exact(ordinary(150f, AXE), "smithing_table");
        exact(ordinary(150f, PICKAXE), "stonecutter");
        exact(ordinary(100f, PICKAXE), "grindstone");
        exact(ordinary(75f, PICKAXE), "brewing_stand");
        exact(ordinary(75f, AXE), "loom", "composter");
        exact(ordinary(100f, AXE), "barrel", "bookshelf", "chiseled_bookshelf", "jukebox", "note_block");
        exact(ordinary(100f, AXE), "chest", "trapped_chest");               // per half; halves are independent records
        exact(ordinary(150f, NEUTRAL), "shulker_box");
        exact(ordinary(150f, NEUTRAL), each(COLORS, c -> c + "_shulker_box"));
        exact(ordinary(500f, PICKAXE), "ender_chest");
    }

    private static void lightingAndCircuitry() {
        exact(ordinary(50f, NEUTRAL), "glowstone");
        exact(ordinary(100f, NEUTRAL), "sea_lantern");
        exact(ordinary(100f, HOE), "shroomlight");
        exact(ordinary(150f, PICKAXE), "redstone_lamp");
        exact(ordinary(50f, PICKAXE), "lantern", "soul_lantern");
        exact(ordinary(75f, null), "copper_lantern");                    // "tool per actual vanilla"
        exact(ordinary(25f, PICKAXE), "rail", "powered_rail", "detector_rail", "activator_rail");
        exact(ordinary(75f, AXE), "daylight_detector");
        exact(ordinary(100f, HOE), "target");
    }

    private static void organic() {
        Material leaves = material("leaves", HOE, 25f);
        tag(leaves, Form.FULL_BLOCK, BlockTags.LEAVES);
        exact(ordinary(50f, HOE), "moss_block");
        exact(ordinary(100f, HOE), "hay_block", "dried_kelp_block", "nether_wart_block", "warped_wart_block");
        exact(ordinary(50f, NEUTRAL), "cactus");                           // per segment
        exact(ordinary(75f, AXE), "pumpkin", "carved_pumpkin", "jack_o_lantern", "melon");
        exact(ordinary(25f, AXE), "cocoa");
        exact(ordinary(100f, AXE), "bee_nest", "beehive");
        exact(ordinary(75f, NEUTRAL), "honeycomb_block");
        exact(ordinary(75f, NEUTRAL), each(COLORS, c -> c + "_wool"));
        exact(ordinary(100f, NEUTRAL), each(COLORS, c -> c + "_bed"));    // whole bed, one shared owner record
        exact(ordinary(50f, HOE), "sponge", "wet_sponge");
        exact(ordinary(100f, HOE), "ochre_froglight", "verdant_froglight", "pearlescent_froglight");
        List<String> corals = List.of("tube", "brain", "bubble", "fire", "horn");
        exact(ordinary(50f, PICKAXE), each(corals, c -> c + "_coral_block"));
        exact(ordinary(50f, PICKAXE), each(corals, c -> "dead_" + c + "_coral_block"));
        note("wet/dry changes the block id; damage is currently cleared (transformation deferred)", "sponge", "wet_sponge");
    }

    private static void concreteQuartzPrismarine() {
        exact(ordinary(75f, SHOVEL), each(COLORS, c -> c + "_concrete_powder"));
        exact(ordinary(150f, PICKAXE), each(COLORS, c -> c + "_concrete"));

        Material quartz = material("quartz", PICKAXE, 150f);
        form(quartz, SLAB, 75f);
        form(quartz, STAIRS, 115f);
        members(quartz, Form.FULL_BLOCK, "quartz_block", "smooth_quartz", "chiseled_quartz_block", "quartz_pillar", "quartz_bricks");
        slabs(quartz, "quartz_slab", "smooth_quartz_slab");
        members(quartz, STAIRS, "quartz_stairs", "smooth_quartz_stairs");

        Material prismarine = material("prismarine", PICKAXE, 150f);
        form(prismarine, SLAB, 75f);
        form(prismarine, STAIRS, 115f);
        form(prismarine, WALL, 115f);
        members(prismarine, Form.FULL_BLOCK, "prismarine", "prismarine_bricks");
        slabs(prismarine, "prismarine_slab", "prismarine_brick_slab");
        members(prismarine, STAIRS, "prismarine_stairs", "prismarine_brick_stairs");
        members(prismarine, WALL, "prismarine_wall");

        Material dark = material("dark_prismarine", PICKAXE, 200f);
        form(dark, SLAB, 100f);
        form(dark, STAIRS, 150f);
        members(dark, Form.FULL_BLOCK, "dark_prismarine");
        slabs(dark, "dark_prismarine_slab");
        members(dark, STAIRS, "dark_prismarine_stairs");
    }

    private static void caveAndDeep() {
        exact(ordinary(150f, PICKAXE), "amethyst_block", "budding_amethyst");
        note("non-obtainable (vanilla loot)", "budding_amethyst");
        exact(ordinary(25f, PICKAXE), "amethyst_cluster");
        exact(ordinary(100f, HOE), "sculk", "sculk_sensor", "calibrated_sculk_sensor");
        exact(ordinary(150f, HOE), "sculk_catalyst", "sculk_shrieker");
    }

    private static void functionalAndEncounter() {
        exact(ordinary(400f, PICKAXE), "enchanting_table", "respawn_anchor");
        exact(ordinary(250f, NEUTRAL), "beacon");
        exact(ordinary(300f, PICKAXE), "lodestone");
        exact(ordinary(150f, NEUTRAL), "conduit");
        exact(ordinary(300f, PICKAXE), "spawner");
        exact(ordinary(500f, NEUTRAL), "trial_spawner", "vault");      // Vault covers its ominous state
        note("non-obtainable (vanilla loot)", "spawner", "trial_spawner", "vault");
        exact(ordinary(100f, AXE), "campfire", "soul_campfire");
        exact(ordinary(150f, PICKAXE), "bell");
        exact(ordinary(100f, PICKAXE), "bone_block");
        exact(ordinary(25f, NEUTRAL), "sniffer_egg");
    }

    /** Accepted SPECIAL / vanilla-owned (vanilla break timing, interactions and drops stay authoritative). */
    private static void special() {
        special("cobweb", "decorated_pot", "tnt", "dragon_egg", "turtle_egg", "frogspawn", "powder_snow", "sculk_vein",
                "pointed_dripstone", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
                "bamboo", "bamboo_sapling", "sugar_cane", "kelp", "kelp_plant", "honey_block", "slime_block",
                "lever", "redstone_wire", "tripwire", "tripwire_hook",
                "torch", "wall_torch", "redstone_torch", "redstone_wall_torch", "soul_torch", "soul_wall_torch",
                "vine", "cave_vines", "cave_vines_plant", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant");
        // Final correction pass: hardness-0 blocks previously authored with inert finite HP — now explicitly SPECIAL, no HP.
        special("repeater", "comparator", "end_rod", "scaffolding");
        List<String> corals = List.of("tube", "brain", "bubble", "fire", "horn");
        special(each(corals, c -> c + "_coral"));
        special(each(corals, c -> "dead_" + c + "_coral"));
        special(each(corals, c -> c + "_coral_fan"));
        special(each(corals, c -> "dead_" + c + "_coral_fan"));
        special(each(corals, c -> c + "_coral_wall_fan"));
        special(each(corals, c -> "dead_" + c + "_coral_wall_fan"));
        specialTag(BlockTags.CROPS);
        specialTag(TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("saplings")));   // no BlockTags constant in 26.2
        specialTag(BlockTags.SMALL_FLOWERS);
        specialTag(BlockTags.FLOWER_POTS);
        specialTag(BlockTags.WOOL_CARPETS);
        specialTag(BlockTags.BANNERS);
        specialTag(BlockTags.STANDING_SIGNS);
        specialTag(BlockTags.WALL_SIGNS);
        specialTag(BlockTags.CANDLES);
        specialTag(BlockTags.CANDLE_CAKES);
        specialTag(BlockTags.BUTTONS);
        specialTag(BlockTags.WOODEN_PRESSURE_PLATES);
        specialTag(BlockTags.STONE_PRESSURE_PLATES);
        // Ordinary small flora with explicit ids (grass/fern/bush family, aquatic grasses, mushrooms, fungi, roots...).
        special("short_grass", "fern", "tall_grass", "large_fern", "dead_bush", "bush", "short_dry_grass", "tall_dry_grass",
                "seagrass", "tall_seagrass", "brown_mushroom", "red_mushroom", "crimson_fungus", "warped_fungus",
                "crimson_roots", "warped_roots", "nether_sprouts", "lily_pad", "sweet_berry_bush", "nether_wart",
                "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant", "pink_petals", "wildflowers",
                "pumpkin_stem", "melon_stem", "attached_pumpkin_stem", "attached_melon_stem");
        note("SPECIAL keeps vanilla break timing; the ledger's 'instant' describes vanilla feel, not a forced instant break",
                "white_banner", "oak_sign", "stone_button", "lever", "bamboo", "pointed_dripstone", "large_amethyst_bud");
    }

    /** Accepted UNBREAKABLE / protected: exactly the runtime unbreakable ids (portals remain portal-owned). */
    private static void unbreakable() {
        exact(BlockProfile.EMPTY.withClassification(Classification.UNBREAKABLE), "bedrock", "barrier", "light",
                "command_block", "repeating_command_block", "chain_command_block", "structure_block", "jigsaw",
                "test_block", "test_instance_block", "end_portal_frame", "moving_piston",
                "nether_portal", "end_portal", "end_gateway");
    }

    /**
     * Pass 3 reconciliation (Context/References/TOTALITY_BLOCK_BREAKING_V2_PASS3_RECONCILIATION_DELTA_LEDGER.md and its exact-id
     * CSV, the canonical checklist): the 176 vanilla ids Pass 3 left UNRESOLVED_DESIGN, 147 ORDINARY + 29 SPECIAL. Every value
     * and member below is a CSV row; slab doubles are the CSV's explicit double values. "VANILLA" tool rows author no tool.
     * No Required Mining Tier is authored. Cross-id preservation (copper weathering/waxing/scraping, stripping, soil
     * cultivation) is NOT wired here: it belongs to the separate transformation-integration pass.
     */
    private static void reconciliation() {
        // Soil (6): 100, Shovel.
        members(material("soil", SHOVEL, 100f), Form.FULL_BLOCK, "coarse_dirt", "podzol", "farmland", "mycelium", "dirt_path", "rooted_dirt");

        // Nether structural wood (22): separate from Overworld Wood; full 100, slab 50 / double 100, fence & trapdoor 50,
        // stairs & gate 75, door 100 (shared lower-half owner). Axe.
        Material nether = material("nether_wood", AXE, 100f);
        form(nether, SLAB, 50f);
        form(nether, FENCE, 50f);
        form(nether, TRAPDOOR, 50f);
        form(nether, STAIRS, 75f);
        form(nether, FENCE_GATE, 75f);
        form(nether, DOOR, 100f);
        form(nether, SHELF, 100f);
        members(nether, Form.FULL_BLOCK, "warped_stem", "stripped_warped_stem", "warped_hyphae", "stripped_warped_hyphae",
                "crimson_stem", "stripped_crimson_stem", "crimson_hyphae", "stripped_crimson_hyphae", "crimson_planks", "warped_planks");
        slabs(nether, "crimson_slab", "warped_slab");
        members(nether, FENCE, "crimson_fence", "warped_fence");
        members(nether, TRAPDOOR, "crimson_trapdoor", "warped_trapdoor");
        members(nether, FENCE_GATE, "crimson_fence_gate", "warped_fence_gate");
        members(nether, STAIRS, "crimson_stairs", "warped_stairs");
        members(nether, DOOR, "crimson_door", "warped_door");

        // Wooden shelves (12): 100, Axe; species wood identity (Crimson/Warped -> Nether wood, Bamboo -> Crafted Bamboo).
        Material overworld = BlockDurabilityDefinitions.OVERWORLD_LOG;
        form(overworld, SHELF, 100f);
        form(crafted_bamboo, SHELF, 100f);
        members(overworld, SHELF, "acacia_shelf", "birch_shelf", "cherry_shelf", "dark_oak_shelf", "jungle_shelf", "mangrove_shelf",
                "oak_shelf", "pale_oak_shelf", "spruce_shelf");
        members(crafted_bamboo, SHELF, "bamboo_shelf");
        members(nether, SHELF, "crimson_shelf", "warped_shelf");

        // Sandstone walls (2): 75, Pickaxe.
        form(sandstoneMaterial, WALL, 75f);
        members(sandstoneMaterial, WALL, "sandstone_wall", "red_sandstone_wall");

        // Resin brick (5): full 150, stairs & wall 115, slab 75 / double 150. Pickaxe.
        Material resin = material("resin_brick", PICKAXE, 150f);
        form(resin, STAIRS, 115f);
        form(resin, WALL, 115f);
        form(resin, SLAB, 75f);
        members(resin, Form.FULL_BLOCK, "resin_bricks", "chiseled_resin_bricks");
        members(resin, STAIRS, "resin_brick_stairs");
        members(resin, WALL, "resin_brick_wall");
        slabs(resin, "resin_brick_slab");

        // Sulfur natural/polished (8): full 100, slab 50 / double 100, stairs & walls 75. Pickaxe.
        Material sulfur = material("sulfur_natural", PICKAXE, 100f);
        form(sulfur, SLAB, 50f);
        form(sulfur, STAIRS, 75f);
        form(sulfur, WALL, 75f);
        members(sulfur, Form.FULL_BLOCK, "sulfur", "polished_sulfur");
        slabs(sulfur, "sulfur_slab", "polished_sulfur_slab");
        members(sulfur, STAIRS, "sulfur_stairs", "polished_sulfur_stairs");
        members(sulfur, WALL, "sulfur_wall", "polished_sulfur_wall");
        // Sulfur bricks (5): full 150, slab 75 / double 150, stairs & wall 115. Pickaxe.
        Material sulfurBrick = material("sulfur_brick", PICKAXE, 150f);
        form(sulfurBrick, SLAB, 75f);
        form(sulfurBrick, STAIRS, 115f);
        form(sulfurBrick, WALL, 115f);
        members(sulfurBrick, Form.FULL_BLOCK, "sulfur_bricks", "chiseled_sulfur");
        slabs(sulfurBrick, "sulfur_brick_slab");
        members(sulfurBrick, STAIRS, "sulfur_brick_stairs");
        members(sulfurBrick, WALL, "sulfur_brick_wall");
        // Potent Sulfur 100 Pickaxe (its own identity; block entity and states untouched).
        members(material("potent_sulfur", PICKAXE, 100f), Form.FULL_BLOCK, "potent_sulfur");

        // Cinnabar natural/polished (8) and bricks (5): the same values as the matching Sulfur families. Pickaxe.
        Material cinnabar = material("cinnabar_natural", PICKAXE, 100f);
        form(cinnabar, SLAB, 50f);
        form(cinnabar, STAIRS, 75f);
        form(cinnabar, WALL, 75f);
        members(cinnabar, Form.FULL_BLOCK, "cinnabar", "polished_cinnabar");
        slabs(cinnabar, "cinnabar_slab", "polished_cinnabar_slab");
        members(cinnabar, STAIRS, "cinnabar_stairs", "polished_cinnabar_stairs");
        members(cinnabar, WALL, "cinnabar_wall", "polished_cinnabar_wall");
        Material cinnabarBrick = material("cinnabar_brick", PICKAXE, 150f);
        form(cinnabarBrick, SLAB, 75f);
        form(cinnabarBrick, STAIRS, 115f);
        form(cinnabarBrick, WALL, 115f);
        members(cinnabarBrick, Form.FULL_BLOCK, "cinnabar_bricks", "chiseled_cinnabar");
        slabs(cinnabarBrick, "cinnabar_brick_slab");
        members(cinnabarBrick, STAIRS, "cinnabar_brick_stairs");
        members(cinnabarBrick, WALL, "cinnabar_brick_wall");

        // Copper utilities (21): every oxidation/wax variant uses its base value; tool stays vanilla.
        Material copperUtility = materialVanillaTool("copper_utility", 150f);
        Form lantern = new Form("lantern"), bulb = new Form("bulb"), rod = new Form("rod");
        form(copperUtility, lantern, 75f);
        form(copperUtility, bulb, 150f);
        form(copperUtility, rod, 75f);
        List<String> variants = OXIDATION.subList(1, OXIDATION.size());   // the 7 non-base oxidation/wax states
        members(copperUtility, lantern, each(variants, o -> o + "copper_lantern"));
        members(copperUtility, bulb, each(variants, o -> o + "copper_bulb"));
        members(copperUtility, rod, each(variants, o -> o + "lightning_rod"));
        // Copper bars 100 / chains 50 / chests 150 (independent per position) / golem statues 150 (8 states each). Pickaxe.
        members(material("copper_bars", PICKAXE, 100f), Form.FULL_BLOCK, each(OXIDATION, o -> o + "copper_bars"));
        members(material("copper_chain", PICKAXE, 50f), Form.FULL_BLOCK, each(OXIDATION, o -> o + "copper_chain"));
        members(material("copper_chest", PICKAXE, 150f), Form.FULL_BLOCK, each(OXIDATION, o -> o + "copper_chest"));
        members(material("copper_golem_statue", PICKAXE, 150f), Form.FULL_BLOCK, each(OXIDATION, o -> o + "copper_golem_statue"));

        // Mob heads and skulls (14): 50, no preferred tool.
        members(material("mob_head", NEUTRAL, 50f), Form.FULL_BLOCK, "skeleton_skull", "skeleton_wall_skull", "wither_skeleton_skull",
                "wither_skeleton_wall_skull", "zombie_head", "zombie_wall_head", "player_head", "player_wall_head", "creeper_head",
                "creeper_wall_head", "dragon_head", "dragon_wall_head", "piglin_head", "piglin_wall_head");

        // Single entries.
        members(material("mechanism", PICKAXE, 150f), Form.FULL_BLOCK, "crafter");
        members(material("heavy_core", PICKAXE, 600f), Form.FULL_BLOCK, "heavy_core");
        members(material("moss", HOE, 50f), Form.FULL_BLOCK, "pale_moss_block");
        members(material("mangrove_roots", AXE, 50f), Form.FULL_BLOCK, "mangrove_roots");
        members(material("muddy_mangrove_roots", SHOVEL, 100f), Form.FULL_BLOCK, "muddy_mangrove_roots");
        Material petrified = material("petrified_wood", PICKAXE, 100f);       // double slab 100
        form(petrified, SLAB, 50f);
        slabs(petrified, "petrified_oak_slab");

        // Explicit SPECIAL (29): vanilla-owned, no finite HP, vanilla timing/interactions/drops.
        special("suspicious_sand", "suspicious_gravel",                          // archaeology
                "resin_clump", "resin_block",                                     // raw resin
                "sulfur_spike", "creaking_heart", "copper_torch", "copper_wall_torch",
                "fire", "soul_fire", "cactus_flower", "structure_void", "dried_ghast", "sea_pickle", "spore_blossom",
                "leaf_litter", "small_dripleaf", "hanging_roots", "pale_hanging_moss", "firefly_bush",
                "moss_carpet", "pale_moss_carpet", "cake", "glow_lichen", "chorus_plant", "chorus_flower", "frosted_ice",
                "big_dripleaf", "big_dripleaf_stem");
    }
}
