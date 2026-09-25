package zcylas.totality.api.mining;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.Totality;
import zcylas.totality.api.mining.BlockProfile.Classification;
import zcylas.totality.api.mining.BlockProfile.Layer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Registry coverage of Block/Material Profiles (Block Breaking V2 Pass 3): one row per block id of the LIVE registry,
 * resolved through exactly the {@link BlockProfiles} the mining engine and Tooltip V2 use. Read-only; runs only from
 * the gated {@link MiningVerification} suite and writes CSVs into the disposable server run directory.
 *
 * <p>Coverage categories: {@code NA}, {@code UNBREAKABLE}, {@code SPECIAL} (explicitly authored), {@code
 * ACCEPTED_AUTHORED} (ordinary with authored durability), {@code COMPAT_FALLBACK} (non-vanilla namespace on the
 * compatibility fallback, deferred to Totality Core) and {@code UNRESOLVED_DESIGN} (vanilla, not unambiguously covered
 * by an accepted rule: fallback durability or fallback-only SPECIAL, or explicitly flagged for review). An unresolved
 * block still resolves through the compatibility fallback in game.
 */
final class BlockCoverageReport {

    enum Category { ACCEPTED_AUTHORED, SPECIAL, UNBREAKABLE, NA, COMPAT_FALLBACK, UNRESOLVED_DESIGN }

    record Row(Identifier id, Category category, String csv) {}

    private BlockCoverageReport() {}

    static Category categorize(Identifier id, BlockProfile.Resolved p, boolean flaggedForReview) {
        if (p.classification() == Classification.NOT_APPLICABLE) return Category.NA;
        if (p.classification() == Classification.UNBREAKABLE) return Category.UNBREAKABLE;
        boolean vanilla = id.getNamespace().equals("minecraft");
        if (flaggedForReview) return vanilla ? Category.UNRESOLVED_DESIGN : Category.COMPAT_FALLBACK;
        if (p.classification() == Classification.SPECIAL) {
            if (p.classificationLayer() != Layer.FALLBACK) return Category.SPECIAL;
            return vanilla ? Category.UNRESOLVED_DESIGN : Category.COMPAT_FALLBACK;
        }
        if (p.durabilityLayer() != Layer.FALLBACK) return Category.ACCEPTED_AUTHORED;
        return vanilla ? Category.UNRESOLVED_DESIGN : Category.COMPAT_FALLBACK;
    }

    static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            BlockState state = block.defaultBlockState();
            BlockProfile.Resolved p = BlockProfiles.resolveStatic(state);
            String review = VanillaBlockProfiles.reviewNote(block);
            Category category = categorize(id, p, review != null);

            TreeSet<String> maxima = new TreeSet<>();
            TreeSet<String> classes = new TreeSet<>();
            for (BlockState s : block.getStateDefinition().getPossibleStates()) {
                BlockProfile.Resolved sp = BlockProfiles.resolveStatic(s);
                classes.add(sp.classification().name());
                if (sp.ordinary()) maxima.add(num(sp.maxDurability()));
            }
            List<String> flags = new ArrayList<>();
            if (block instanceof DoorBlock || block instanceof BedBlock || block instanceof PistonBaseBlock
                    || block instanceof PistonHeadBlock || block instanceof DoublePlantBlock) flags.add("MULTIBLOCK");
            if (block instanceof ChestBlock) flags.add("DOUBLE_CHEST_INDEPENDENT");
            if (maxima.size() > 1) flags.add("STATE_DEPENDENT_MAX");
            if (classes.size() > 1) flags.add("STATE_DEPENDENT_CLASS");
            if (state.hasBlockEntity()) flags.add("BLOCK_ENTITY");
            Item item = block.asItem();
            boolean hasItem = item instanceof BlockItem;
            if (!hasItem) flags.add("NO_BLOCKITEM");

