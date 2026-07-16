package zcylas.totality.api.rpg.classes.covenant;

import net.minecraft.resources.Identifier;
import java.util.*;

public final class CovenantRegistry {

    private static final Map<Identifier, CovenantCategory> CATEGORIES = new LinkedHashMap<>();
    private static final Map<Identifier, CovenantData>     COVENANTS  = new LinkedHashMap<>();

    public static void registerCategory(CovenantCategory category) {
        CATEGORIES.put(category.id(), category);
    }

    public static void register(CovenantData covenant) {
        COVENANTS.put(covenant.id(), covenant);
    }

    public static Optional<CovenantCategory> getCategory(Identifier id) {
        return Optional.ofNullable(CATEGORIES.get(id));
    }

    public static Optional<CovenantData> get(Identifier id) {
        return Optional.ofNullable(COVENANTS.get(id));
    }

    public static List<CovenantCategory> getAllCategories() {
        return List.copyOf(CATEGORIES.values());
    }

    public static List<CovenantData> getByCategory(Identifier categoryId) {
        return COVENANTS.values().stream()
                .filter(c -> c.categoryId().equals(categoryId))
                .toList();
    }

    private CovenantRegistry() {}
}