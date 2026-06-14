package zcylas.totality.api.dialogue;

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

public class DialogueRegistry extends SimplePreparableReloadListener<Map<Identifier, DialogueTemplate>>
        implements IdentifiableResourceReloadListener {

    public static final DialogueRegistry INSTANCE = new DialogueRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "dialogues";

    private Map<Identifier, DialogueTemplate> templates = new HashMap<>();

    private DialogueRegistry() {}

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "dialogue_registry");
    }

    @Override
    protected Map<Identifier, DialogueTemplate> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, DialogueTemplate> result = new HashMap<>();
        manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                DialogueTemplate.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> LOGGER.error("Failed to parse dialogue {}: {}", resourceId, err))
                        .ifPresent(template -> {
                            String path = resourceId.getPath();
                            String key = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
                            result.put(Identifier.fromNamespaceAndPath(resourceId.getNamespace(), key), template);
                        });
            } catch (Exception e) {
                LOGGER.error("Failed to load dialogue {}", resourceId, e);
            }
        });
        LOGGER.info("Loaded {} dialogues", result.size());
        return result;
    }

    @Override
    protected void apply(Map<Identifier, DialogueTemplate> prepared, ResourceManager manager, ProfilerFiller profiler) {
        this.templates = prepared;
    }

    public @Nullable DialogueTemplate get(Identifier id) {
        return templates.get(id);
    }

    public boolean has(Identifier id) {
        return templates.containsKey(id);
    }
}
