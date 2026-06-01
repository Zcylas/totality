package zcylas.totality.api.mob.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import zcylas.totality.Totality;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MobStatBlockLoader implements SimpleSynchronousResourceReloadListener {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mob_stats");

    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public Identifier getFabricId() { return ID; }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        MobStatBlockRegistry.clear();

        Map<Identifier, Resource> resources = manager.listResources(
                "mob_stats", loc -> loc.getPath().endsWith(".json"));

        int loaded = 0;
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (InputStream stream  = entry.getValue().open();
                 Reader     reader  = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                MobStatBlock block = GSON.fromJson(reader, MobStatBlock.class);
                MobStatBlockRegistry.register(block);
                loaded++;

            } catch (Exception e) {
                Totality.LOGGER.error("Failed to load mob stat block {}: {}",
                        entry.getKey(), e.getMessage());
            }
        }

        Totality.LOGGER.info("[Totality] Loaded {} mob stat blocks", loaded);
    }
}