            List<String> issues = new ArrayList<>();
            if (review != null) issues.add(review);
            else if (category == Category.UNRESOLVED_DESIGN) issues.add("no accepted ledger rule matches; compatibility fallback kept");
            else if (category == Category.COMPAT_FALLBACK) issues.add("non-vanilla; deferred to Totality Core; compatibility fallback kept");
            String note = VanillaBlockProfiles.note(block);
            if (note != null) issues.add(note);
            if (category == Category.ACCEPTED_AUTHORED && !(block.defaultDestroyTime() > 0f)) {
                issues.add("INERT: hardness 0 keeps the block vanilla-owned (confirmed decision); authored HP is data only until a finite-HP ownership mechanism exists");
            }
            String issue = String.join("; ", issues);
            String tools = p.tools().neutral() ? "NEUTRAL" : p.tools().tools().isEmpty() ? "NONE"
                    : p.tools().tools().stream().map(Enum::name).collect(Collectors.joining("|"));
            String csv = String.join(",",
                    id.toString(), id.getNamespace(), block.getClass().getSimpleName(), String.valueOf(hasItem),
                    num(block.defaultDestroyTime()), String.valueOf(state.requiresCorrectToolForDrops()),
                    String.valueOf(block.getStateDefinition().getPossibleStates().size()),
                    category.name(), p.classification().name(), p.classificationLayer().name(),
                    p.material() == null ? "" : p.material().id(), p.form() == null ? "" : p.form().id(),
                    p.ordinary() ? num(p.maxDurability()) : "", p.ordinary() ? p.durabilityLayer().name() : "",
                    String.join("|", maxima), tools, p.toolsLayer().name(), String.valueOf(p.requiredTier()),
                    p.ownership().name(), String.join("|", flags), quote(issue));
            rows.add(new Row(id, category, csv));
        }
        return rows;
    }

    static final String HEADER = "registry_id,namespace,block_class,has_block_item,hardness,requires_correct_tool,state_count,"
            + "coverage_category,classification,classification_provenance,material,form,max_durability_default_state,"
            + "durability_provenance,max_durability_all_states,effective_tools,tools_provenance,required_mining_tier,"
            + "integrity_ownership,flags,issue";

    /** Writes the complete coverage CSV, the fallback/unresolved CSV and a summary; returns the counts per category. */
    static Map<Category, Integer> write(MinecraftServer server) {
        List<Row> rows = rows();
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Category c : Category.values()) counts.put(c, 0);
        rows.forEach(r -> counts.merge(r.category(), 1, Integer::sum));
        Path dir = server.getServerDirectory().resolve("totality-block-coverage");
        try {
            Files.createDirectories(dir);
            List<String> all = new ArrayList<>(List.of(HEADER));
            rows.forEach(r -> all.add(r.csv()));
            Files.write(dir.resolve("BLOCK_PROFILE_COVERAGE.csv"), all);
            List<String> open = new ArrayList<>(List.of(HEADER));
            rows.stream().filter(r -> r.category() == Category.UNRESOLVED_DESIGN || r.category() == Category.COMPAT_FALLBACK)
                    .forEach(r -> open.add(r.csv()));
            Files.write(dir.resolve("BLOCK_PROFILE_FALLBACK_AND_UNRESOLVED.csv"), open);
            List<String> summary = new ArrayList<>();
            summary.add("registry blocks: " + rows.size() + " (minecraft: "
                    + rows.stream().filter(r -> r.id().getNamespace().equals("minecraft")).count() + ", other: "
                    + rows.stream().filter(r -> !r.id().getNamespace().equals("minecraft")).count() + ")");
            counts.forEach((c, n) -> summary.add(c + ": " + n));
            Files.write(dir.resolve("SUMMARY.txt"), summary);
            List<String> tags = new ArrayList<>(List.of("registry_id,tags"));
            for (Block block : BuiltInRegistries.BLOCK) {
                tags.add(BuiltInRegistries.BLOCK.getKey(block) + "," + BuiltInRegistries.BLOCK.wrapAsHolder(block).tags()
                        .map(t -> t.location().toString()).sorted().collect(Collectors.joining("|")));
            }
            Files.write(dir.resolve("BLOCK_TAG_INVENTORY.csv"), tags);
            Totality.LOGGER.info("[BlockCoverage] wrote {} rows to {}: {}", rows.size(), dir, counts);
        } catch (IOException e) {
            Totality.LOGGER.error("[BlockCoverage] could not write the coverage report", e);
        }
        return counts;
    }

    private static String num(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.valueOf(v);
    }

    private static String quote(String s) {
        return s.isEmpty() ? "" : "\"" + s.replace("\"", "'") + "\"";
    }
}
