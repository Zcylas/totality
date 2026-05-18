package zcylas.totality.api.ritual;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import zcylas.totality.Totality;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RitualRecipeRegistry {

    private static final Map<Identifier, RitualRecipe> RECIPES = new HashMap<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(RitualRecipeRegistry::load);
    }

    public static Collection<RitualRecipe> getAll() {
        return RECIPES.values();
    }

    public static Optional<RitualRecipe> get(Identifier id) {
        return Optional.ofNullable(RECIPES.get(id));
    }

    private static void load(MinecraftServer server) {
        RECIPES.clear();
        ResourceManager manager = server.getResourceManager();

        Map<Identifier, Resource> resources = manager.listResources(
                "recipes/ritual",
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStream stream = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(stream)) {

                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                // Build a recipe id from the file path
                // e.g. totality:ritual/grimoire -> totality:grimoire
                String path = fileId.getPath(); // "ritual/grimoire.json"
                String name = path.substring("recipes/ritual/".length(), path.length() - ".json".length());
                Identifier recipeId = Identifier.fromNamespaceAndPath(fileId.getNamespace(), name);

                RitualRecipe recipe = parse(recipeId, json);
                if (recipe != null) {
                    RECIPES.put(recipeId, recipe);
                    Totality.LOGGER.info("Loaded ritual recipe: {}", recipeId);
                }

            } catch (Exception e) {
                Totality.LOGGER.error("Failed to load ritual recipe: {}", fileId, e);
            }
        }

        Totality.LOGGER.info("Loaded {} ritual recipe(s)", RECIPES.size());
    }

    private static RitualRecipe parse(Identifier id, JsonObject json) {
        // Pattern
        List<RitualRecipe.ChalkEntry> pattern = new ArrayList<>();
        for (JsonElement el : json.getAsJsonArray("pattern")) {
            JsonObject obj = el.getAsJsonObject();
            ChalkColor color = ChalkColor.valueOf(obj.get("color").getAsString().toUpperCase());
            ChalkSigil glyph = ChalkSigil.valueOf(
                    obj.get("glyph").getAsString().toUpperCase().replace("_GLYPH", ""));
            int offsetX = obj.get("offsetX").getAsInt();
            int offsetZ = obj.get("offsetZ").getAsInt();
            pattern.add(new RitualRecipe.ChalkEntry(color, glyph, offsetX, offsetZ));
        }

        // Dais
        List<RitualRecipe.DaisEntry> dais = new ArrayList<>();
        if (json.has("dais")) {
            for (JsonElement el : json.getAsJsonArray("dais")) {
                JsonObject obj = el.getAsJsonObject();
                Identifier itemId = Identifier.parse(obj.get("item").getAsString());
                int offsetX = obj.get("offsetX").getAsInt();
                int offsetZ = obj.get("offsetZ").getAsInt();
                dais.add(new RitualRecipe.DaisEntry(itemId, offsetX, offsetZ));
            }
        }

        // Altar input
        Identifier altarInputId = Identifier.parse(json.get("altar_input").getAsString());
        Item altarInputItem = BuiltInRegistries.ITEM.getValue(altarInputId);
        if (altarInputItem == null) {
            Totality.LOGGER.error("Unknown altar_input item: {}", altarInputId);
            return null;
        }
        ItemStack altarInput = new ItemStack(altarInputItem);

        // Result
        Identifier resultId = Identifier.parse(json.get("result").getAsString());
        Item resultItem = BuiltInRegistries.ITEM.getValue(resultId);
        if (resultItem == null) {
            Totality.LOGGER.error("Unknown result item: {}", resultId);
            return null;
        }
        ItemStack result = new ItemStack(resultItem);

        return new RitualRecipe(id, pattern, dais, altarInput, result);
    }
}