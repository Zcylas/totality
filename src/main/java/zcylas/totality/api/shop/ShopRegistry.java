package zcylas.totality.api.shop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven shop catalogs, loaded from {@code data/<namespace>/shops/*.json}.
 *
 * Deferred to {@code ServerLifecycleEvents.SERVER_STARTED} (mirrors {@code RitualRecipeRegistry}),
 * not a {@code SimplePreparableReloadListener}, because {@link ShopEntry}'s {@code ItemStack.CODEC}
 * resolves registry-backed components (e.g. enchantments) — that needs a real
 * {@code RegistryOps} built from {@code server.registryAccess()}, which a plain reload
 * listener's {@code prepare()} phase doesn't have access to (only {@code JsonOps.INSTANCE},
 * which fails on anything registry-dependent). Trade-off: no live {@code /reload} support,
 * same as {@code RitualRecipeRegistry} — acceptable for this feature.
 */
public final class ShopRegistry {

    public static final ShopRegistry INSTANCE = new ShopRegistry();
    private static final String FOLDER = "shops";

    private Map<Identifier, ShopTemplate> shops = new HashMap<>();

    private ShopRegistry() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::load);
    }

    public @Nullable ShopTemplate get(Identifier id) {
        return shops.get(id);
    }

    private void load(MinecraftServer server) {
        Map<Identifier, ShopTemplate> result = new HashMap<>();
        ResourceManager manager = server.getResourceManager();
        RegistryOps<com.google.gson.JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());

        Map<Identifier, Resource> resources = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
            try (InputStream stream = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(stream)) {

                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ShopTemplate.CODEC.parse(ops, json)
                        .resultOrPartial(err -> Totality.LOGGER.error("Failed to parse shop {}: {}", resourceId, err))
                        .ifPresent(template -> {
                            String path = resourceId.getPath();
                            String key = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
                            Identifier id = Identifier.fromNamespaceAndPath(resourceId.getNamespace(), key);
                            result.put(id, rejectNegativePrices(id, template));
                        });
            } catch (Exception e) {
                Totality.LOGGER.error("Failed to load shop {}", resourceId, e);
            }
        }

        this.shops = result;
        Totality.LOGGER.info("Loaded {} shops", result.size());
    }

    /**
     * Drops any catalog entry with a negative authored price as a datapack authoring error —
     * defense-in-depth alongside (not instead of) {@link TradeSessionManager#handleBuy}'s own
     * transaction-time rejection, since a datapack is not the only way a malformed price could
     * theoretically reach a shop (economy hardening pass, Part 1).
     */
    private static ShopTemplate rejectNegativePrices(Identifier shopId, ShopTemplate template) {
        List<ShopEntry> valid = new ArrayList<>(template.sells().size());
        int rejected = 0;
        for (ShopEntry entry : template.sells()) {
            if (entry.price() < 0) {
                rejected++;
                Totality.LOGGER.error("Shop {} has a catalog entry with a negative price ({}) for {} — dropping it",
                        shopId, entry.price(), entry.stack());
            } else {
                valid.add(entry);
            }
        }
        if (rejected == 0) return template;
        return new ShopTemplate(template.name(), valid);
    }
}
