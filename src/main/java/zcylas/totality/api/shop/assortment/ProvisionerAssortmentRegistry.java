package zcylas.totality.api.shop.assortment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import zcylas.totality.Totality;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data-driven Provisioner assortment pools, loaded from
 * {@code data/<namespace>/provisioner_assortments/*.json} (one {@link ProvisionerAssortmentPool}
 * per file). Mirrors {@link zcylas.totality.api.shop.ShopRegistry}/
 * {@link zcylas.totality.api.economy.value.ItemValueRegistry}'s loading pattern exactly, and for
 * the identical reason: pool entries carry registry-backed component data (e.g. potion variants),
 * which needs a real {@code RegistryOps} built from {@code server.registryAccess()} — deferred to
 * {@code ServerLifecycleEvents.SERVER_STARTED}, same trade-off as those two registries: no live
 * {@code /reload} support.
 */
public final class ProvisionerAssortmentRegistry {

    public static final ProvisionerAssortmentRegistry INSTANCE = new ProvisionerAssortmentRegistry();
    private static final String FOLDER = "provisioner_assortments";

    private Map<Identifier, ProvisionerAssortmentPool> pools = new HashMap<>();

    private ProvisionerAssortmentRegistry() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::load);
    }

    public Optional<ProvisionerAssortmentPool> get(Identifier id) {
        return Optional.ofNullable(pools.get(id));
    }

    private void load(MinecraftServer server) {
        Map<Identifier, ProvisionerAssortmentPool> result = new HashMap<>();
        ResourceManager manager = server.getResourceManager();
        RegistryOps<com.google.gson.JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());

        Map<Identifier, Resource> resources = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        int rejected = 0;
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
            try (InputStream stream = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(stream)) {

                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Optional<ProvisionerAssortmentPool> parsed = ProvisionerAssortmentPool.CODEC.parse(ops, json)
                        .resultOrPartial(err -> Totality.LOGGER.error(
                                "Failed to parse provisioner assortment {}: {}", resourceId, err));
                if (parsed.isEmpty()) {
                    rejected++;
                    continue;
                }

                ProvisionerAssortmentPool pool = parsed.get();
                ProvisionerAssortmentPool.ValidationResult validation = pool.validate();
                if (!validation.valid()) {
                    rejected++;
                    for (String error : validation.errors()) {
                        Totality.LOGGER.error("Rejected provisioner assortment {}: {}", resourceId, error);
                    }
                    continue;
                }

                String path = resourceId.getPath();
                String key = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
                Identifier id = Identifier.fromNamespaceAndPath(resourceId.getNamespace(), key);
                result.put(id, pool);
            } catch (Exception e) {
                rejected++;
                Totality.LOGGER.error("Failed to load provisioner assortment {}", resourceId, e);
            }
        }

        this.pools = result;
        Totality.LOGGER.info("Loaded {} provisioner assortment pool(s), {} rejected", result.size(), rejected);
    }
}
