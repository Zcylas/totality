package zcylas.totality.api.quest;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import zcylas.totality.Totality;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Data-driven quest templates, loaded from {@code data/<namespace>/quests/*.json}. */
public class QuestRegistry extends SimplePreparableReloadListener<Map<Identifier, QuestTemplate>>
        implements IdentifiableResourceReloadListener {

    public static final QuestRegistry INSTANCE = new QuestRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "quests";

    private Map<Identifier, QuestTemplate> quests = new HashMap<>();

    private QuestRegistry() {}

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "quest_registry");
    }

    @Override
    protected Map<Identifier, QuestTemplate> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, QuestTemplate> result = new HashMap<>();
        manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                QuestTemplate.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> LOGGER.error("Failed to parse quest {}: {}", resourceId, err))
                        .ifPresent(template -> {
                            String path = resourceId.getPath();
                            String key = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
                            result.put(Identifier.fromNamespaceAndPath(resourceId.getNamespace(), key), template);
                        });
            } catch (Exception e) {
                LOGGER.error("Failed to load quest {}", resourceId, e);
            }
        });
        LOGGER.info("Loaded {} quests", result.size());
        return result;
    }

    @Override
    protected void apply(Map<Identifier, QuestTemplate> prepared, ResourceManager manager, ProfilerFiller profiler) {
        this.quests = prepared;
    }

    public @Nullable QuestTemplate get(Identifier id) {
        return quests.get(id);
    }

    public Map<Identifier, QuestTemplate> getAll() {
        return quests;
    }
}
