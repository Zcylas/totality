package zcylas.totality.entity.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import zcylas.totality.Totality;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven random NPC name pools — data/totality/npc_names/{male,female,neutral}.json,
 * each a flat JSON array of strings. Neutral names are unisex and usable alongside either
 * gendered pool (see getRandomName).
 */
public class NpcNameRegistry extends SimplePreparableReloadListener<NpcNameRegistry.Loaded>
        implements IdentifiableResourceReloadListener {

    public static final NpcNameRegistry INSTANCE = new NpcNameRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "npc_names";

    private Map<NpcGender, List<String>> namesByGender = new EnumMap<>(NpcGender.class);
    private List<String> neutralNames = List.of();

    private NpcNameRegistry() {}

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "npc_name_registry");
    }

    record Loaded(Map<NpcGender, List<String>> byGender, List<String> neutral) {}

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<NpcGender, List<String>> byGender = new EnumMap<>(NpcGender.class);
        for (NpcGender gender : NpcGender.values()) byGender.put(gender, new ArrayList<>());
        List<String> neutral = new ArrayList<>();

        manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            String fileName = resourceId.getPath().substring(resourceId.getPath().lastIndexOf('/') + 1);
            List<String> target = switch (fileName) {
                case "male.json" -> byGender.get(NpcGender.MALE);
                case "female.json" -> byGender.get(NpcGender.FEMALE);
                case "neutral.json" -> neutral;
                default -> null;
            };
            if (target == null) return;
            try (Reader reader = resource.openAsReader()) {
                JsonArray array = GsonHelper.parseArray(reader);
                for (JsonElement el : array) target.add(el.getAsString());
            } catch (Exception e) {
                LOGGER.error("Failed to load NPC names from {}", resourceId, e);
            }
        });

        LOGGER.info("Loaded NPC names: {} male, {} female, {} neutral",
                byGender.get(NpcGender.MALE).size(), byGender.get(NpcGender.FEMALE).size(), neutral.size());
        return new Loaded(byGender, neutral);
    }

    @Override
    protected void apply(Loaded prepared, ResourceManager manager, ProfilerFiller profiler) {
        this.namesByGender = prepared.byGender();
        this.neutralNames = prepared.neutral();
    }

    public String getRandomName(RandomSource random, NpcGender gender) {
        List<String> pool = new ArrayList<>(namesByGender.getOrDefault(gender, List.of()));
        pool.addAll(neutralNames);
        if (pool.isEmpty()) return "Nameless";
        return pool.get(random.nextInt(pool.size()));
    }
}
